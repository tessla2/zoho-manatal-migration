package com.migration.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String detail;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
        this.detail = detail;
    }

    public ApiException(HttpStatus status, String detail, Throwable cause) {
        super(detail, cause);
        this.status = status;
        this.detail = detail;
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail);
    }

    public static ApiException badGateway(String detail) {
        return new ApiException(HttpStatus.BAD_GATEWAY, detail);
    }

    public static ApiException internalError(String detail, Throwable cause) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, detail, cause);
    }
}
