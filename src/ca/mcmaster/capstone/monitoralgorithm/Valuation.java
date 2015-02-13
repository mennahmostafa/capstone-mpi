package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class Valuation {
    private final Map<String, Double> valuation = new HashMap<>();

    public Valuation(@NonNull final Map<String, Double> valuation) {
        this.valuation.putAll(valuation);
    }

    public Valuation(@NonNull final Valuation valuation) {
        this.valuation.putAll(valuation.valuation);
    }

    public Double add(@NonNull final String variableName, @NonNull final Double value) {
        return this.valuation.put(variableName, value);
    }

    public Double getValue(@NonNull final String name){
        return valuation.get(name);
    }
}
