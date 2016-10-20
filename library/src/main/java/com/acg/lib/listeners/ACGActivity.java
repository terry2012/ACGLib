package com.acg.lib.listeners;

/**
 * Interface for an ACG activity to use so that it can declare listeners while encapsulating details
 *
 * The easiest way to build the listeners is to use the builder
 */
public interface ACGActivity {
    ACGListeners buildACGListeners();
}
