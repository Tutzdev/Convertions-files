package dev.tutz.boostconvert.format;

import dev.tutz.boostconvert.common.FileNames;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

/**
 * Decides what an upload really is. The content wins: magic bytes identify binary formats, and the
 * file name is trusted only to tell text formats apart (CSV, JSON and Markdown have no signature).
 * A name that contradicts the content is rejected instead of guessed around.
 */
@Component
public class FormatDetector {

    static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP_SIGNATURE = {'P', 'K', 3, 4};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final int HEADER_SIZE = 16;
    private static final int BUFFER_SIZE = 64 * 1024;

    public DetectedFormat detect(Path file, String fileName) throws IOException {
        Optional<String> extension = FileNames.extension(fileName);
        Optional<FileFormat> claimed = extension.flatMap(FileFormat::fromExtension);
        if (extension.isPresent() && claimed.isEmpty()) {
            throw new UnsupportedFileException("UNSUPPORTED_FORMAT",
                    ".%s files are not supported.".formatted(extension.get()));
        }

        Optional<FileFormat> sniffed = sniffBinary(file);
        if (sniffed.isPresent()) {
            FileFormat actual = sniffed.get();
            if (claimed.isPresent() && claimed.get() != actual) {
                throw mismatch(claimed.get(), "a " + label(actual) + " file");
            }
            return new DetectedFormat(actual, StandardCharsets.UTF_8);
        }

        FileFormat format = claimed.orElseThrow(() -> new UnsupportedFileException("UNSUPPORTED_FORMAT",
                "The file format could not be recognised. Add the file extension to the name."));
        if (!format.isText()) {
            throw mismatch(format, "something else");
        }
        return new DetectedFormat(format, textCharset(file));
    }

    private Optional<FileFormat> sniffBinary(Path file) throws IOException {
        byte[] header = readHeader(file);
        if (startsWith(header, PNG_SIGNATURE, 0)) {
            return Optional.of(FileFormat.PNG);
        }
        if (startsWith(header, JPG_SIGNATURE, 0)) {
            return Optional.of(FileFormat.JPG);
        }
        if (startsWith(header, RIFF, 0) && startsWith(header, WEBP, 8)) {
            return Optional.of(FileFormat.WEBP);
        }
        if (startsWith(header, PDF_SIGNATURE, 0)) {
            return Optional.of(FileFormat.PDF);
        }
        if (startsWith(header, ZIP_SIGNATURE, 0)) {
            // DOCX, XLSX and plain ZIP share the signature; the workbook part is what makes a spreadsheet.
            return Optional.of(isWorkbook(file) ? FileFormat.XLSX : FileFormat.ZIP);
        }
        return Optional.empty();
    }

    /**
     * UTF-8 when the bytes decode as UTF-8, otherwise Windows-1252: that is what Excel writes when a
     * spreadsheet is saved as CSV on a Portuguese or Spanish Windows.
     */
    private Charset textCharset(Path file) throws IOException {
        if (containsNulByte(file)) {
            throw new UnsupportedFileException("NOT_TEXT",
                    "The file does not look like text (binary or UTF-16 content). Save it as UTF-8 and try again.");
        }
        return isUtf8(file) ? StandardCharsets.UTF_8 : WINDOWS_1252;
    }

    private static boolean isUtf8(Path file) throws IOException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), decoder)) {
            char[] buffer = new char[BUFFER_SIZE];
            while (reader.read(buffer) != -1) {
                // decoding the whole file is the check
            }
            return true;
        } catch (CharacterCodingException invalid) {
            return false;
        }
    }

    private static boolean containsNulByte(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buffer[i] == 0) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static boolean isWorkbook(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.getEntry("xl/workbook.xml") != null;
        } catch (ZipException corrupt) {
            return false;
        }
    }

    private static byte[] readHeader(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEADER_SIZE);
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        return data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static UnsupportedFileException mismatch(FileFormat claimed, String actual) {
        return new UnsupportedFileException("FORMAT_MISMATCH",
                "The file is named .%s but its content is %s.".formatted(claimed.extension(), actual));
    }

    private static String label(FileFormat format) {
        return format.extension().toUpperCase(Locale.ROOT);
    }
}
