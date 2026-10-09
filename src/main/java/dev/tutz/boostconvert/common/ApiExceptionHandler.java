package dev.tutz.boostconvert.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Every error leaves the API as RFC 9457 Problem Details with a stable {@code code}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProblemResponses problems;
    private final DataSize maxFileSize;

    public ApiExceptionHandler(ProblemResponses problems,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.problems = problems;
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .headers(exception.getHeaders())
                .body(problems.create(exception.getStatus(), exception.getCode(), exception.getMessage(), request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        String detail = "Files are limited to %d MB.".formatted(maxFileSize.toMegabytes());
        return problems.create(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", detail, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail handleMissingPart(MissingServletRequestPartException exception, HttpServletRequest request) {
        String detail = "Multipart field '%s' is required.".formatted(exception.getRequestPartName());
        return problems.create(HttpStatus.BAD_REQUEST, "MISSING_FIELD", detail, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        String detail = "Field '%s' is required.".formatted(exception.getParameterName());
        return problems.create(HttpStatus.BAD_REQUEST, "MISSING_FIELD", detail, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MultipartException.class})
    ProblemDetail handleInvalidInput(Exception exception, HttpServletRequest request) {
        return problems.create(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            return ResponseEntity.status(status).headers(response.getHeaders())
                    .body(problems.create(status, status.name(), status.getReasonPhrase(), request));
        }
        // The client only gets a generic message: exception text may carry paths or library internals.
        log.error("Unexpected failure on {}", request.getRequestURI(), exception);
        return ResponseEntity.internalServerError()
                .body(problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error.", request));
    }
}
