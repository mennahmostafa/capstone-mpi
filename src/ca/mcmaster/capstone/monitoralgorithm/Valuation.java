package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class Valuation implements java.io.Serializable {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final Map<String, Double> valuation = new HashMap<>();

    public Valuation(@NonNull final Map<String, Double> valuation) {
        this.valuation.putAll(valuation);
    }

    public Valuation(@NonNull final Valuation valuation) {
        for (Map.Entry<String, Double> entry : valuation.valuation.entrySet()) {
            this.valuation.put(entry.getKey(), new Double(entry.getValue()));
        }
    }

    public Double add(@NonNull final String variableName, @NonNull final Double value) {
        return this.valuation.put(variableName, value);
    }

    public Double getValue(@NonNull final String name){
        return valuation.get(name);
    }
}
