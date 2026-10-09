package dev.tutz.boostconvert.converter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds the work a single upload can cause. The upload size alone is not enough: a 2 MB PNG can
 * decode to gigabytes of pixels, and a small XLSX can unzip to millions of cells.
 *
 * @param maxImagePixels   width x height allowed for any image that gets decoded
 * @param maxPdfPages      pages accepted in a PDF
 * @param maxRenderedPages pages rendered by PDF to PNG, which is the expensive conversion
 * @param renderDpi        resolution of rendered PDF pages
 * @param maxCells         cells (rows x columns) in CSV, JSON and XLSX conversions
 * @param jpegQuality      0.0 to 1.0
 */
@ConfigurationProperties("boostconvert.limits")
public record ConversionLimits(long maxImagePixels, int maxPdfPages, int maxRenderedPages, int renderDpi,
        long maxCells, float jpegQuality) {
}
