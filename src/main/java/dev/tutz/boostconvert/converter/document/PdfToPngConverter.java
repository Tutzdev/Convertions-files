package dev.tutz.boostconvert.converter.document;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionFailedException;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.converter.image.ImageFiles;
import dev.tutz.boostconvert.format.FileFormat;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/** Renders every page. One page gives a PNG; several pages give a ZIP with page-01.png, page-02.png... */
@Component
public class PdfToPngConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("pdf-to-png", "PDF to PNG", Category.DOCUMENT,
            Set.of(FileFormat.PDF), FileFormat.PNG,
            "One image per page at print-friendly resolution. Several pages are delivered as a ZIP.");

    private final ConversionLimits limits;

    public PdfToPngConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        try (PDDocument document = PdfDocuments.open(source.path(), limits.maxPdfPages())) {
            int pages = document.getNumberOfPages();
            if (pages > limits.maxRenderedPages()) {
                throw PdfDocuments.tooManyPages(pages, limits.maxRenderedPages());
            }
            PDFRenderer renderer = new PDFRenderer(document);
            if (pages == 1) {
                ImageFiles.writePng(render(document, renderer, 0), target);
                return FileFormat.PNG;
            }

            // Not closed: closing the ZIP would close the target, which belongs to the caller.
            ZipOutputStream zip = new ZipOutputStream(target);
            String nameFormat = "page-%0" + Math.max(2, String.valueOf(pages).length()) + "d.png";
            for (int page = 0; page < pages; page++) {
                zip.putNextEntry(new ZipEntry(nameFormat.formatted(page + 1)));
                ImageFiles.writePng(render(document, renderer, page), zip);
                zip.closeEntry();
            }
            zip.finish();
            return FileFormat.ZIP;
        }
    }

    private BufferedImage render(PDDocument document, PDFRenderer renderer, int page) throws IOException {
        // A page can declare any size; check the bitmap it would produce before allocating it.
        PDRectangle box = document.getPage(page).getCropBox();
        float scale = limits.renderDpi() / 72f;
        long pixels = (long) Math.ceil(box.getWidth() * scale) * (long) Math.ceil(box.getHeight() * scale);
        if (pixels > limits.maxImagePixels()) {
            throw new ConversionFailedException("PAGE_TOO_LARGE", "Page %d is too large to render (%,d pixels at %d dpi)."
                    .formatted(page + 1, pixels, limits.renderDpi()));
        }
        return renderer.renderImageWithDPI(page, limits.renderDpi(), ImageType.RGB);
    }
}
