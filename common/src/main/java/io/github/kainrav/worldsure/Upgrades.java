package io.github.kainrav.worldsure;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The steps that bring a file written in an older format up to the current one. Step {@code i}
 * turns format {@code i + 1} into format {@code i + 2}, so a file of any older format is upgraded
 * by running the steps after it in order. A format change appends one step and never edits an old one.
 */
public final class Upgrades<T> {
    private final int current;
    private final List<UnaryOperator<T>> steps;

    /**
     * @param current the format this version writes
     * @param steps   one step per older format, oldest first
     */
    public Upgrades(int current, List<UnaryOperator<T>> steps) {
        if (current < 1 || steps.size() != current - 1) {
            throw new IllegalArgumentException("Format " + current + " needs " + (current - 1)
                    + " upgrade steps, got " + steps.size());
        }
        this.current = current;
        this.steps = List.copyOf(steps);
    }

    public int current() {
        return current;
    }

    /**
     * Upgrades data of format {@code from} to the current format.
     *
     * @throws IllegalArgumentException if {@code from} is not a format older than or equal to the current one
     */
    public T apply(T data, int from) {
        if (from < 1 || from > current) {
            throw new IllegalArgumentException("Cannot upgrade format " + from + " to " + current);
        }
        T upgraded = data;
        for (int format = from; format < current; format++) {
            upgraded = steps.get(format - 1).apply(upgraded);
        }
        return upgraded;
    }
}
