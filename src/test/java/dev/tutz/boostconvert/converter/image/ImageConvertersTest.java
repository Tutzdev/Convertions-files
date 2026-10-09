package dev.tutz.boostconvert.converter.image;

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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageConvertersTest {

    @TempDir
    Path dir;

    @Test
    void pngToJpgTurnsTransparencyWhite() throws IOException {
        Path png = write(dir, "in", TestFiles.png(20, 10));

        Converted jpg = run(new PngToJpgConverter(LIMITS), source(png, FileFormat.PNG));

        assertThat(jpg.format()).isEqualTo(FileFormat.JPG);
        BufferedImage image = decode(jpg.bytes());
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getColorModel().hasAlpha()).isFalse();
        assertThat(near(image.getRGB(15, 5), Color.WHITE)).as("transparent half").isTrue();
        assertThat(near(image.getRGB(2, 5), new Color(240, 138, 36))).as("opaque half").isTrue();
    }

    @Test
    void jpgToPngKeepsDimensions() throws IOException {
        Path jpg = write(dir, "in", TestFiles.jpg(33, 17));

        Converted png = run(new JpgToPngConverter(LIMITS), source(jpg, FileFormat.JPG));

        assertThat(png.format()).isEqualTo(FileFormat.PNG);
        BufferedImage image = decode(png.bytes());
        assertThat(image.getWidth()).isEqualTo(33);
        assertThat(image.getHeight()).isEqualTo(17);
    }

    @Test
    void webpToPngKeepsTransparency() throws IOException {
        Path webp = write(dir, "in", TestFiles.webp());

        Converted png = run(new WebpToPngConverter(LIMITS), source(webp, FileFormat.WEBP));

        BufferedImage image = decode(png.bytes());
        assertThat(image.getWidth()).isEqualTo(40);
        assertThat(image.getRGB(5, 5) >>> 24).as("opaque alpha").isEqualTo(255);
        assertThat(image.getRGB(35, 5) >>> 24).as("transparent alpha").isZero();
    }

    @Test
    void imageToPdfUsesLandscapeForWideImagesAndNeverUpscales() throws IOException {
        Path wide = write(dir, "in", TestFiles.png(400, 200));

        Converted pdf = run(new ImageToPdfConverter(LIMITS), source(wide, FileFormat.PNG));

        assertThat(pdf.format()).isEqualTo(FileFormat.PDF);
        try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
            assertThat(document.getNumberOfPages()).isOne();
            PDPage page = document.getPage(0);
            assertThat(page.getMediaBox().getWidth()).isGreaterThan(page.getMediaBox().getHeight());
        }
    }

    @Test
    void imageToPdfEmbedsJpgAsIs() throws IOException {
        byte[] original = TestFiles.jpg(120, 300);
        Path jpg = write(dir, "in", original);

        Converted pdf = run(new ImageToPdfConverter(LIMITS), source(jpg, FileFormat.JPG));

        try (PDDocument document = Loader.loadPDF(pdf.bytes())) {
            PDPage page = document.getPage(0);
            assertThat(page.getMediaBox().getHeight()).isGreaterThan(page.getMediaBox().getWidth());
            PDResources resources = page.getResources();
            PDXObject image = resources.getXObject(resources.getXObjectNames().iterator().next());
            assertThat(image.getCOSObject().getCOSName(COSName.FILTER)).isEqualTo(COSName.DCT_DECODE);
        }
    }

    @Test
    void decompressionBombIsRejectedBeforeDecoding() {
        ConversionLimits tiny = new ConversionLimits(100, 300, 30, 72, 1_000_000, 0.9f);
        Path png = write(dir, "in", TestFiles.png(20, 10));

        assertThatThrownBy(() -> run(new PngToJpgConverter(tiny), source(png, FileFormat.PNG)))
                .hasMessage("The image has 200 pixels; the limit is 100.")
                .extracting("code").isEqualTo("IMAGE_TOO_LARGE");
    }

    @Test
    void truncatedImageIsReportedAsCorrupt() {
        byte[] png = TestFiles.png(50, 50);
        byte[] truncated = Arrays.copyOf(png, 40);
        Path file = write(dir, "in", truncated);

        assertThatThrownBy(() -> run(new PngToJpgConverter(LIMITS), source(file, FileFormat.PNG)))
                .extracting("code").isEqualTo("CORRUPT_FILE");
    }

    private static boolean near(int rgb, Color expected) {
        Color actual = new Color(rgb);
        return Math.abs(actual.getRed() - expected.getRed()) < 12
                && Math.abs(actual.getGreen() - expected.getGreen()) < 12
                && Math.abs(actual.getBlue() - expected.getBlue()) < 12;
    }
}
