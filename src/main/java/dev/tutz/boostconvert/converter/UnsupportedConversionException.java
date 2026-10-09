package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.common.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedConversionException extends ApiException {

    public UnsupportedConversionException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "CONVERSION_NOT_SUPPORTED", message);
    }
}
