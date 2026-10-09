package dev.tutz.boostconvert.converter.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The parts of a CSV file that are not standardised: the delimiter and the header names. */
final class CsvDialect {

    private static final char[] DELIMITERS = {',', ';', '\t', '|'};
    private static final int SAMPLE_SIZE = 64 * 1024;

    private CsvDialect() {
    }

    /**
     * Picks the delimiter that appears most often, outside quotes, in the first line. Excel writes
     * semicolons wherever the decimal separator is a comma (Brazil, most of Europe). Ties go to comma.
     * The reader is reset, so parsing starts from the beginning.
     */
    static char detectDelimiter(BufferedReader reader) throws IOException {
        reader.mark(SAMPLE_SIZE);
        char[] sample = new char[SAMPLE_SIZE];
        int length = reader.read(sample, 0, SAMPLE_SIZE);
        reader.reset();

        int[] counts = new int[DELIMITERS.length];
        boolean quoted = false;
        for (int i = 0; i < length; i++) {
            char current = sample[i];
            if (current == '"') {
                quoted = !quoted;
            } else if (!quoted && (current == '\n' || current == '\r')) {
                break;
            } else if (!quoted) {
                for (int d = 0; d < DELIMITERS.length; d++) {
                    if (current == DELIMITERS[d]) {
                        counts[d]++;
                    }
                }
            }
        }

        int best = 0;
        for (int d = 1; d < DELIMITERS.length; d++) {
            if (counts[d] > counts[best]) {
                best = d;
            }
        }
        return DELIMITERS[best];
    }

    /** JSON keys must be unique and non-empty: blank headers get a position name, repeats get a suffix. */
    static List<String> uniqueHeaders(List<String> raw) {
        Set<String> seen = new HashSet<>();
        List<String> headers = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String base = raw.get(i).strip();
            if (base.isEmpty()) {
                base = "column_" + (i + 1);
            }
            String name = base;
            for (int suffix = 2; !seen.add(name); suffix++) {
                name = base + "_" + suffix;
            }
            headers.add(name);
        }
        return headers;
    }
}
