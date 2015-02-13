package ca.mcmaster.capstone.monitoralgorithm.interfaces;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;

public interface Node<T> {
    T evaluate(ProcessState state);
}
