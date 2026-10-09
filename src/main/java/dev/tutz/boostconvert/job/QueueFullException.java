package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.common.ApiException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class QueueFullException extends ApiException {

    private final Duration retryAfter;

    public QueueFullException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "QUEUE_FULL",
                "All workers are busy and the queue is full. Try again in %d seconds.".formatted(retryAfter.toSeconds()));
        this.retryAfter = retryAfter;
    }

    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter.toSeconds()));
        return headers;
    }
}
