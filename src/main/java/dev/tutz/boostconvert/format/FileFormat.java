package dev.tutz.boostconvert.format;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Formats the service can read or write. Text formats are recognised by name, binary ones by content. */
public enum FileFormat {

    PNG("png", "image/png", false, "png"),
    JPG("jpg", "image/jpeg", false, "jpg", "jpeg"),
    WEBP("webp", "image/webp", false, "webp"),
    PDF("pdf", "application/pdf", false, "pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, "xlsx"),
    ZIP("zip", "application/zip", false, "zip"),
    CSV("csv", "text/csv", true, "csv"),
    JSON("json", "application/json", true, "json"),
    MARKDOWN("md", "text/markdown", true, "md", "markdown"),
    HTML("html", "text/html", true, "html", "htm"),
    TXT("txt", "text/plain", true, "txt");

    private final String extension;
    private final String mediaType;
    private final boolean text;
    private final List<String> aliases;

    FileFormat(String extension, String mediaType, boolean text, String... aliases) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.text = text;
        this.aliases = List.of(aliases);
    }

    /** Canonical extension, without the dot. Also the format's id in the API. */
    public String extension() {
        return extension;
    }

    /** Media type for downloads. Text formats are always written as UTF-8. */
    public String mediaType() {
        return text ? mediaType + ";charset=UTF-8" : mediaType;
    }

    public boolean isText() {
        return text;
    }

    public List<String> aliases() {
        return aliases;
    }

    public static Optional<FileFormat> fromExtension(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        String normalized = extension.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> format.aliases.contains(normalized))
                .findFirst();
    }
}
