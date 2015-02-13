package ca.mcmaster.capstone.monitoralgorithm.tree;

import java.util.Objects;

import ca.mcmaster.capstone.monitoralgorithm.Conjunct;
import ca.mcmaster.capstone.monitoralgorithm.ProcessState;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode @ToString
public class BooleanExpressionTree {

    @NonNull @Getter private final RootNode root;

    //FIXME: This is garbage.
    /*
     * Very limited parsing of expression string of the form 'varibale' [==.!=] 'value'.
     */
    public BooleanExpressionTree(@NonNull RootNode root) {
        this.root = root;
    }

    public Conjunct.Evaluation evaluate(@NonNull final ProcessState state) {
        final boolean evaluation = root.evaluate(state);
        if (evaluation) {
            return Conjunct.Evaluation.TRUE;
        } else {
            return Conjunct.Evaluation.FALSE;
        }
    }
}
