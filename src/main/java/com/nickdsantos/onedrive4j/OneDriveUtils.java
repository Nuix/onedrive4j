package com.nickdsantos.onedrive4j;

import java.util.Map;

/**
 * OneDrive utilities.
 *
 * @author Luke Quinane
 */
public class OneDriveUtils {

    /**
     * Throws a {@link OneDriveException} if the response has an error reported in it.
     *
     * @param response the response to check.
     */
    public static void throwOnError(Map<Object, Object> response) {
        if (response.containsKey("error")) {
            throw new OneDriveException(response.get("error"), (String) response.get("error_description"));
        }
    }
}
