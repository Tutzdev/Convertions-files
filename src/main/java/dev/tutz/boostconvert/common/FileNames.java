package dev.tutz.boostconvert.common;

import java.util.Locale;
import java.util.Optional;

/** Client file names are untrusted input: they are only used for display and for the download name. */
public final class FileNames {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DISPLAY_LENGTH = 160;
    private static final String FALLBACK = "converted";

    private FileNames() {
    }

    /** {@code "C:\docs\Q3 report.final.pdf"} becomes {@code "Q3 report.final"}. */
    public static String baseName(String fileName) {
        String name = lastSegment(fileName);
        int dot = name.lastIndexOf('.');
        return sanitize(dot > 0 ? name.substring(0, dot) : name);
    }

    /** Keeps a name safe to use as a file name on any operating system. */
    public static String sanitize(String name) {
        String safe = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "")
                .replaceAll("\\s+", " ")
                .strip()
                // A leading dot would turn the download into a hidden file.
                .replaceFirst("^\\.+", "");
        if (safe.isEmpty()) {
            return FALLBACK;
        }
        return safe.length() > MAX_NAME_LENGTH ? safe.substring(0, MAX_NAME_LENGTH).strip() : safe;
    }

    /** The extension in lower case, without the dot. */
    public static Optional<String> extension(String fileName) {
        String name = lastSegment(fileName);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** The name shown back to the user: no path, no control characters, bounded length. */
    public static String displayName(String fileName) {
        String name = lastSegment(fileName).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "upload";
        }
        return name.length() > MAX_DISPLAY_LENGTH ? name.substring(0, MAX_DISPLAY_LENGTH) : name;
    }

    private static String lastSegment(String fileName) {
        if (fileName == null) {
            return "";
        }
        // Old browsers on Windows sent the full client path.
        int separator = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(separator + 1);
    }
}
