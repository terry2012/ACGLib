package com.acg.lib;

/**
 * Permanent access ACG that can be composed with another ACG as input
 */
public abstract class ComposablePermanentAccessACG<S, T> extends PermanentAccessACG<T> implements Composable<S> {
}
