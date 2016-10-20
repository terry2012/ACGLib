package com.acg.lib.validation.bitmap;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import com.acg.lib.view.ValidatedViewWrapper;

/**
 * Validates a bitmap for a view
 */
public interface BitmapValidator {

    /**
     * Check that the part of the screen where the ACG should be looks like one of the possible valid bitmaps
     * This will be called while validating a MotionEvent, but also periodically for temporal checks TODO
     * For temporal checks we probably need to store the state somewhere TODO
     */
    boolean validateBitmapForView(@NonNull ValidatedViewWrapper acgLocationView, @NonNull Rect boundariesForView);
}
