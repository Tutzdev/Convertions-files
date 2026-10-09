package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.format.FileFormat;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/converters")
public class ConverterController {

    private final ConverterRegistry registry;

    public ConverterController(ConverterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    List<ConverterResponse> catalog() {
        return registry.catalog().stream().map(ConverterResponse::from).toList();
    }

    /**
     * @param from    source formats, by canonical extension
     * @param accepts every extension the source formats are recognised by, ready for an HTML {@code accept}
     */
    record ConverterResponse(String id, String title, Category category, List<String> from, String to,
            List<String> accepts, String description) {

        static ConverterResponse from(ConverterInfo info) {
            List<FileFormat> sources = info.from().stream().sorted(Comparator.naturalOrder()).toList();
            return new ConverterResponse(info.id(), info.title(), info.category(),
                    sources.stream().map(FileFormat::extension).toList(),
                    info.to().extension(),
                    sources.stream().flatMap(format -> format.aliases().stream()).map(alias -> "." + alias).toList(),
                    info.description());
        }
    }
}
