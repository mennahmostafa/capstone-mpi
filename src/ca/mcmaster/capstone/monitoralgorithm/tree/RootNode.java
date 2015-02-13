package ca.mcmaster.capstone.monitoralgorithm.tree;

import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* Root node must be a comparison */
@ToString @Getter @EqualsAndHashCode(callSuper = true)
public class RootNode extends InnerNode<Boolean, Double> {
    public RootNode(final Node<Double> left, final Node<Double> right, final Operator<Double, Boolean> operator) {
        super(left, right, operator);
    }

    @Override public Boolean evaluate(@NonNull final ProcessState state) {
        return op.apply(left.evaluate(state), right.evaluate(state));
    }
}
