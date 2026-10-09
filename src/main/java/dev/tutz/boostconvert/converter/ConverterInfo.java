package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.format.FileFormat;
import java.util.Set;

/**
 * What a converter does, as shown in the catalog.
 *
 * @param id    stable id used in the API and in metrics, e.g. {@code png-to-jpg}
 * @param from  formats it reads (more than one when they share the same pipeline)
 * @param to    format it writes; converters with several output files write a ZIP instead
 */
public record ConverterInfo(String id, String title, Category category, Set<FileFormat> from, FileFormat to,
        String description) {

    public ConverterInfo {
        from = Set.copyOf(from);
    }
}
