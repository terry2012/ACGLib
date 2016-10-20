package com.acg.lib;

public class ACGResourceAccessException extends Exception {

    public ACGResourceAccessException() {
        super();
    }

    public ACGResourceAccessException(String message) {
        super(message);
    }

    public ACGResourceAccessException(String message, Throwable t) {
        super(message, t);
    }

    public ACGResourceAccessException(Throwable t) {
        super(t);
    }
}
