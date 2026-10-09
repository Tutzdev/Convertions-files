package dev.tutz.boostconvert.converter;

import javax.imageio.ImageIO;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.context.annotation.Configuration;

/** Process-wide settings of the conversion libraries, applied once at startup. */
@Configuration(proxyBeanMethods = false)
public class ConversionEngineConfiguration {

    private static final long MAX_ZIP_ENTRY_BYTES = 200L * 1024 * 1024;

    public ConversionEngineConfiguration() {
        // Inside an executable jar the WebP and CMYK JPEG readers are only found after a rescan.
        ImageIO.scanForPlugins();
        // ImageIO would otherwise create a temp file for every stream it reads.
        ImageIO.setUseCache(false);
        // Zip bomb guard for XLSX: POI already rejects absurd compression ratios, this caps entry size.
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
    }
}
