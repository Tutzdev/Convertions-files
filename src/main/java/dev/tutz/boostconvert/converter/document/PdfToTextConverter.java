package dev.tutz.boostconvert.converter.document;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionFailedException;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfToTextConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("pdf-to-txt", "PDF to TXT", Category.DOCUMENT,
            Set.of(FileFormat.PDF), FileFormat.TXT,
            "Extracts the text layer as UTF-8. Scanned PDFs have no text layer and are reported as such.");

    private final ConversionLimits limits;

    public PdfToTextConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        String text;
        try (PDDocument document = PdfDocuments.open(source.path(), limits.maxPdfPages())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            text = stripper.getText(document);
        }
        if (text.isBlank()) {
            throw new ConversionFailedException("PDF_HAS_NO_TEXT",
                    "This PDF has no text layer; it is probably a scanned image. OCR is not supported.");
        }
        Writer writer = new OutputStreamWriter(target, StandardCharsets.UTF_8);
        writer.write(text);
        writer.flush();
        return FileFormat.TXT;
    }
}
