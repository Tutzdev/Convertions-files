package dev.tutz.boostconvert.converter.image;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * One image on one A4 page, turned to landscape for wide images. JPG bytes are embedded as they are
 * (PDF supports JPEG compression natively), so the photo is not compressed a second time.
 */
@Component
public class ImageToPdfConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("image-to-pdf", "Image to PDF", Category.IMAGE,
            Set.of(FileFormat.JPG, FileFormat.PNG), FileFormat.PDF,
            "Puts a JPG or PNG on an A4 page, centred and scaled down to fit. Ready to print or attach.");

    private static final float MARGIN = 36;
    private static final float POINTS_PER_PIXEL = 72f / 96f;

    private final ConversionLimits limits;

    public ImageToPdfConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDImageXObject image = embed(source, document);
            PDRectangle a4 = PDRectangle.A4;
            boolean landscape = image.getWidth() > image.getHeight();
            PDPage page = new PDPage(landscape ? new PDRectangle(a4.getHeight(), a4.getWidth()) : a4);
            document.addPage(page);

            PDRectangle box = page.getMediaBox();
            float naturalWidth = image.getWidth() * POINTS_PER_PIXEL;
            float naturalHeight = image.getHeight() * POINTS_PER_PIXEL;
            // Shrink to fit, never enlarge: an upscaled icon only looks blurry on paper.
            float scale = Math.min(1f, Math.min((box.getWidth() - 2 * MARGIN) / naturalWidth,
                    (box.getHeight() - 2 * MARGIN) / naturalHeight));
            float width = naturalWidth * scale;
            float height = naturalHeight * scale;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, (box.getWidth() - width) / 2, (box.getHeight() - height) / 2, width, height);
            }
            document.save(target);
        }
        return FileFormat.PDF;
    }

    private PDImageXObject embed(SourceFile source, PDDocument document) throws IOException {
        if (source.format() == FileFormat.PNG) {
            return LosslessFactory.createFromImage(document, ImageFiles.read(source.path(), limits.maxImagePixels()));
        }
        ImageFiles.checkDimensions(source.path(), limits.maxImagePixels());
        try (InputStream jpeg = Files.newInputStream(source.path())) {
            return JPEGFactory.createFromStream(document, jpeg);
        } catch (IOException | RuntimeException corrupt) {
            throw ImageFiles.unreadable(corrupt);
        }
    }
}
