package dev.tutz.boostconvert.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileNamesTest {

    @Test
    void baseNameDropsPathAndExtension() {
        assertThat(FileNames.baseName("C:\\Users\\ana\\Q3 report.final.pdf")).isEqualTo("Q3 report.final");
        assertThat(FileNames.baseName("/tmp/photo.png")).isEqualTo("photo");
        assertThat(FileNames.baseName("Relatório Março.xlsx")).isEqualTo("Relatório Março");
    }

    @Test
    void baseNameRemovesCharactersThatBreakFileSystemsOrHeaders() {
        assertThat(FileNames.baseName("a<b>c:d\"e|f?g*h\r\n.txt")).isEqualTo("abcdefgh");
        assertThat(FileNames.baseName("..hidden.csv")).isEqualTo("hidden");
        assertThat(FileNames.baseName("???.png")).isEqualTo("converted");
        assertThat(FileNames.baseName(null)).isEqualTo("converted");
        assertThat(FileNames.baseName("x".repeat(300) + ".png")).hasSize(100);
    }

    @Test
    void extensionIsLowerCaseAndOptional() {
        assertThat(FileNames.extension("PHOTO.JPEG")).contains("jpeg");
        assertThat(FileNames.extension("archive.tar.gz")).contains("gz");
        assertThat(FileNames.extension("README")).isEmpty();
        assertThat(FileNames.extension("trailing.")).isEmpty();
        assertThat(FileNames.extension("folder.v2/README")).isEmpty();
    }
}
