package dev.tutz.boostconvert.format;

import java.nio.charset.Charset;

/** What an upload really is. {@code charset} only matters for text formats. */
public record DetectedFormat(FileFormat format, Charset charset) {
}
