package dev.tutz.boostconvert.converter.data;

import static dev.tutz.boostconvert.TestFiles.LIMITS;
import static dev.tutz.boostconvert.TestFiles.run;
import static dev.tutz.boostconvert.TestFiles.source;
import static dev.tutz.boostconvert.TestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JsonToCsvConverterTest {

    private final JsonToCsvConverter converter = new JsonToCsvConverter(JsonMapper.builder().build(), LIMITS);

    @TempDir
    Path dir;

    @Test
    void nestedObjectsBecomeDottedColumnsAndArraysStayJson() throws IOException {
        String csv = convert("""
                [
                  {"name": "Ana", "address": {"city": "Recife", "zip": "50000"}, "tags": ["a", "b"]},
                  {"name": "Bruno", "address": {"city": "Natal"}, "age": 31, "vip": true, "note": null}
                ]
                """);

        assertThat(csv).isEqualTo("""
                name,address.city,address.zip,tags,age,vip,note\r
                Ana,Recife,50000,"[""a"",""b""]",,,\r
                Bruno,Natal,,,31,true,\r
                """);
    }

    @Test
    void singleObjectIsOneRow() throws IOException {
        assertThat(convert("{\"id\": 7, \"city\": \"São Paulo\"}")).isEqualTo("id,city\r\n7,São Paulo\r\n");
    }

    @Test
    void valuesWithCommasAndQuotesAreEscaped() throws IOException {
        assertThat(convert("[{\"text\": \"say \\\"hi\\\", please\"}]"))
                .isEqualTo("text\r\n\"say \"\"hi\"\", please\"\r\n");
    }

    @Test
    void arrayOfScalarsHasNoColumnsToMapTo() {
        assertThatThrownBy(() -> convert("[1, 2, 3]"))
                .extracting("code").isEqualTo("JSON_SHAPE_UNSUPPORTED");
    }

    @Test
    void invalidJsonReportsWhereItBroke() {
        assertThatThrownBy(() -> convert("[{\"a\": 1,}]"))
                .hasMessageStartingWith("The file is not valid JSON (line 1")
                .extracting("code").isEqualTo("JSON_INVALID");
    }

    @Test
    void emptyArrayIsRejected() {
        assertThatThrownBy(() -> convert("[]")).extracting("code").isEqualTo("EMPTY_FILE");
    }

    private String convert(String json) throws IOException {
        Path file = write(dir, "in.json", json);
        return run(converter, source(file, FileFormat.JSON)).text();
    }
}
