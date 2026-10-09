package dev.tutz.boostconvert.format;

import dev.tutz.boostconvert.common.ApiException;
import org.springframework.http.HttpStatus;

/** The upload is not a format we accept, or it is not what its name claims to be. */
public class UnsupportedFileException extends ApiException {

    public UnsupportedFileException(String code, String message) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, message);
    }
}
