package dev.tutz.boostconvert;

import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds real files in memory, so tests never depend on binary fixtures they cannot explain. */
public final class TestFiles {

    public static final ConversionLimits LIMITS = new ConversionLimits(50_000_000, 300, 30, 72, 1_000_000, 0.9f);

    private TestFiles() {
    }

    public static SourceFile source(Path file, FileFormat format) {
        return new SourceFile(file, format, StandardCharsets.UTF_8);
    }

    public static Converted run(Converter converter, SourceFile source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileFormat format = converter.convert(source, out);
        return new Converted(format, out.toByteArray());
    }

    public record Converted(FileFormat format, byte[] bytes) {

        public String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public static Path write(Path directory, String name, byte[] content) {
        try {
            return Files.write(directory.resolve(name), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path write(Path directory, String name, String content) {
        return write(directory, name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Left half opaque orange, right half fully transparent. */
    public static byte[] png(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(240, 138, 36));
        graphics.fillRect(0, 0, width / 2, height);
        graphics.dispose();
        return encode(image, "png");
    }

    public static byte[] jpg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(32, 63, 58));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return encode(image, "jpg");
    }

    public static byte[] webp() {
        try (InputStream in = TestFiles.class.getResourceAsStream("/fixtures/half-transparent.webp")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One page per text; a {@code null} text leaves the page blank, like a scan without OCR. */
    public static byte[] pdf(String... pageTexts) {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text != null) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(font, 14);
                        content.newLineAtOffset(72, 700);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encryptedPdf() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret",
                    new AccessPermission());
            document.protect(policy);
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sheet name to rows; a {@code null} cell is left out of the file, as Excel does with empty cells. */
    public static byte[] xlsx(Map<String, Object[][]> sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            for (Map.Entry<String, Object[][]> entry : sheets.entrySet()) {
                Sheet sheet = workbook.createSheet(entry.getKey());
                Object[][] rows = entry.getValue();
                for (int r = 0; r < rows.length; r++) {
                    if (rows[r] == null) {
                        continue;
                    }
                    Row row = sheet.createRow(r);
                    for (int c = 0; c < rows[r].length; c++) {
                        Object value = rows[r][c];
                        if (value == null) {
                            continue;
                        }
                        Cell cell = row.createCell(c);
                        if (value instanceof Number number) {
                            cell.setCellValue(number.doubleValue());
                        } else if (value instanceof String text && text.startsWith("=")) {
                            cell.setCellFormula(text.substring(1));
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                }
            }
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, Object[][]> sheets(Object... nameAndRows) {
        Map<String, Object[][]> sheets = new LinkedHashMap<>();
        for (int i = 0; i < nameAndRows.length; i += 2) {
            sheets.put((String) nameAndRows[i], (Object[][]) nameAndRows[i + 1]);
        }
        return sheets;
    }

    public static BufferedImage decode(byte[] image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
