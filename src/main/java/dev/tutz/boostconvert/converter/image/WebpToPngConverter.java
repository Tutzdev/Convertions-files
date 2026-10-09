package dev.tutz.boostconvert.converter.image;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WebpToPngConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("webp-to-png", "WEBP to PNG", Category.IMAGE,
            Set.of(FileFormat.WEBP), FileFormat.PNG,
            "Opens images saved from the web in any editor. Keeps transparency; animations keep the first frame.");

    private final ConversionLimits limits;

    public WebpToPngConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        ImageFiles.writePng(ImageFiles.read(source.path(), limits.maxImagePixels()), target);
        return FileFormat.PNG;
    }
}
