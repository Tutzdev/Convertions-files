package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.OutputStream;

/**
 * One conversion. Implementations are stateless Spring beans: adding a format means adding a class,
 * and {@link ConverterRegistry} picks it up.
 */
public interface Converter {

    ConverterInfo info();

    /**
     * Reads {@code source} and writes the converted file to {@code target}.
     *
     * @return the format actually written: {@code info().to()}, or {@link FileFormat#ZIP} when the
     *         result has several files (one image per PDF page, one CSV per sheet)
     * @throws ConversionFailedException when the file breaks a rule or cannot be read; the message is
     *         shown to the user
     */
    FileFormat convert(SourceFile source, OutputStream target) throws IOException;
}
