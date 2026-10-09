package dev.tutz.boostconvert.converter.data;

import dev.tutz.boostconvert.common.FileNames;
import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionFailedException;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Reads the sheet XML with SAX instead of loading the workbook object model, which needs several
 * times the file size in heap. Values come out the way Excel displays them (dates, percentages),
 * and formulas use the result Excel saved. One sheet gives a CSV; several give a ZIP of CSVs.
 */
@Component
public class XlsxToCsvConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("xlsx-to-csv", "XLSX to CSV", Category.DATA,
            Set.of(FileFormat.XLSX), FileFormat.CSV,
            "Values as Excel shows them, formulas as their saved results. A workbook with several sheets becomes a ZIP.");

    private final ConversionLimits limits;

    public XlsxToCsvConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        List<Sheet> sheets = readSheets(source.path());
        if (sheets.isEmpty()) {
            throw new ConversionFailedException("EMPTY_FILE", "The spreadsheet has no data.");
        }
        if (sheets.size() == 1) {
            writeCsv(sheets.getFirst(), target);
            return FileFormat.CSV;
        }

        // Not closed: closing the ZIP would close the target, which belongs to the caller.
        ZipOutputStream zip = new ZipOutputStream(target);
        Set<String> usedNames = new HashSet<>();
        for (Sheet sheet : sheets) {
            String name = FileNames.sanitize(sheet.name());
            for (int suffix = 2; !usedNames.add(name.toLowerCase(Locale.ROOT)); suffix++) {
                name = FileNames.sanitize(sheet.name()) + " (" + suffix + ")";
            }
            zip.putNextEntry(new ZipEntry(name + ".csv"));
            writeCsv(sheet, zip);
            zip.closeEntry();
        }
        zip.finish();
        return FileFormat.ZIP;
    }

    private List<Sheet> readSheets(Path file) {
        CellBudget budget = new CellBudget(limits.maxCells());
        // Locale.US gives a dot as decimal separator, which never collides with the CSV comma.
        DataFormatter formatter = new DataFormatter(Locale.US);
        List<Sheet> sheets = new ArrayList<>();
        try (OPCPackage workbook = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(workbook);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(workbook);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator iterator = reader.getSheetIterator();
            while (iterator.hasNext()) {
                try (InputStream sheetXml = iterator.next()) {
                    RowCollector rows = new RowCollector(budget);
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, rows, formatter, false));
                    parser.parse(new InputSource(sheetXml));
                    if (rows.hasData()) {
                        sheets.add(new Sheet(iterator.getSheetName(), rows.rows()));
                    }
                }
            }
        } catch (IOException | OpenXML4JException | SAXException | POIXMLException | UnsupportedFileFormatException
                | InvalidOperationException corrupt) {
            throw new ConversionFailedException("CORRUPT_FILE", "The spreadsheet is damaged or is not a valid XLSX file.",
                    corrupt);
        } catch (ParserConfigurationException misconfigured) {
            throw new IllegalStateException("The XML parser could not be created", misconfigured);
        }
        return sheets;
    }

    /** Every row gets the same number of columns, as RFC 4180 expects and Excel itself writes. */
    private static void writeCsv(Sheet sheet, OutputStream target) throws IOException {
        int width = sheet.rows().stream().mapToInt(List::size).max().orElse(0);
        Writer writer = new OutputStreamWriter(target, StandardCharsets.UTF_8);
        CSVPrinter csv = new CSVPrinter(writer, CSVFormat.RFC4180);
        for (List<String> row : sheet.rows()) {
            List<String> padded = new ArrayList<>(row);
            while (padded.size() < width) {
                padded.add("");
            }
            csv.printRecord(padded);
        }
        csv.flush();
    }

    private record Sheet(String name, List<List<String>> rows) {
    }

    /** Receives cells from the SAX handler. Excel leaves empty rows and cells out of the XML. */
    private static final class RowCollector implements SheetContentsHandler {

        private final CellBudget budget;
        private final List<List<String>> rows = new ArrayList<>();
        private List<String> current;
        private boolean hasData;

        RowCollector(CellBudget budget) {
            this.budget = budget;
        }

        @Override
        public void startRow(int rowNumber) {
            while (rows.size() < rowNumber) {
                rows.add(List.of());
            }
            current = new ArrayList<>();
        }

        @Override
        public void cell(String reference, String value, XSSFComment comment) {
            int column = reference == null ? current.size() : new CellReference(reference).getCol();
            while (current.size() < column) {
                current.add("");
            }
            budget.spend(1);
            String text = value == null ? "" : value;
            current.add(text);
            hasData |= !text.isEmpty();
        }

        @Override
        public void endRow(int rowNumber) {
            rows.add(current);
        }

        boolean hasData() {
            return hasData;
        }

        List<List<String>> rows() {
            // Styled but empty rows at the end of a sheet carry no data.
            int last = rows.size();
            while (last > 0 && rows.get(last - 1).stream().allMatch(String::isEmpty)) {
                last--;
            }
            return rows.subList(0, last);
        }
    }
}
