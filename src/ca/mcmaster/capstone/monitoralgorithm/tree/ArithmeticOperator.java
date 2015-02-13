package ca.mcmaster.capstone.monitoralgorithm.tree;

import java.util.Arrays;

import ca.mcmaster.capstone.monitoralgorithm.ArityException;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.BinaryOperator;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public enum ArithmeticOperator implements Operator<Double, Double>, BinaryOperator {
    ADD(x -> x[0] + x[1]),
    SUBTRACT(x -> x[0] - x[1]),
    DIVIDE(x -> x[0] / x[1]),
    MULTIPLY(x -> x[0] * x[1]),
    EXPONENT(x -> Math.pow(x[0], x[1]));

    @NonNull private final Operator<Double, Double> operator;

    @Override
    public Double apply(final Double ... args) {
        if (args.length != ARITY) {
            throw new ArityException(ARITY, args);
        }
        if (Arrays.asList(args).contains(null)) {
            throw new NullPointerException("Tried to apply " + this + " to arguments including null: " + Arrays.toString(args));
        }
        return this.operator.apply(args);
    }
}
