package dev.tutz.boostconvert.converter.document;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

/**
 * GitHub-flavoured Markdown to a standalone HTML page. Raw HTML in the input is escaped and
 * {@code javascript:} links are dropped, so a converted file can never carry a script.
 */
@Component
public class MarkdownToHtmlConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("md-to-html", "Markdown to HTML", Category.DOCUMENT,
            Set.of(FileFormat.MARKDOWN), FileFormat.HTML,
            "A standalone, styled HTML page. Supports GitHub tables, strikethrough and autolinks.");

    private static final String PAGE = """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
              body { margin: 0; background: #fcf8f3; color: #1c1917; font: 16px/1.65 system-ui, sans-serif; }
              main { max-width: 760px; margin: 0 auto; padding: 48px 24px; }
              h1, h2, h3 { line-height: 1.25; }
              a { color: #bd5a00; }
              code { background: #f3e8d8; padding: .1em .35em; border-radius: 4px; font-size: .9em; }
              pre { background: #1c1917; color: #fcf8f3; padding: 16px; border-radius: 8px; overflow-x: auto; }
              pre code { background: none; padding: 0; }
              table { border-collapse: collapse; width: 100%%; }
              th, td { border: 1px solid #eadfd3; padding: 6px 10px; text-align: left; }
              blockquote { margin: 0; padding-left: 16px; border-left: 3px solid #f08a24; color: #57534e; }
              img { max-width: 100%%; }
            </style>
            </head>
            <body>
            <main>
            %s</main>
            </body>
            </html>
            """;

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final TextContentRenderer plainText;

    public MarkdownToHtmlConverter() {
        List<Extension> extensions = List.of(TablesExtension.create(), StrikethroughExtension.create(),
                AutolinkExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();
        this.plainText = TextContentRenderer.builder().build();
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        Node document;
        try (Reader reader = source.openReader()) {
            document = parser.parseReader(reader);
        }
        Writer writer = new OutputStreamWriter(target, StandardCharsets.UTF_8);
        writer.write(PAGE.formatted(escape(title(document)), renderer.render(document)));
        writer.flush();
        return FileFormat.HTML;
    }

    /** The first heading, which is what a reader would call the document. */
    private String title(Node document) {
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                String text = plainText.render(heading).strip();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "Document";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
