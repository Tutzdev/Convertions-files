package dev.tutz.boostconvert.converter;

import dev.tutz.boostconvert.format.FileFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Index of every {@link Converter} bean by id and by (source format, target format). Two converters
 * claiming the same id or the same route stop the application at startup instead of one silently
 * shadowing the other.
 */
@Component
public class ConverterRegistry {

    private static final Comparator<Converter> CATALOG_ORDER = Comparator
            .comparing((Converter converter) -> converter.info().category())
            .thenComparing(converter -> converter.info().title());

    private final Map<String, Converter> byId = new LinkedHashMap<>();
    private final Map<Route, Converter> byRoute = new HashMap<>();

    public ConverterRegistry(List<Converter> converters) {
        converters.stream().sorted(CATALOG_ORDER).forEach(this::register);
    }

    public List<ConverterInfo> catalog() {
        return byId.values().stream().map(Converter::info).toList();
    }

    public Optional<Converter> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Converter resolve(FileFormat from, FileFormat to) {
        Converter converter = byRoute.get(new Route(from, to));
        if (converter != null) {
            return converter;
        }
        List<String> targets = byRoute.keySet().stream()
                .filter(route -> route.from() == from)
                .map(route -> route.to().extension())
                .sorted()
                .toList();
        if (targets.isEmpty()) {
            throw new UnsupportedConversionException(
                    "%s files cannot be converted.".formatted(label(from)));
        }
        throw new UnsupportedConversionException("%s cannot be converted to %s. Available targets: %s.".formatted(
                label(from), label(to), String.join(", ", targets)));
    }

    private void register(Converter converter) {
        ConverterInfo info = converter.info();
        if (byId.putIfAbsent(info.id(), converter) != null) {
            throw new IllegalStateException("Duplicate converter id: " + info.id());
        }
        for (FileFormat from : info.from()) {
            Converter previous = byRoute.putIfAbsent(new Route(from, info.to()), converter);
            if (previous != null) {
                throw new IllegalStateException("Converters %s and %s both handle %s -> %s"
                        .formatted(previous.info().id(), info.id(), from, info.to()));
            }
        }
    }

    private static String label(FileFormat format) {
        return format.extension().toUpperCase(Locale.ROOT);
    }

    private record Route(FileFormat from, FileFormat to) {
    }
}
