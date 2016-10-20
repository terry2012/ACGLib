package com.acg.lib.validation;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import com.acg.lib.validation.bitmap.BitmapValidator;
import com.acg.lib.view.ValidatedViewWrapper;

import java.util.Arrays;
import java.util.List;

import static com.acg.lib.validation.ValidationParameters.RANDOM_CHECK_INTERVAL_PARAMETER;
import static com.acg.lib.validation.ValidationParameters.RANDOM_CHECK_INVALIDATION_PARAMETER;

/**
 * Validate input events
 */
public final class ACGValidator {

    private Bundle validationArgs;
    private BitmapValidator bitmapValidator;
    private long nextPossiblyValidTimestamp = System.currentTimeMillis();

    public ACGValidator(@NonNull Bundle validationArgs, @NonNull BitmapValidator bitmapValidator) {
        this.validationArgs = validationArgs;
        this.bitmapValidator = bitmapValidator;
    }

    /**
     * Validates that the last n calling methods are the desired methods, in the order provided, working backward
     * This doesn't validate arguments so it's not that sensitive of a check
     */
    public static boolean validateCallingMethods(@NonNull List<Pair<Class<?>, String>> desiredMethods) {
        final Throwable t = new Throwable();
        StackTraceElement[] stackTraceElements = t.getStackTrace();

        if (stackTraceElements == null || stackTraceElements.length < 3) {
            return false;
        }

        // We don't care about the first two elements, since they must always be this class and the caller
        List<StackTraceElement> relevantElements = Arrays.asList(stackTraceElements).subList(2, stackTraceElements.length);

        for (int i = 0; i < desiredMethods.size(); i++) {
            Pair<Class<?>, String> desiredMethod = desiredMethods.get(i);
            if (!validateCallingMethod(desiredMethod.first, desiredMethod.second, relevantElements.get(i))) {
                return false;
            }
        }

        return true;
    }

    private static <U> boolean validateCallingMethod(Class<U> desiredClass, String desiredMethodName, StackTraceElement callingMethodElement) {
        try {
            return desiredClass.equals(Class.forName(callingMethodElement.getClassName())) && desiredMethodName.equals(callingMethodElement.getMethodName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load class in stack trace", e);
        }
    }

    /**
     * Validate that a motion event for an ACG is legitimate, that is:
     *
     * 1. The event occurred in the correct location on the screen (where the ACG is
     * 2. The bitmap for the correct location of the screen (where the ACG is) is an expected bitmap for the ACG
     * 3. The event is not obscured
     * 4. [2] and [3] (for any event) have true for a sufficiently long period of time
     * 5, The event occurred at the correct time
     */
    public boolean validateMotionEvent(MotionEvent event, ValidatedViewWrapper view) {
        Rect boundariesForView = getBoundariesForView(view);
        return validateObscuredEvent(event) && validateEventLocation(event.getRawX(), event.getRawY(), boundariesForView) &&
                validateViewBitmap(view, boundariesForView) && validateEventTime(event);
    }

    /**
     *  Filter touches when obscured manually, since we want to invalidate for a period of time TODO: how to do this in random checks, though?
     */
    private boolean validateObscuredEvent(MotionEvent event) {
        if ((event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {
            extendNextValidTimeStamp(System.currentTimeMillis());
            return false;
        }

        return true;
    }

    /**
     * Validate that the bitmap for the view location is expected, and that this has been true for sufficiently long.
     * Update the internal state accordingly.
     */
    private boolean validateViewBitmap(@NonNull ValidatedViewWrapper view, @NonNull Rect boundariesForView) {
        // Validate the bitmap and update the next possible valid time
        boolean bitmapIsValid = bitmapValidator.validateBitmapForView(view, boundariesForView);

        // Check whether or not the ACG was recently invalidated, and if that has expired
        long nowTimeStamp = System.currentTimeMillis();
        boolean mayBeValid = nowTimeStamp >= nextPossiblyValidTimestamp;

        if (bitmapIsValid) {
            // If the bitmap is valid, we return whether or not the invalidation interval has expired
            return mayBeValid;
        } else {
            // Otherwise, we extend next possible valid time and return false
            extendNextValidTimeStamp(nowTimeStamp);
            return false;
        }
    }

    /**
     * Validate that the event occurred at the right time, so that applications can't just store legitimate events
     * and replay them later
     */
    private boolean validateEventTime(MotionEvent event) {
        long eventTimeStamp = event.getEventTime();
        long nowTimeStamp = SystemClock.uptimeMillis();

        // Events expire at the same rate as random checks occur (for now)
        return eventTimeStamp + validationArgs.getInt(RANDOM_CHECK_INTERVAL_PARAMETER) >= nowTimeStamp;
    }

    /**
     * Validate a view without a motion event (to be used for random checks), that is:
     *
     * 1. The bitmap for the view location is expected
     * 2. An event in that location would not be obscured (not possible right now because of Android limitations)
     * 3. This has been true for sufficiently long
     */
    public boolean validateView(@NonNull ValidatedViewWrapper view) {
        Rect boundariesForView = getBoundariesForView(view);

        /*// Create pointers
        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = 0;

        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, boundariesForView.centerX());
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, boundariesForView.centerY());

        MotionEvent event = MotionEvent.obtain(
                uptimeMillis(),
                uptimeMillis(),
                ACTION_DOWN,
                1,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                0,
                0,
                1.0f,
                1.0f,
                7,
                0x0,
                SOURCE_TOUCHSCREEN,
                0x0);*/

        /* can't validate outside of the hierarchy since obscured flag isn't set if we just construct a MotionEvent
        * Otherwise, we'd need to do some sufficient sample of points, or a random set of point*/

        return /*validateObscuredEvent(event) &&*/ validateViewBitmap(view, boundariesForView);
    }

    /**
     * Extend the next valid timestamp to now plus some configuration interval
     */
    private void extendNextValidTimeStamp(long nowTimeStamp) {
        int invalidationLengthInMillis = validationArgs.getInt(RANDOM_CHECK_INVALIDATION_PARAMETER);
        nextPossiblyValidTimestamp = nowTimeStamp + invalidationLengthInMillis;
    }

    /**
     * Check that the location of the event came from within the bounds of the ACG view
     */
    private boolean validateEventLocation(float eventX, float eventY, @NonNull Rect boundariesForView) {
        return boundariesForView.contains((int) eventX, (int) eventY);
    }

    /**
     * Get the boundaries for a view
     * Note that this assumes the MotionEvent cannot be modified, which is not inherently true, so it's something we need to prevent TODO
     */
    private Rect getBoundariesForView(@NonNull View view) {
        // Get the location of the view on the screen
        int[] viewLocation = new int[2];
        view.getLocationOnScreen(viewLocation);

        // Get the boundaries of the view
        int viewX = viewLocation[0];
        int viewY = viewLocation[1];
        return new Rect(viewX, viewY, viewX + view.getWidth(), viewY + view.getHeight());
    }
}
