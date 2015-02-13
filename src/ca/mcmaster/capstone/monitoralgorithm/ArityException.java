package ca.mcmaster.capstone.monitoralgorithm;

import java.util.Arrays;

import lombok.NonNull;

public class ArityException extends IllegalArgumentException {
    public ArityException(final int expected, @NonNull final Object[] args) {
        super(String.format("Arity mismatch: expected %d arguments, got %d: %s", expected, args.length, Arrays.toString(args)));
    }
}
