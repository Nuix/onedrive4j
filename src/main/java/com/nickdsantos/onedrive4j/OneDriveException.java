package com.nickdsantos.onedrive4j;

/**
 * A OneDrive exception.
 */
public class OneDriveException extends RuntimeException {

    /**
     * The error. This may be a string, or a map of more details (error code, etc).
     */
    private final Object error;

    /**
     * The error description.
     */
    private final String errorDescription;

    /**
     * Constructs a new exception.
     *
     * @param error the error details object. This may be a string, or a map of more details (error code, etc).
     * @param errorDescription the error description.
     */
    public OneDriveException(Object error, String errorDescription) {

        this.error = error;
        this.errorDescription = errorDescription;
    }

    /**
     * Gets the error details. This may be a string, or a map of more details (error code, etc).
     *
     * @return the error details.
     */
    public Object getError() {
        return error;
    }

    /**
     * Gets the error description.
     *
     * @return the error description.
     */
    public String getErrorDescription() {
        return errorDescription;
    }
}
