package dev.tutz.boostconvert.converter;

/**
 * The file was accepted but cannot be converted: it is corrupt, encrypted, too big for the limits,
 * or has the wrong shape. The job fails with this code and message; it is not an HTTP error,
 * because conversions run after the upload request has already been answered.
 */
public class ConversionFailedException extends RuntimeException {

    private final String code;

    public ConversionFailedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ConversionFailedException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
