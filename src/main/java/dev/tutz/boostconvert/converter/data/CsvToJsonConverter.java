package dev.tutz.boostconvert.converter.data;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionFailedException;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams rows straight into the JSON writer, so memory does not grow with the file. Values stay
 * strings on purpose: guessing types would turn the ZIP code {@code 01310} into the number 1310.
 */
@Component
public class CsvToJsonConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("csv-to-json", "CSV to JSON", Category.DATA,
            Set.of(FileFormat.CSV), FileFormat.JSON,
            "An array of objects keyed by the header row. Detects comma, semicolon or tab, and keeps values as text.");

    private final JsonMapper mapper;
    private final ConversionLimits limits;

    public CsvToJsonConverter(JsonMapper mapper, ConversionLimits limits) {
        this.mapper = mapper;
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        try (BufferedReader reader = source.openReader()) {
            CSVFormat format = CSVFormat.RFC4180.builder()
                    .setDelimiter(CsvDialect.detectDelimiter(reader))
                    .setIgnoreEmptyLines(true)
                    .get();
            try (CSVParser parser = format.parse(reader)) {
                writeRecords(parser.iterator(), target);
            }
        } catch (UncheckedIOException malformed) {
            throw new ConversionFailedException("CSV_MALFORMED",
                    "The CSV is malformed, usually because of a quote that is never closed.", malformed);
        }
        return FileFormat.JSON;
    }

    private void writeRecords(Iterator<CSVRecord> records, OutputStream target) {
        if (!records.hasNext()) {
            throw new ConversionFailedException("EMPTY_FILE", "The CSV file is empty.");
        }
        List<String> headers = CsvDialect.uniqueHeaders(records.next().toList());
        CellBudget budget = new CellBudget(limits.maxCells());

        try (JsonGenerator json = mapper.writer().withDefaultPrettyPrinter().createGenerator(target)) {
            json.writeStartArray();
            while (records.hasNext()) {
                CSVRecord record = records.next();
                requireFitsHeader(record, headers.size());
                budget.spend(headers.size());
                json.writeStartObject();
                for (int column = 0; column < headers.size(); column++) {
                    json.writeName(headers.get(column));
                    if (column < record.size()) {
                        json.writeString(record.get(column));
                    } else {
                        json.writeNull();
                    }
                }
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }

    /** Short rows are padded with null; extra cells are only tolerated when they are empty. */
    private static void requireFitsHeader(CSVRecord record, int columns) {
        for (int extra = columns; extra < record.size(); extra++) {
            if (!record.get(extra).isBlank()) {
                throw new ConversionFailedException("CSV_MALFORMED", "Row %d has %d values but the header has %d columns."
                        .formatted(record.getRecordNumber(), record.size(), columns));
            }
        }
    }
}
