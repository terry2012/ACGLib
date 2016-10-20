package com.acg.lib;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.acg.ACGLib.R;
import com.acg.lib.listeners.ACGActivity;
import com.acg.lib.listeners.ACGListeners;
import com.acg.lib.listeners.ResourceReadyListener;
import com.acg.lib.validation.ACGValidator;
import com.acg.lib.validation.bitmap.BitmapValidator;

import java.util.List;

import static com.acg.lib.validation.ValidationParameters.*;

/**
 * Represents an ACG at the highest level
 * An ACG is a Fragment that maintains some bitmap and validity state and has a resource that can be accessed
 */
public abstract class ACG<T> extends Fragment {

    // bitmap validation
    protected static BitmapValidator bitmapValidator;

    // noop validation
    protected static Bundle noopValidationArgs = new Bundle();
    protected static ACGValidator noopValidator = new ACGValidator(noopValidationArgs, bitmapValidator);

    // actual validation
    protected Bundle validationArguments = noopValidationArgs;
    protected static ACGValidator validator = noopValidator;

    // listeners for the ACG - all ACGs have at least a resourceReadyListener
    protected ACGListeners acgListeners;
    protected ResourceReadyListener resourceReadyListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        initBitmapValidator(context);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        initBitmapValidator(activity);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        bindListeners(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Populate the validation arguments
        validationArguments = new Bundle();
        validationArguments.putInt(RANDOM_CHECK_INVALIDATION_PARAMETER, randomCheckInvalidationParameter());
        validationArguments.putInt(RANDOM_CHECK_INTERVAL_PARAMETER, randomCheckIntervalParameter());

        // Create an ACG validator
        validator = new ACGValidator(validationArguments, bitmapValidator);

        // Build the view
        return buildView(inflater, container);
    }

    /**
     * The amount of time an ACG should invalidate a view after a failed check in ms
     */
    protected abstract int randomCheckInvalidationParameter();

    /**
     * The maximum frequency of random checks for an ACG in ms
     */
    protected abstract int randomCheckIntervalParameter();

    /**
     * Initialize the bitmap validator
     */
    protected void initBitmapValidator(@NonNull Context context) {
        List<View> viewStates = renderViewsInIsolation(context);
        bitmapValidator = initBitmapValidator(viewStates);
    }

    protected abstract BitmapValidator initBitmapValidator(@NonNull List<View> views);

    /**
     * Render a bitmap for the view
     */
    protected @NonNull Bitmap getBitmapForView(@NonNull View view) {
        // Build a container to stick the view in for measuring
        Context context = view.getContext();
        ViewGroup container = new FrameLayout(context);
        container.addView(view);

        // Inflate the container
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.isolated_acg_view, container, true);

        // Measure the view to get the width and height
        int measureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(measureSpec, measureSpec);

        // Call layout
        container.layout(0, 0, container.getMeasuredWidth(), container.getMeasuredHeight());

        // Create a bitmap and draw to it
        Bitmap bitmap = Bitmap.createBitmap(container.getWidth(), container.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        container.draw(canvas);
        return bitmap;
    }

    /**
     * Render the inner view in isolation in any possible states to populate bitmaps
     * We use the ViewWrapper here since it doesn't make a difference what approach we take for the sake of
     * Bitmap rendering
     */
    protected abstract @NonNull List<View> renderViewsInIsolation(@NonNull Context context);

    /**
     * Build a view inside a container
     */
    protected abstract @NonNull View buildView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container);

    /**
     * Access the resource, given an input resource
     */
    public abstract T getResource() throws ACGResourceAccessException;

    /**
     * Bind the listeners
     */
    protected void bindListeners(Context context) {
        try {
            ACGActivity acgActivity = (ACGActivity) context;
            acgListeners = acgActivity.buildACGListeners();
        } catch (ClassCastException e) {
            throw new ClassCastException(String.format("%s must implement ACGActivity", context));
        }

        resourceReadyListener = acgListeners.getResourceListenerForACG(this);
    }
}
