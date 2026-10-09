package dev.tutz.boostconvert.format;

import static dev.tutz.boostconvert.TestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.TestFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FormatDetectorTest {

    private final FormatDetector detector = new FormatDetector();

    @TempDir
    Path dir;

    @Test
    void recognisesBinaryFormatsByTheirContent() throws IOException {
        assertThat(detect("photo.png", TestFiles.png(4, 4))).isEqualTo(FileFormat.PNG);
        assertThat(detect("photo.JPEG", TestFiles.jpg(4, 4))).isEqualTo(FileFormat.JPG);
        assertThat(detect("banner.webp", TestFiles.webp())).isEqualTo(FileFormat.WEBP);
        assertThat(detect("report.pdf", TestFiles.pdf("hello"))).isEqualTo(FileFormat.PDF);
        assertThat(detect("budget.xlsx", TestFiles.xlsx(TestFiles.sheets("Q1", new Object[][] {{"a"}}))))
                .isEqualTo(FileFormat.XLSX);
    }

    @Test
    void contentWinsWhenTheNameHasNoExtension() throws IOException {
        assertThat(detect("scan", TestFiles.pdf("hello"))).isEqualTo(FileFormat.PDF);
    }

    @Test
    void rejectsANameThatContradictsTheContent() {
        assertThatThrownBy(() -> detect("invoice.png", TestFiles.pdf("hello")))
                .isInstanceOf(UnsupportedFileException.class)
                .hasMessage("The file is named .png but its content is a PDF file.")
                .extracting("code").isEqualTo("FORMAT_MISMATCH");
    }

    @Test
    void rejectsABinaryNameOnTextContent() {
        assertThatThrownBy(() -> detect("photo.jpg", "just text".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnsupportedFileException.class)
                .extracting("code").isEqualTo("FORMAT_MISMATCH");
    }

    @Test
    void aZipIsNotASpreadsheet() {
        byte[] zipWithoutWorkbook = {'P', 'K', 3, 4, 0, 0, 0, 0};
        assertThatThrownBy(() -> detect("budget.xlsx", zipWithoutWorkbook))
                .extracting("code").isEqualTo("FORMAT_MISMATCH");
    }

    @Test
    void textFormatsComeFromTheNameAndKeepTheirCharset() throws IOException {
        DetectedFormat utf8 = detector.detect(write(dir, "a.csv", "nome;cidade\nJoão;São Paulo\n"), "a.csv");
        assertThat(utf8).isEqualTo(new DetectedFormat(FileFormat.CSV, StandardCharsets.UTF_8));

        // What Excel writes on a Portuguese Windows: "João" in Windows-1252 is not valid UTF-8.
        byte[] excelCsv = "nome;cidade\nJoão;São Paulo\n".getBytes(FormatDetector.WINDOWS_1252);
        DetectedFormat legacy = detector.detect(write(dir, "b.csv", excelCsv), "b.csv");
        assertThat(legacy).isEqualTo(new DetectedFormat(FileFormat.CSV, FormatDetector.WINDOWS_1252));
    }

    @Test
    void binaryContentWithATextNameIsRejected() {
        byte[] binary = {'a', 'b', 0, 'c'};
        assertThatThrownBy(() -> detect("data.json", binary))
                .extracting("code").isEqualTo("NOT_TEXT");
    }

    @Test
    void unknownExtensionsAndUnrecognisedContentAreRejected() {
        assertThatThrownBy(() -> detect("letter.docx", TestFiles.pdf("x")))
                .hasMessage(".docx files are not supported.")
                .extracting("code").isEqualTo("UNSUPPORTED_FORMAT");
        assertThatThrownBy(() -> detect("notes", "plain words".getBytes(StandardCharsets.UTF_8)))
                .extracting("code").isEqualTo("UNSUPPORTED_FORMAT");
    }

    private FileFormat detect(String name, byte[] content) throws IOException {
        return detector.detect(write(dir, "upload", content), name).format();
    }
}
