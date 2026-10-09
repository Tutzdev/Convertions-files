package dev.tutz.boostconvert.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tutz.boostconvert.format.FileFormat;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConverterRegistryTest {

    @Test
    void resolvesEverySourceOfAConverter() {
        Converter imageToPdf = fake("image-to-pdf", Category.IMAGE, Set.of(FileFormat.PNG, FileFormat.JPG), FileFormat.PDF);
        ConverterRegistry registry = new ConverterRegistry(List.of(imageToPdf));

        assertThat(registry.resolve(FileFormat.PNG, FileFormat.PDF)).isSameAs(imageToPdf);
        assertThat(registry.resolve(FileFormat.JPG, FileFormat.PDF)).isSameAs(imageToPdf);
    }

    @Test
    void unsupportedRouteListsTheTargetsThatDoExist() {
        ConverterRegistry registry = new ConverterRegistry(List.of(
                fake("png-to-jpg", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.JPG),
                fake("image-to-pdf", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.PDF)));

        assertThatThrownBy(() -> registry.resolve(FileFormat.PNG, FileFormat.CSV))
                .isInstanceOf(UnsupportedConversionException.class)
                .hasMessage("PNG cannot be converted to CSV. Available targets: jpg, pdf.");
        assertThatThrownBy(() -> registry.resolve(FileFormat.ZIP, FileFormat.PDF))
                .hasMessage("ZIP files cannot be converted.");
    }

    @Test
    void twoConvertersForTheSameRouteStopTheApplication() {
        List<Converter> clash = List.of(
                fake("png-to-jpg", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.JPG),
                fake("png-to-jpg-fast", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.JPG));

        assertThatThrownBy(() -> new ConverterRegistry(clash))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both handle PNG -> JPG");
    }

    @Test
    void duplicateIdsStopTheApplication() {
        List<Converter> clash = List.of(
                fake("same", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.JPG),
                fake("same", Category.DATA, Set.of(FileFormat.CSV), FileFormat.JSON));

        assertThatThrownBy(() -> new ConverterRegistry(clash)).hasMessage("Duplicate converter id: same");
    }

    @Test
    void catalogIsGroupedByCategory() {
        ConverterRegistry registry = new ConverterRegistry(List.of(
                fake("csv-to-json", Category.DATA, Set.of(FileFormat.CSV), FileFormat.JSON),
                fake("pdf-to-txt", Category.DOCUMENT, Set.of(FileFormat.PDF), FileFormat.TXT),
                fake("png-to-jpg", Category.IMAGE, Set.of(FileFormat.PNG), FileFormat.JPG)));

        assertThat(registry.catalog()).extracting(ConverterInfo::id)
                .containsExactly("png-to-jpg", "pdf-to-txt", "csv-to-json");
    }

    private static Converter fake(String id, Category category, Set<FileFormat> from, FileFormat to) {
        ConverterInfo info = new ConverterInfo(id, id, category, from, to, "");
        return new Converter() {
            @Override
            public ConverterInfo info() {
                return info;
            }

            @Override
            public FileFormat convert(SourceFile source, OutputStream target) {
                return to;
            }
        };
    }
}
