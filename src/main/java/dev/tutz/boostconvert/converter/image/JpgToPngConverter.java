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
public class JpgToPngConverter implements Converter {

    private static final ConverterInfo INFO = new ConverterInfo("jpg-to-png", "JPG to PNG", Category.IMAGE,
            Set.of(FileFormat.JPG), FileFormat.PNG,
            "Lossless copy for editing. CMYK JPGs from print workflows come out as RGB.");

    private final ConversionLimits limits;

    public JpgToPngConverter(ConversionLimits limits) {
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
