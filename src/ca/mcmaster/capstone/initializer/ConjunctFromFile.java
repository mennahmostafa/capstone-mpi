package ca.mcmaster.capstone.initializer;

import lombok.NonNull;
import lombok.Value;

@Value
public class ConjunctFromFile {
    @NonNull String ownerProcess;
    @NonNull String name;
    @NonNull String expression;
}
