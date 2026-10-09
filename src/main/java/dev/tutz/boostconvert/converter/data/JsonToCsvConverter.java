package dev.tutz.boostconvert.converter.data;

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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An array of objects becomes one row per object. Nested objects are flattened into dotted columns
 * ({@code address.city}), arrays are kept as JSON text in a single cell, and the header is the union
 * of every key in first-seen order, so objects with different keys still line up.
 */
@Component
public class JsonToCsvConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("json-to-csv", "JSON to CSV", Category.DATA,
            Set.of(FileFormat.JSON), FileFormat.CSV,
            "One row per object. Nested objects become dotted columns such as address.city.");

    private final JsonMapper mapper;
    private final ConversionLimits limits;

    public JsonToCsvConverter(JsonMapper mapper, ConversionLimits limits) {
        this.mapper = mapper;
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        Set<String> headers = new LinkedHashSet<>();
        for (JsonNode object : objects(read(source))) {
            Map<String, String> row = new LinkedHashMap<>();
            flatten("", object, row);
            headers.addAll(row.keySet());
            rows.add(row);
        }
        new CellBudget(limits.maxCells()).spend((long) rows.size() * headers.size());

        CSVPrinter csv = new CSVPrinter(new OutputStreamWriter(target, StandardCharsets.UTF_8), CSVFormat.RFC4180);
        csv.printRecord(headers);
        for (Map<String, String> row : rows) {
            csv.printRecord(headers.stream().map(header -> row.getOrDefault(header, "")));
        }
        csv.flush();
        return FileFormat.CSV;
    }

    private JsonNode read(SourceFile source) throws IOException {
        try (Reader reader = source.openReader()) {
            return mapper.readTree(reader);
        } catch (JacksonException invalid) {
            String where = invalid.getLocation() == null ? ""
                    : " (line %d, column %d)".formatted(invalid.getLocation().getLineNr(),
                            invalid.getLocation().getColumnNr());
            throw new ConversionFailedException("JSON_INVALID", "The file is not valid JSON" + where + ".", invalid);
        }
    }

    private static List<JsonNode> objects(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            throw new ConversionFailedException("EMPTY_FILE", "The JSON file is empty.");
        }
        if (root.isObject()) {
            return List.of(root);
        }
        if (!root.isArray() || !root.values().stream().allMatch(JsonNode::isObject)) {
            throw new ConversionFailedException("JSON_SHAPE_UNSUPPORTED",
                    "Expected an array of objects, like [{\"name\": \"Ana\"}, {\"name\": \"Bruno\"}].");
        }
        if (root.isEmpty()) {
            throw new ConversionFailedException("EMPTY_FILE", "The JSON array is empty.");
        }
        return List.copyOf(root.values());
    }

    private void flatten(String prefix, JsonNode node, Map<String, String> row) {
        // A nested empty object still gets its column ("{}"), so the key is not silently lost.
        if (node.isObject() && (prefix.isEmpty() || !node.isEmpty())) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                String key = prefix.isEmpty() ? property.getKey() : prefix + "." + property.getKey();
                flatten(key, property.getValue(), row);
            }
        } else if (node.isContainer()) {
            row.put(prefix, mapper.writeValueAsString(node));
        } else if (node.isNull()) {
            row.put(prefix, "");
        } else {
            row.put(prefix, node.asString());
        }
    }
}
