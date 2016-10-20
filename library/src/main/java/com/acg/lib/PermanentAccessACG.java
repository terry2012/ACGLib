package com.acg.lib;

import android.content.Context;
import com.acg.lib.listeners.ResourceAvailabilityListener;

/**
 * Abstract ACG as a fragment with permanent access until the user explicitly disables it.
 * Activities that include this fragment must implement ResourceAvailabilityListener.
 */
public abstract class PermanentAccessACG<T> extends ACG<T> {

    protected ResourceAvailabilityListener resourceAvailabilityListener;

    @Override
    protected void bindListeners(Context context) {
        super.bindListeners(context);
        resourceAvailabilityListener = (ResourceAvailabilityListener) acgListeners.getResourceListenerForACG(this);
    }
}
