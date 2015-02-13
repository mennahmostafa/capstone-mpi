package ca.mcmaster.capstone.monitoralgorithm.tree;

import java.util.Arrays;

import ca.mcmaster.capstone.monitoralgorithm.ArityException;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.BinaryOperator;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public enum ComparisonOperator implements Operator<Double, Boolean>, BinaryOperator {
    LESS_THAN(x -> x[0] < x[1]),
    GREATER_THAN(x -> x[0] > x[1]),
    EQUAL(x -> x[0].equals(x[1])),
    NOT_EQUAL(x -> !EQUAL.apply(x)),
    LESS_OR_EQUAL(x -> LESS_THAN.apply(x) || EQUAL.apply(x)),
    GREATER_OR_EQUAL(x -> GREATER_THAN.apply(x) || EQUAL.apply(x));

    @NonNull private final Operator<Double, Boolean> operator;

    @Override
    public Boolean apply(@NonNull final Double ... args) {
        if (args.length != ARITY) {
            throw new ArityException(ARITY, args);
        }
        if (Arrays.asList(args).contains(null)) {
            throw new NullPointerException("Tried to apply " + this + " to arguments including null: " + Arrays.toString(args));
        }
        return this.operator.apply(args);
    }
}
