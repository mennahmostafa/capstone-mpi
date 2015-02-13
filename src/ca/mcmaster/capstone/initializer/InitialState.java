package ca.mcmaster.capstone.initializer;

import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;
import lombok.Value;

@Value
public class InitialState {
    List<ValuationDummy> valuations = new ArrayList<>();

    @Value
    public static class ValuationDummy {
        List<Variable> variables = new ArrayList();
    }

    @Value
    public static class Variable {
        @NonNull String variable;
        @NonNull String value;
    }
}
