package dev.tutz.boostconvert.converter.document;

import static dev.tutz.boostconvert.TestFiles.LIMITS;
import static dev.tutz.boostconvert.TestFiles.decode;
import static dev.tutz.boostconvert.TestFiles.run;
import static dev.tutz.boostconvert.TestFiles.source;
import static dev.tutz.boostconvert.TestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.TestFiles;
import dev.tutz.boostconvert.TestFiles.Converted;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfConvertersTest {

    @TempDir
    Path dir;

    @Test
    void pdfToTextExtractsEveryPage() throws IOException {
        Path pdf = write(dir, "in", TestFiles.pdf("First page", "Second page"));

        Converted text = run(new PdfToTextConverter(LIMITS), source(pdf, FileFormat.PDF));

        assertThat(text.format()).isEqualTo(FileFormat.TXT);
        assertThat(text.text()).contains("First page").contains("Second page");
    }

    @Test
    void scannedPdfWithoutTextLayerIsExplained() {
        Path pdf = write(dir, "in", TestFiles.pdf((String) null));

        assertThatThrownBy(() -> run(new PdfToTextConverter(LIMITS), source(pdf, FileFormat.PDF)))
                .extracting("code").isEqualTo("PDF_HAS_NO_TEXT");
    }

    @Test
    void passwordProtectedPdfIsExplained() {
        Path pdf = write(dir, "in", TestFiles.encryptedPdf());

        assertThatThrownBy(() -> run(new PdfToTextConverter(LIMITS), source(pdf, FileFormat.PDF)))
                .extracting("code").isEqualTo("PDF_ENCRYPTED");
    }

    @Test
    void garbageAfterThePdfSignatureIsCorrupt() {
        Path pdf = write(dir, "in", "%PDF-1.7 this is not really a pdf");

        assertThatThrownBy(() -> run(new PdfToTextConverter(LIMITS), source(pdf, FileFormat.PDF)))
                .extracting("code").isEqualTo("CORRUPT_FILE");
    }

    @Test
    void singlePageBecomesOnePng() throws IOException {
        Path pdf = write(dir, "in", TestFiles.pdf("Only page"));

        Converted png = run(new PdfToPngConverter(LIMITS), source(pdf, FileFormat.PDF));

        assertThat(png.format()).isEqualTo(FileFormat.PNG);
        // US Letter at 72 dpi
        assertThat(decode(png.bytes()).getWidth()).isEqualTo(612);
    }

    @Test
    void severalPagesBecomeAZipOfNumberedPngs() throws IOException {
        Path pdf = write(dir, "in", TestFiles.pdf("one", "two", "three"));

        Converted zip = run(new PdfToPngConverter(LIMITS), source(pdf, FileFormat.PDF));

        assertThat(zip.format()).isEqualTo(FileFormat.ZIP);
        assertThat(entries(zip.bytes())).containsExactly("page-01.png", "page-02.png", "page-03.png");
    }

    @Test
    void renderingHasItsOwnPageLimit() {
        ConversionLimits twoPages = new ConversionLimits(50_000_000, 300, 2, 72, 1_000_000, 0.9f);
        Path pdf = write(dir, "in", TestFiles.pdf("one", "two", "three"));

        assertThatThrownBy(() -> run(new PdfToPngConverter(twoPages), source(pdf, FileFormat.PDF)))
                .hasMessage("The PDF has 3 pages; the limit for this conversion is 2.")
                .extracting("code").isEqualTo("TOO_MANY_PAGES");
    }

    @Test
    void pageBitmapSizeIsCheckedBeforeRendering() {
        ConversionLimits smallBitmaps = new ConversionLimits(10_000, 300, 30, 72, 1_000_000, 0.9f);
        Path pdf = write(dir, "in", TestFiles.pdf("big"));

        assertThatThrownBy(() -> run(new PdfToPngConverter(smallBitmaps), source(pdf, FileFormat.PDF)))
                .extracting("code").isEqualTo("PAGE_TOO_LARGE");
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
