package dev.tutz.boostconvert.converter.data;

import dev.tutz.boostconvert.converter.ConversionFailedException;

/** Counts the cells a conversion touches and stops it as soon as the limit is passed. Not thread-safe. */
final class CellBudget {

    private final long limit;
    private long used;

    CellBudget(long limit) {
        this.limit = limit;
    }

    void spend(long cells) {
        used += cells;
        if (used > limit) {
            throw new ConversionFailedException("TOO_MANY_CELLS",
                    "The file has more than %,d cells, the limit for one conversion.".formatted(limit));
        }
    }
}
