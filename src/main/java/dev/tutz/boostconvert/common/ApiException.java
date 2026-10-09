package dev.tutz.boostconvert.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * A request the API refuses on purpose. It carries the HTTP status and a stable {@code code},
 * so clients can branch on the code without parsing the message.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** Extra response headers, such as {@code Retry-After}. */
    public HttpHeaders getHeaders() {
        return HttpHeaders.EMPTY;
    }
}
