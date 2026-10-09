package dev.tutz.boostconvert.job;

import dev.tutz.boostconvert.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Unknown id, or a job that expired and was cleaned up. */
public class JobNotFoundException extends ApiException {

    public JobNotFoundException(UUID id) {
        super(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", "Conversion %s does not exist or has expired.".formatted(id));
    }
}
