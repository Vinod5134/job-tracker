package com.vinod.jobtracker.common;

import java.util.Map;

/**
 * Error body returned by every endpoint, e.g. {"status":400,"message":"...","fieldErrors":{...}}.
 */
public record ApiError(int status, String message, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Map.of());
    }
}
