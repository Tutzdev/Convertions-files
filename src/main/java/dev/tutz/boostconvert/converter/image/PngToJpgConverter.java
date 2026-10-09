package dev.tutz.boostconvert.converter.image;

import dev.tutz.boostconvert.converter.Category;
import dev.tutz.boostconvert.converter.ConversionLimits;
import dev.tutz.boostconvert.converter.Converter;
import dev.tutz.boostconvert.converter.ConverterInfo;
import dev.tutz.boostconvert.converter.SourceFile;
import dev.tutz.boostconvert.format.FileFormat;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PngToJpgConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("png-to-jpg", "PNG to JPG", Category.IMAGE,
            Set.of(FileFormat.PNG), FileFormat.JPG,
            "Smaller files for photos. Transparent areas turn white, since JPG has no transparency.");

    private final ConversionLimits limits;

    public PngToJpgConverter(ConversionLimits limits) {
        this.limits = limits;
    }

    @Override
    public ConverterInfo info() {
        return INFO;
    }

    @Override
    public FileFormat convert(SourceFile source, OutputStream target) throws IOException {
        BufferedImage image = ImageFiles.read(source.path(), limits.maxImagePixels());
        ImageFiles.writeJpeg(ImageFiles.onWhiteBackground(image), target, limits.jpegQuality());
        return FileFormat.JPG;
    }
}
