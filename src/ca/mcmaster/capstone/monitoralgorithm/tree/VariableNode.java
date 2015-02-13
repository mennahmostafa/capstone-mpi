package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* A leaf node */
@ToString @Getter @EqualsAndHashCode(callSuper = true)
public class VariableNode extends LeafNode<String, Double> {
    public VariableNode(final String variableName) {
        super(variableName);
    }

    @Override public Double evaluate(@NonNull final ProcessState state) {
        return state.getVal().getValue(value);
    }
}
