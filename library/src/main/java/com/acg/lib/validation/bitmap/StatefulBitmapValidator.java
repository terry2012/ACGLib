package com.acg.lib.validation.bitmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.View;
import com.acg.lib.validation.state.ViewState;
import com.acg.lib.view.ValidatedViewWrapper;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a bitmap based on some state for a ValidatedViewWrapper
 */
public class StatefulBitmapValidator implements BitmapValidator {

    protected final @NonNull Map<ViewState, List<Bitmap>> bitmapsForStates;

    public StatefulBitmapValidator(@NonNull Map<ValidatedViewWrapper, List<Bitmap>> bitmapsForViews) {
        this.bitmapsForStates = initBitmapsForStates(bitmapsForViews);
    }

    @Override
    public boolean validateBitmapForView(@NonNull ValidatedViewWrapper view, @NonNull Rect boundariesForView) {
        View root = view.getRootView();

        // Get state to return to
        boolean drawingCacheEnabled = root.isDrawingCacheEnabled();
        boolean willNotDraw = root.willNotDraw();
        boolean willNotCacheDrawing = root.willNotCacheDrawing();

        // Enable drawing cache
        setDrawingState(root, true, false, false);

        // Create bitmap from the cache
        Bitmap rootBitmap = Bitmap.createBitmap(root.getDrawingCache());

        // Return to state
        setDrawingState(root, drawingCacheEnabled, willNotDraw, willNotCacheDrawing);

        // Create a bitmap with the boundary width and height
        int boundaryWidth = boundariesForView.width();
        int boundaryHeight = boundariesForView.height();
        Bitmap bitmap = Bitmap.createBitmap(boundaryWidth, boundaryHeight, Bitmap.Config.ARGB_8888);

        // Render the cropped view to a canvas
        Canvas canvas = new Canvas(bitmap);
        Rect shiftedBoundaries = new Rect(0, 0, boundaryWidth, boundaryHeight);
        canvas.drawBitmap(rootBitmap, boundariesForView, shiftedBoundaries, null);

        // Validate the bitmap
        return validateBitmap(view.internalViewState(), bitmap);
    }

    private void setDrawingState(View root, boolean drawingCacheEnabled, boolean willNotDraw, boolean willNotCacheDrawing) {
        root.setDrawingCacheEnabled(drawingCacheEnabled);
        root.setWillNotDraw(willNotDraw);
        root.setWillNotCacheDrawing(willNotCacheDrawing);
    }

    /**
     * Validate a bitmap is one of the possible ones for a given state
     */
    private boolean validateBitmap(@NonNull ViewState viewState, @NonNull final Bitmap acgLocationBitmap) {
        if (!bitmapsForStates.containsKey(viewState)) {
            return false;
        }

        final List<Bitmap> bitmaps = bitmapsForStates.get(viewState);

        return bitmaps != null && Iterables.any(bitmaps, new Predicate<Bitmap>() {
            @Override
            public boolean apply(Bitmap bitmap) {
                return bitmap.sameAs(acgLocationBitmap);
            }
        });
    }

    protected @NonNull Map<ViewState, List<Bitmap>> initBitmapsForStates(@NonNull Map<ValidatedViewWrapper, List<Bitmap>> bitmapsForViews) {
        Map<ViewState, List<Bitmap>> bitmapsForStates = new HashMap<>();

        for (ValidatedViewWrapper view : bitmapsForViews.keySet()) {
            bitmapsForStates.put(view.internalViewState(), bitmapsForViews.get(view));
        }

        return bitmapsForStates;
    }
}
