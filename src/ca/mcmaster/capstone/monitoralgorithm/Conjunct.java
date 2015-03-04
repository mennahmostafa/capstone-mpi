package ca.mcmaster.capstone.monitoralgorithm;

import java.io.Serializable;

import ca.mcmaster.capstone.monitoralgorithm.tree.BooleanExpressionTree;
import ca.mcmaster.capstone.monitoralgorithm.tree.parser.BooleanExpressionParser;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode
public class Conjunct implements Serializable {
    public static enum Evaluation {TRUE, FALSE, NONE}

    @NonNull @Getter private final Integer ownerProcess;
    @NonNull private final BooleanExpressionTree expression;
    @NonNull private final String expressionStr;
	@NonNull @Getter private final Integer transitionId;

    public Conjunct(@NonNull final Integer transitionId,@NonNull final Integer ownerProcess, @NonNull final String expression) {
        this.ownerProcess = ownerProcess;
        this.expressionStr = expression;
        this.transitionId=transitionId;
        this.expression = BooleanExpressionParser.INSTANCE.parse(expressionStr);
    }

    public Evaluation evaluate(@NonNull final ProcessState state) {
        if (!ownerProcess.equals(state.getId())) {
            return Evaluation.NONE;
        }
        return expression.evaluate(state);
    }

    @Override
    public String toString() {
        return "Conjunct{" +
                "ownerProcess=" + ownerProcess +
                ", expressionStr='" + expressionStr + '\'' +
                '}';
    }
}
