package dev.tutz.boostconvert.converter.document;

import static dev.tutz.boostconvert.TestFiles.run;
import static dev.tutz.boostconvert.TestFiles.source;
import static dev.tutz.boostconvert.TestFiles.write;
import static org.assertj.core.api.Assertions.assertThat;

import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownToHtmlConverterTest {

    private final MarkdownToHtmlConverter converter = new MarkdownToHtmlConverter();

    @TempDir
    Path dir;

    @Test
    void rendersAStandalonePageTitledByTheFirstHeading() throws IOException {
        String markdown = """
                # Release <notes>

                | Format | Status |
                | ------ | ------ |
                | PNG    | ~~beta~~ ready |

                See https://example.com
                """;

        String html = convert(markdown);

        assertThat(html).startsWith("<!doctype html>");
        assertThat(html).contains("<title>Release &lt;notes&gt;</title>");
        assertThat(html).contains("<table>").contains("<del>beta</del>");
        assertThat(html).contains("href=\"https://example.com\"");
    }

    @Test
    void rawHtmlAndJavascriptLinksCannotReachTheOutput() throws IOException {
        String markdown = """
                <script>alert('x')</script>

                [click](javascript:alert(1))
                """;

        String html = convert(markdown);

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(html).doesNotContain("javascript:");
    }

    private String convert(String markdown) throws IOException {
        Path file = write(dir, "in.md", markdown);
        return run(converter, source(file, FileFormat.MARKDOWN)).text();
    }
}
