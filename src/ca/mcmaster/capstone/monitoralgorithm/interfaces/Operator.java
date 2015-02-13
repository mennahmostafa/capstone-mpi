package ca.mcmaster.capstone.monitoralgorithm.interfaces;

public interface Operator<T, R> {
    R apply(T... args);
}
