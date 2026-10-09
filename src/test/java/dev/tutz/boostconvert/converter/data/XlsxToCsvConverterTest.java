package dev.tutz.boostconvert.converter.data;

import static dev.tutz.boostconvert.TestFiles.LIMITS;
import static dev.tutz.boostconvert.TestFiles.run;
import static dev.tutz.boostconvert.TestFiles.sheets;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XlsxToCsvConverterTest {

    private final XlsxToCsvConverter converter = new XlsxToCsvConverter(LIMITS);

    @TempDir
    Path dir;

    @Test
    void singleSheetBecomesOneCsvWithSavedFormulaResults() throws IOException {
        byte[] workbook = TestFiles.xlsx(sheets("Sales", new Object[][] {
                {"product", "qty", "price", "total"},
                {"Coffee", 2, 9.5, "=B2*C2"},
                {"Tea, green", 1, 4, "=B3*C3"}}));

        Converted csv = convert(workbook);

        assertThat(csv.format()).isEqualTo(FileFormat.CSV);
        assertThat(csv.text()).isEqualTo("""
                product,qty,price,total\r
                Coffee,2,9.5,19\r
                "Tea, green",1,4,4\r
                """);
    }

    @Test
    void cellsAndRowsExcelLeftOutArePaddedBack() throws IOException {
        byte[] workbook = TestFiles.xlsx(sheets("Gaps", new Object[][] {
                {"a", null, "c"},
                null,
                {null, "y"}}));

        // Commons CSV quotes an empty first value, so a blank row is not read back as a row with no values.
        assertThat(convert(workbook).text()).isEqualTo("a,,c\r\n\"\",,\r\n\"\",y,\r\n");
    }

    @Test
    void severalSheetsBecomeAZipAndEmptySheetsAreSkipped() throws IOException {
        byte[] workbook = TestFiles.xlsx(sheets(
                "Q1", new Object[][] {{"month", "revenue"}, {"Jan", 10}},
                "Empty", new Object[][] {},
                "Q2 | 2026", new Object[][] {{"month", "revenue"}, {"Apr", 12}}));

        Converted zip = convert(workbook);

        assertThat(zip.format()).isEqualTo(FileFormat.ZIP);
        Map<String, String> files = unzip(zip.bytes());
        assertThat(files).containsOnlyKeys("Q1.csv", "Q2 2026.csv");
        assertThat(files.get("Q1.csv")).isEqualTo("month,revenue\r\nJan,10\r\n");
    }

    @Test
    void workbookWithoutDataIsRejected() {
        byte[] workbook = TestFiles.xlsx(sheets("Empty", new Object[][] {}));

        assertThatThrownBy(() -> convert(workbook)).extracting("code").isEqualTo("EMPTY_FILE");
    }

    @Test
    void cellLimitStopsTheConversion() {
        XlsxToCsvConverter limited = new XlsxToCsvConverter(new ConversionLimits(50_000_000, 300, 30, 72, 3, 0.9f));
        Path file = write(dir, "in.xlsx", TestFiles.xlsx(sheets("S", new Object[][] {{"a", "b"}, {"c", "d"}})));

        assertThatThrownBy(() -> run(limited, source(file, FileFormat.XLSX)))
                .extracting("code").isEqualTo("TOO_MANY_CELLS");
    }

    @Test
    void zipThatIsNotAWorkbookIsCorrupt() {
        Path file = write(dir, "in.xlsx", new byte[] {'P', 'K', 3, 4, 1, 2, 3});

        assertThatThrownBy(() -> run(converter, source(file, FileFormat.XLSX)))
                .extracting("code").isEqualTo("CORRUPT_FILE");
    }

    private Converted convert(byte[] workbook) throws IOException {
        return run(converter, source(write(dir, "in.xlsx", workbook), FileFormat.XLSX));
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
