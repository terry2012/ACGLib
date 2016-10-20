package com.acg.lib.validation.bitmap;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.View;
import com.acg.lib.view.ValidatedViewWrapper;

/**
 * Validates a single bitmap with no state
 */
public class SingleBitmapValidator implements BitmapValidator {

    protected final @NonNull Bitmap bitmap;

    public SingleBitmapValidator(@NonNull Bitmap bitmap) {
        this.bitmap = bitmap;
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
        Bitmap acgLocationBitmap = Bitmap.createBitmap(boundaryWidth, boundaryHeight, Bitmap.Config.ARGB_8888);

        // Render the cropped view to a canvas
        Canvas canvas = new Canvas(acgLocationBitmap);
        Rect shiftedBoundaries = new Rect(0, 0, boundaryWidth, boundaryHeight);
        canvas.drawBitmap(rootBitmap, boundariesForView, shiftedBoundaries, null);

        // Validate the bitmap
        return bitmap.sameAs(acgLocationBitmap);
    }

    private void setDrawingState(View root, boolean drawingCacheEnabled, boolean willNotDraw, boolean willNotCacheDrawing) {
        root.setDrawingCacheEnabled(drawingCacheEnabled);
        root.setWillNotDraw(willNotDraw);
        root.setWillNotCacheDrawing(willNotCacheDrawing);
    }
}
