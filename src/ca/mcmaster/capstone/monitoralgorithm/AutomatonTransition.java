package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent an automaton transition.*/
@EqualsAndHashCode @ToString
public class AutomatonTransition {
    @NonNull @Getter private AutomatonState from;
    @NonNull @Getter private AutomatonState to;
    private List<Conjunct> conjuncts = new ArrayList<>();

    public AutomatonTransition(@NonNull final AutomatonState from, @NonNull final AutomatonState to, @NonNull final List<Conjunct> conjuncts) {
        this.from = from;
        this.to = to;
        this.conjuncts.addAll(conjuncts);
    }

    public List<Conjunct> getConjuncts() {
        return new ArrayList<>(conjuncts);
    }

    /*
     * Computes the evaluation of the transition based on the evaluation of each conjunct.
     *
     * @return The evaluation of the transition based on its conjuncts.
     */
    public Conjunct.Evaluation evaluate(@NonNull final Collection<ProcessState> processStates) {
        final Map<Conjunct, Conjunct.Evaluation> evaluations = new HashMap<>();
        for (final ProcessState state : processStates) {
            for (final Conjunct conjunct : this.conjuncts) {
                if (conjunct.getOwnerProcess().equals(state.getId())) {
                    evaluations.put(conjunct, conjunct.evaluate(state));
                }
            }
        }
        if (evaluations.values().contains(Conjunct.Evaluation.FALSE)) {
            return Conjunct.Evaluation.FALSE;
        } else if (evaluations.values().contains(Conjunct.Evaluation.NONE)) {
            return Conjunct.Evaluation.NONE;
        }
        return Conjunct.Evaluation.TRUE;
    }

    /*
     * Returns a set of process ids for the processes that contribute variables to the predicate
     * labeling this transition.
     *
     * @return A set of process ids.
     */
    public Set<Integer> getParticipatingProcesses() {
        final Set<Integer> ret = new HashSet<>();
        for (final Conjunct conjunct : conjuncts) {
            ret.add(conjunct.getOwnerProcess());
        }
        return ret;
    }

    /*
     * Returns a set of conjuncts that cause this transition to evaluate to false.
     *
     * @return A set of Conjuncts.
     */
    public Set<Conjunct> getForbiddingConjuncts(@NonNull final GlobalView gv) {
        final Set<Conjunct> ret = new HashSet<>();
        for (final Map.Entry<Integer, ProcessState> entry : gv.getStates().entrySet()) {
            final ProcessState state = entry.getValue();
            for (final Conjunct conjunct : conjuncts) {
                if (conjunct.getOwnerProcess().equals(state.getId())
                        && conjunct.evaluate(state) == Conjunct.Evaluation.FALSE) {
                    ret.add(conjunct);
                    break;
                }
            }
        }
        return ret;
    }

    public boolean enabled(@NonNull GlobalView globalView, @NonNull final List<Token> tokens) {
        Map<Integer, ProcessState> states = new HashMap<>(globalView.getStates());
        for (Token token : tokens) {
            if (!token.isReturned()) {
                return false;
            }
            final ProcessState targetProcessState = token.getTargetProcessState();
            states.put(targetProcessState.getId(), targetProcessState);
        }

        return this.evaluate(states.values()) == Conjunct.Evaluation.TRUE;
    }
}