package com.acg.lib.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Pair;
import android.view.*;
import android.widget.ToggleButton;
import com.acg.lib.validation.ACGValidator;
import com.acg.lib.validation.state.ViewState;
import com.google.common.util.concurrent.*;

import java.util.Random;
import java.util.concurrent.*;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.acg.lib.validation.ACGValidator.validateCallingMethods;
import static com.acg.lib.validation.ValidationParameters.*;
import static java.util.Collections.singletonList;

/**
 * Here we explore an alternative approach to preventing clickjacking-style attacks.
 *
 * This View contains an internal ACG View element, but doesn't allow anyone to actually access the underlying element.
 * All rendering logic is deferred to the underlying element.
 *
 * The View delegates events to the internal View, which then handles them. Then, it requests relayout.
 * For now, this only handles immediately relevant touch & motion events. Eventually, if we need to, we can add
 * delegators for KeyEvents, and so on.
 *
 * For now, we restrict ACGs to having solid backgrounds and predefined sizes. Transparent backgrounds and
 * relative sizes (e.g. wrap-content and match-parent) are not supported. Transparent backgrounds complicate
 * validation, and relative sizes complicate measurement and layout flow. This is something we can likely work
 * around at some point, but the time cost is probably not worth it for the first iteration.
 *
 * We go with this approach for now, but leave the Frame intact in case we want to return to it.
 */
public final class ValidatedViewWrapper extends View {

    private final @NonNull Bundle validationParameters;
    private final @NonNull View internalView;
    private final @NonNull ACGValidator acgValidator;

    // Validation checker
    private final Random random = new Random(System.currentTimeMillis());
    private static final ListeningScheduledExecutorService RANDOM_CHECKER = MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor());

    public ValidatedViewWrapper(@NonNull Context context, @NonNull final View internalView, @NonNull Bundle validationParameters, @NonNull ACGValidator acgValidator) {
        super(context);

        // Add a layout listener to the internal view so that we can invalidate after child layout changes
        internalView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                postInvalidate();
            }
        });

        this.internalView = internalView;
        this.validationParameters = validationParameters;
        this.acgValidator = acgValidator;

        // Schedule the first random check
        scheduleRandomCheck();

        // Set the layout params so the parent sizes us correctly (we don't care if people later change this, since validation catches it)
        ViewGroup.LayoutParams layoutParams = this.internalView.getLayoutParams();
        validateLayoutParams(layoutParams);
        super.setLayoutParams(layoutParams);
    }

    public ValidatedViewWrapper getOuter() {
        return this;
    }

    /**
     * Schedule the next random validation check, which modifies the internal validation state
     * As soon as that check runs, schedule the next one
     */
    private void scheduleRandomCheck() {
        if (validationParameters.isEmpty()) {
            return;
        }

        // Schedule the validation check into a future
        ListenableFuture future = RANDOM_CHECKER.schedule(new Runnable() {
            private Runnable validateView = new Runnable() {
                @Override
                public void run() {
                    acgValidator.validateView(getOuter());
                }
            };

            // This must be run on the UI thread, which is why we wrap the validation runnable and post it on the UI thread
            @Override
            public void run() {
                Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND);
                post(validateView);
            }
        }, (long) random.nextInt(validationParameters.getInt(RANDOM_CHECK_INTERVAL_PARAMETER)), TimeUnit.MILLISECONDS);

        // On success, schedule the next check, and on failure, fail fast
        Futures.addCallback(future, new FutureCallback() {
            @Override
            public void onSuccess(Object result) {
                scheduleRandomCheck();
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence getAccessibilityClassName() {
        return ValidatedViewWrapper.class.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchSetPressed(boolean pressed) {
        boolean internalViewIsClickable = internalView.isClickable() || internalView.isLongClickable();
        if (!pressed || !internalViewIsClickable) {
            internalView.setPressed(pressed);
            requestLayout();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFilterTouchesWhenObscured(boolean enabled) {
        doNotSupportIfValidated();
        super.setFilterTouchesWhenObscured(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        boolean dispatch = false;

        if (onFilterTouchEventForSecurity(event)) {
            dispatch = internalView.dispatchTouchEvent(event);
            requestLayout();
        }

        return dispatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onFilterTouchEventForSecurity(@NonNull MotionEvent event) {
        return super.onFilterTouchEventForSecurity(event) && acgValidator.validateMotionEvent(event, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchTrackballEvent(@NonNull MotionEvent event) {
        boolean dispatch = false;

        if (acgValidator.validateMotionEvent(event, this)) {
            dispatch = internalView.dispatchTrackballEvent(event);
            requestLayout();
        }

        return dispatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
        boolean dispatch = false;

        if (acgValidator.validateMotionEvent(event, this)) {
            dispatch = internalView.dispatchGenericMotionEvent(event);
            requestLayout();
        }

        return dispatch;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        super.dispatchWindowFocusChanged(hasFocus);
        internalView.dispatchWindowFocusChanged(hasFocus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispatchWindowVisibilityChanged(int visibility) {
        super.dispatchWindowVisibilityChanged(visibility);
        internalView.dispatchWindowVisibilityChanged(visibility);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispatchConfigurationChanged(Configuration newConfig) {
        super.dispatchConfigurationChanged(newConfig);
        internalView.dispatchConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTouchDelegate(TouchDelegate delegate) {
        doNotSupportIfValidated();
        super.setTouchDelegate(delegate);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        internalView.draw(canvas);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        internalView.layout(left, top, right, bottom);
        postInvalidate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        internalView.refreshDrawableState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        internalView.jumpDrawablesToCurrentState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setForeground(Drawable foreground) {
        doNotSupportIfValidated();
        super.setForeground(foreground);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setForegroundGravity(int gravity) {
        doNotSupportIfValidated();
        super.setForegroundGravity(gravity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setForegroundTintList(ColorStateList tint) {
        doNotSupportIfValidated();
        super.setForegroundTintList(tint);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setForegroundTintMode(PorterDuff.Mode tintMode) {
        doNotSupportIfValidated();
        super.setForegroundTintMode(tintMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchSetSelected(boolean selected) {
        internalView.setSelected(selected);
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void dispatchSetActivated(boolean activated) {
        internalView.setActivated(activated);
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ViewGroup.LayoutParams layoutParams = internalView.getLayoutParams();

        // We require the child to provide us with an exact measurement
        validateLayoutParams(layoutParams);

        // We ignore the parent-provided constraints; the ACG always wins
        internalView.measure(makeMeasureSpec(layoutParams.width, EXACTLY), makeMeasureSpec(layoutParams.height, EXACTLY));
        setMeasuredDimension(internalView.getMeasuredWidth(), internalView.getMeasuredHeight());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performClick() {
        boolean click = internalView.performClick();
        requestLayout();
        return click;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        Pair<Class<?>, String> expectedMethod = new Pair<Class<?>, String>(ViewGroup.class, "addViewInner");
        if (!validateCallingMethods(singletonList(expectedMethod))) {
            doNotSupportIfValidated();
        }
        super.setLayoutParams(params);
    }

    @Override
    public ViewOverlay getOverlay() {
        doNotSupportIfValidated();
        return super.getOverlay();
    }

    /**
     * Check validation parameters and throw an exception if validation is on.
     * The parameters can only be null before the constructor is run. We need this check so that the call
     * to the superclass constructor, which must be first in our constructor, does not fail.
     */
    private void doNotSupportIfValidated() {
        if (validationParameters != null) {
            throw new UnsupportedOperationException("changes to validated views not supported");
        }
    }

    /**
     * Require the ACG to provide us with an exact measurement
     */
    private void validateLayoutParams(ViewGroup.LayoutParams layoutParams) {
        if (layoutParams.width == MATCH_PARENT || layoutParams.height == MATCH_PARENT) {
            throw new UnsupportedOperationException("MATCH_PARENT layout in ACG views not supported");
        } else if (layoutParams.width == WRAP_CONTENT || layoutParams.height == WRAP_CONTENT) {
            throw new UnsupportedOperationException("WRAP_CONTENT layout in ACG views not supported");
        } else if (layoutParams.width < 0 || layoutParams.height < 0) {
            throw new UnsupportedOperationException("ACG must provide exact measurement to wrapper");
        }
    }

    public @NonNull ViewState internalViewState() {
        if (internalView instanceof ToggleButton) {
            ToggleButton toggleButton = (ToggleButton) internalView;

            if (toggleButton.isChecked()) {
                return ViewState.CHECKED;
            } else {
                return ViewState.UNCHECKED;
            }
        } else {
            throw new UnsupportedOperationException("Not yet implemented for non-toggle-button ACGs");
        }
    }
}
