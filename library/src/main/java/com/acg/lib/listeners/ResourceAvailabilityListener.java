package com.acg.lib.listeners;

/**
 * Listener to be notified when the resource is no longer available
 */
public interface ResourceAvailabilityListener extends ResourceReadyListener {

    /**
     * Called when the resource is no longer available
     */
    void onResourceUnavailable();
}
