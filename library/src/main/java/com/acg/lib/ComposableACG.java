package com.acg.lib;

/**
 * One-time ACG that can be composed with another ACG as input
 */
public abstract class ComposableACG<S, T> extends ACG<T> implements Composable<S> {
    public abstract void passInput(S s);
}
