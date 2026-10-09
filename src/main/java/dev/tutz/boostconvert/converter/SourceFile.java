package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.format.FileFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/** An upload already on disk, with the format and charset found by the format detector. */
public record SourceFile(Path path, FileFormat format, Charset charset) {

    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /** A reader in the detected charset that skips the byte order mark Excel and Notepad add. */
    public BufferedReader openReader() throws IOException {
        BufferedReader reader = Files.newBufferedReader(path, charset);
        reader.mark(1);
        if (reader.read() != BYTE_ORDER_MARK) {
            reader.reset();
        }
        return reader;
    }
}
