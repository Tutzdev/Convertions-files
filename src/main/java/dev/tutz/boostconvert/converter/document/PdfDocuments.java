package dev.tutz.boostconvert.converter.document;

import dev.tutz.boostconvert.converter.ConversionFailedException;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/** Opens uploaded PDFs and turns the library's exceptions into messages a user can act on. */
final class PdfDocuments {

    private PdfDocuments() {
    }

    static PDDocument open(Path file, int maxPages) {
        PDDocument document;
        try {
            document = Loader.loadPDF(file.toFile());
        } catch (InvalidPasswordException encrypted) {
            throw new ConversionFailedException("PDF_ENCRYPTED", "The PDF is password protected. Remove the password and try again.");
        } catch (IOException corrupt) {
            throw new ConversionFailedException("CORRUPT_FILE", "The PDF is damaged and could not be opened.", corrupt);
        }
        int pages = document.getNumberOfPages();
        if (pages > maxPages) {
            closeQuietly(document);
            throw tooManyPages(pages, maxPages);
        }
        return document;
    }

    static ConversionFailedException tooManyPages(int pages, int maxPages) {
        return new ConversionFailedException("TOO_MANY_PAGES",
                "The PDF has %d pages; the limit for this conversion is %d.".formatted(pages, maxPages));
    }

    private static void closeQuietly(PDDocument document) {
        try {
            document.close();
        } catch (IOException ignored) {
            // the document is being rejected anyway
        }
    }
}
