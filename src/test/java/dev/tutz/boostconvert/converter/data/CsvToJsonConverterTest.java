package dev.tutz.boostconvert.converter.data;

import static dev.tutz.boostconvert.TestFiles.LIMITS;
import static dev.tutz.boostconvert.TestFiles.run;
import static dev.tutz.boostconvert.TestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CsvToJsonConverterTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CsvToJsonConverter converter = new CsvToJsonConverter(mapper, LIMITS);

    @TempDir
    Path dir;

    @Test
    void keysComeFromTheHeaderAndValuesStayText() throws IOException {
        JsonNode rows = convert("id,zip,active\n1,01310,true\n2,20040,false\n");

        assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.get(0).get("zip").stringValue()).isEqualTo("01310");
        assertThat(rows.get(0).get("active").isString()).isTrue();
        assertThat(rows.get(1).get("id").stringValue()).isEqualTo("2");
    }

    @Test
    void excelCsvFromABrazilianWindowsIsUnderstood() throws IOException {
        // semicolons, Windows-1252 and CRLF line endings
        Charset windows1252 = Charset.forName("windows-1252");
        byte[] bytes = "nome;cidade;valor\r\nJoão;São Paulo;1.234,50\r\n".getBytes(windows1252);
        Path file = write(dir, "in.csv", bytes);

        JsonNode rows = mapper.readTree(run(converter, new SourceFile(file, FileFormat.CSV, windows1252)).bytes());

        assertThat(rows.get(0).get("nome").stringValue()).isEqualTo("João");
        assertThat(rows.get(0).get("cidade").stringValue()).isEqualTo("São Paulo");
        assertThat(rows.get(0).get("valor").stringValue()).isEqualTo("1.234,50");
    }

    @Test
    void quotedFieldsMayContainDelimitersAndLineBreaks() throws IOException {
        JsonNode rows = convert("﻿name,notes\n\"Silva, Ana\",\"line one\nline two\"\n");

        assertThat(rows.get(0).get("name").stringValue()).isEqualTo("Silva, Ana");
        assertThat(rows.get(0).get("notes").stringValue()).isEqualTo("line one\nline two");
    }

    @Test
    void blankAndRepeatedHeadersGetUniqueNames() throws IOException {
        JsonNode rows = convert("name,,name\na,b,c\n");

        assertThat(rows.get(0).propertyNames()).containsExactly("name", "column_2", "name_2");
    }

    @Test
    void shortRowsArePaddedWithNull() throws IOException {
        JsonNode rows = convert("a,b,c\n1,2\n");

        assertThat(rows.get(0).get("c").isNull()).isTrue();
    }

    @Test
    void rowWiderThanTheHeaderIsRejected() {
        assertThatThrownBy(() -> convert("a,b\n1,2,3\n"))
                .hasMessage("Row 2 has 3 values but the header has 2 columns.")
                .extracting("code").isEqualTo("CSV_MALFORMED");
    }

    @Test
    void unclosedQuoteIsMalformed() {
        assertThatThrownBy(() -> convert("a,b\n\"open,2\n"))
                .extracting("code").isEqualTo("CSV_MALFORMED");
    }

    @Test
    void cellLimitStopsTheConversion() {
        CsvToJsonConverter limited = new CsvToJsonConverter(mapper, new ConversionLimits(50_000_000, 300, 30, 72, 4, 0.9f));
        Path file = write(dir, "in.csv", "a,b\n1,2\n3,4\n5,6\n");

        assertThatThrownBy(() -> run(limited, new SourceFile(file, FileFormat.CSV, StandardCharsets.UTF_8)))
                .extracting("code").isEqualTo("TOO_MANY_CELLS");
    }

    private JsonNode convert(String csv) throws IOException {
        Path file = write(dir, "in.csv", csv);
        return mapper.readTree(run(converter, new SourceFile(file, FileFormat.CSV, StandardCharsets.UTF_8)).bytes());
    }
}
