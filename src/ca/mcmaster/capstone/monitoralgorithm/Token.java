package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/* Class to represent the computation slicing tokens.*/
@EqualsAndHashCode
public class Token  implements java.io.Serializable{
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static class Builder {
        private final int uniqueLocalIdentifier;
        private final int owner;
        private final int  destination;

        private int targetEventId = 0;
        private VectorClock cut;
        private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
        private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
        private ProcessState targetProcessState = null;
        private boolean returned = false;
        private boolean sent = false;

        private static class TokenIdCounter {
            private static int tokenIdCounter = 0;

            public static int getTokenId() {
                return tokenIdCounter++;
            }
        }

        public Builder(@NonNull final int owner, @NonNull final int destination) {
            this.uniqueLocalIdentifier = TokenIdCounter.getTokenId();
            this.owner = owner;
            this.destination = destination;
        }

        public Builder(@NonNull final Token token) {
            this.uniqueLocalIdentifier = token.uniqueLocalIdentifier;
            this.owner = token.owner;
            this.destination = token.destination;
            this.targetEventId = token.targetEventId;
            this.cut = new VectorClock(token.cut);
            this.automatonTransitions.addAll(token.automatonTransitions);
            this.conjuncts.putAll(token.conjuncts);
            if (token.targetProcessState == null) {
                this.targetProcessState = null;
            } else {
                this.targetProcessState = new ProcessState(token.targetProcessState);
            }
            this.returned = token.returned;
            this.sent = token.sent;
        }

        public Builder targetEventId(final int id) {
            this.targetEventId = id;
            return this;
        }

        public Builder cut(@NonNull final VectorClock cut) {
            this.cut = new VectorClock(cut);
            return this;
        }

        public Builder automatonTransitions(@NonNull final Set<AutomatonTransition> transitions) {
            this.automatonTransitions.addAll(transitions);
            return this;
        }

        public Builder conjuncts(@NonNull final Map<Conjunct, Conjunct.Evaluation> conjuncts) {
            this.conjuncts.putAll(conjuncts);
            return this;
        }

        public Builder targetProcessState(@NonNull final ProcessState state) {
            this.targetProcessState = new ProcessState(state);
            return this;
        }

        public Builder returned(final boolean returned) {
            this.returned = returned;
            return this;
        }

        public Builder sent(final boolean sent) {
            this.sent = sent;
            return this;
        }

        public Token build() {
            return new Token(this);
        }
    }

    @Getter private final int uniqueLocalIdentifier; //4
    @Getter private final int owner; //4
    @Getter @Setter private  int destination;
    @Getter private final int targetEventId;
    @NonNull @Getter private final VectorClock cut;
    private final Set<AutomatonTransition> automatonTransitions = new HashSet<>();
    private final Map<Conjunct, Conjunct.Evaluation> conjuncts = new HashMap<>();
    @NonNull @Getter private final ProcessState targetProcessState;
    @Getter private boolean returned = false;
    @Getter private boolean sent = false;

    public Token(@NonNull final Builder builder) {
        this.uniqueLocalIdentifier = builder.uniqueLocalIdentifier;
        this.owner = builder.owner;
        this.destination = builder.destination;
        this.targetEventId = builder.targetEventId;
        this.cut = builder.cut;
        this.automatonTransitions.addAll(builder.automatonTransitions);
        this.conjuncts.putAll(builder.conjuncts);
        this.targetProcessState = builder.targetProcessState;
        this.returned = builder.returned;
        this.sent = builder.sent;
    }

    public Set<Conjunct> getConjuncts() {
        return new HashSet<>(conjuncts.keySet());
    }

    public Map<Conjunct, Conjunct.Evaluation> getConjunctsMap() {
        return new HashMap<>(conjuncts);
    }

    public Set<AutomatonTransition> getAutomatonTransitions() {
        return new HashSet<>(automatonTransitions);
    }

    /*
     * Returns a token with its target event increased by 1.
     *
     * @return A new token with its target event increased by 1.
     */
    public Token waitForNextEvent() {
        return new Builder(this).targetEventId(this.targetEventId + 1).build();
    }

    /*
     * Uses the state information in event to evaluate this token's conjuncts.
     *
     * @param event The event to use to evaluate the transitions.
     */
    public void evaluateConjuncts(@NonNull final Event event) {
        for (final Conjunct conjunct : conjuncts.keySet()) {
            conjuncts.put(conjunct, conjunct.evaluate(event.getState()));
        }
    }

    /*
     * Checks if any of this token's conjuncts are satisfied.
     *
     * @return True if at least one conjunct is satisfied, false otherwise.
     */
    public boolean anyConjunctSatisfied() {
        return conjuncts.containsValue(Conjunct.Evaluation.TRUE);
    }

    @Override
    public String toString() {
        return "Token{" +
                "uniqueLocalIdentifier=" + uniqueLocalIdentifier +
                ", owner=" + this.owner +
                ", destination=" + destination+
                ", targetEventId=" + targetEventId +
                ", cut=" + cut +
                ", automatonTransitions=" + automatonTransitions +
                ", conjuncts=" + conjuncts +
                ", targetProcessState=" + targetProcessState +
                ", returned=" + returned +
                ", sent=" + sent +
                '}';
    }
}
