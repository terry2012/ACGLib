package com.acg.lib.listeners;

import android.support.annotation.NonNull;
import com.acg.lib.ACG;

import java.util.HashMap;
import java.util.Map;

/**
 * Listeners for an ACG, so that activities can contain multiple (separate) listeners, and we can add different types
 * of listeners in the future if we choose
 */
public class ACGListeners {

    // Listeners
    private final Map<ACG, ResourceReadyListener> acgResourceListeners;

    public static class Builder {
        private Map<ACG, ResourceReadyListener> acgResourceListeners = new HashMap<>();

        public ACGListeners build() {
            return new ACGListeners(this);
        }

        public <T> Builder withResourceReadyListener(@NonNull ACG<T> acg, @NonNull ResourceReadyListener resourceReadyListener) {
            acgResourceListeners.put(acg, resourceReadyListener);
            return this;
        }
    }

    private ACGListeners(Builder builder) {
        this.acgResourceListeners = builder.acgResourceListeners;
    }

    /**
     * Get a resource listener for a given ACG
     *
     * Throws a runtime exception if the ACG does not have a listener
     */
    public @NonNull <T> ResourceReadyListener getResourceListenerForACG(ACG<T> acg) {
        if (!acgResourceListeners.containsKey(acg)) {
            throw new RuntimeException("There is no ResourceReadyListener the ACG");
        }

        return acgResourceListeners.get(acg);
    }
}
