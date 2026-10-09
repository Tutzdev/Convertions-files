package dev.tutz.boostconvert.converter.image;

import dev.tutz.boostconvert.converter.ConversionFailedException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/** Safe image decoding and encoding shared by the converters that read or write pixels. */
public final class ImageFiles {

    private ImageFiles() {
    }

    /**
     * Decodes the first frame. The dimensions are read from the header and checked first, so a
     * decompression bomb (a tiny file declaring a huge canvas) is rejected before any pixel is allocated.
     */
    public static BufferedImage read(Path file, long maxPixels) throws IOException {
        return withReader(file, reader -> {
            checkPixels(reader, maxPixels);
            try {
                return reader.read(0);
            } catch (IOException | RuntimeException corrupt) {
                throw unreadable(corrupt);
            }
        });
    }

    /** Validates the header and the dimensions without decoding the pixels. */
    public static void checkDimensions(Path file, long maxPixels) throws IOException {
        withReader(file, reader -> {
            checkPixels(reader, maxPixels);
            return null;
        });
    }

    public static void writePng(BufferedImage image, OutputStream target) throws IOException {
        if (!ImageIO.write(image, "png", target)) {
            throw new IllegalStateException("No PNG writer is registered");
        }
    }

    public static void writeJpeg(BufferedImage image, OutputStream target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    /** JPG has no alpha channel: transparent pixels are composed over white instead of turning black. */
    public static BufferedImage onWhiteBackground(BufferedImage image) {
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = opaque.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return opaque;
    }

    private static <T> T withReader(Path file, ReaderAction<T> action) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw unreadable(null);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return action.apply(reader);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void checkPixels(ImageReader reader, long maxPixels) {
        long pixels;
        try {
            pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        } catch (IOException | RuntimeException corrupt) {
            throw unreadable(corrupt);
        }
        if (pixels > maxPixels) {
            throw new ConversionFailedException("IMAGE_TOO_LARGE",
                    "The image has %,d pixels; the limit is %,d.".formatted(pixels, maxPixels));
        }
    }

    static ConversionFailedException unreadable(Throwable cause) {
        return new ConversionFailedException("CORRUPT_FILE", "The image is damaged or uses an unsupported encoding.",
                cause);
    }

    @FunctionalInterface
    private interface ReaderAction<T> {
        T apply(ImageReader reader) throws IOException;
    }
}
