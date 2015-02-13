package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* A leaf node */
@ToString @Getter @EqualsAndHashCode(callSuper = true)
public class ValueNode extends LeafNode<Double, Double> {
    public ValueNode(final Double value) {
        super(value);
    }
    @Override public Double evaluate(@NonNull final ProcessState state) {
        return value;
    }
}
