package ca.mcmaster.capstone.monitoralgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent the local view of the global state.*/
@ToString
public class GlobalView {
    // FIXME: Storing these like this is needlessly error prone. Should probably be a set.
    private final Map<Integer, ProcessState> states = new HashMap<>();
    @NonNull private VectorClock cut;
    @NonNull private AutomatonState currentState;
    //TODO: Maybe tokens, and pendingTransitions could be refactored into a Map<AutomatonTransitoin, Set<Token>>
    private final Set<Token> tokens = new HashSet<>();
    private final Queue<Event> pendingEvents = new ArrayDeque<>();
    private final Set<AutomatonTransition> pendingTransitions = new HashSet<>();

    public GlobalView() {}

    public GlobalView(@NonNull final GlobalView gv) {
        this.states.putAll(gv.states);
        for (Map.Entry<Integer, ProcessState> entry : gv.states.entrySet()) {
            this.states.put(entry.getKey(), new ProcessState(entry.getValue()));
        }
        this.cut = new VectorClock(gv.cut);
        this.currentState = new AutomatonState(gv.currentState);
        this.pendingEvents.addAll(gv.pendingEvents);
        this.pendingTransitions.addAll(gv.pendingTransitions);
    }

    public Map<Integer, ProcessState> getStates() {
        return new HashMap<>(states);
    }

    public void setStates(@NonNull final Map<Integer, ProcessState> states) {
        this.states.clear();
        this.states.putAll(states);
    }

    public VectorClock getCut() {
        return cut;
    }

    public void setCut(@NonNull final VectorClock cut) {
        this.cut = cut;
    }

    public AutomatonState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(@NonNull final AutomatonState state) {
        this.currentState = state;
    }

    public Set<Token> getTokens() {
        return new HashSet<>(tokens);
    }

    public void setTokens(@NonNull final List<Token> tokens) {
        this.tokens.clear();
        this.tokens.addAll(tokens);
    }

    public void addTokens(@NonNull final List<Token> tokens) {
        for (Token token : tokens) {
            this.tokens.add(new Token.Builder(token).sent(true).build());
        }
    }

    public Queue<Event> getPendingEvents() {
        return pendingEvents;
    }

    public List<AutomatonTransition> getPendingTransitions() {
        return new ArrayList<>(pendingTransitions);
    }

    public void addPendingTransition(AutomatonTransition trans) {
        pendingTransitions.add(trans);
    }

    public void removePendingTransition(AutomatonTransition trans) {
        pendingTransitions.remove(trans);
    }

    /*
     * Finds token in the list of sent tokens and updates it with the
     * information collected at the target process.
     *
     * @param token The token to use to update the global view.
     */
    public void updateWithToken(@NonNull final Token token) {
        Token updatedToken = null;
        for (Iterator<Token> it = this.tokens.iterator(); it.hasNext();) {
            Token tokenInGV = it.next();
            if (tokenInGV.getUniqueLocalIdentifier() == token.getUniqueLocalIdentifier()) {
                updatedToken = new Token.Builder(tokenInGV).cut(token.getCut())
                        .returned(true).targetProcessState(token.getTargetProcessState()).build();
                it.remove();
                break;
            }
        }
        this.tokens.add(updatedToken);
    }

    /*
     * Updates the global view with the information in event.
     *
     * @param event The event to use to update the global view.
     */
    public void updateWithEvent(@NonNull final Event event) {
        this.cut = cut.merge(event.getVC());
        final ProcessState state = this.states.get(event.getPid()).update(event);
        this.states.put(event.getPid(), state);
    }

    /*
     * Finds the token that has the most conjuncts requested to be evaluated.
     *
     * @return A reference to The token with the most conjuncts requested to be evaluated, or null if
     *         there are no tokens in this global view.
     */
    public Token getTokenWithMostConjuncts() {
        Token ret = null;
        for (final Token token : this.tokens) {
            if (ret == null || token.getConjuncts().size() > ret.getConjuncts().size()) {
                ret = token;
            }
        }
        return ret;
    }

    /*
     * Gets all tokens in the global view that are associated with a particular transition.
     *
     * @param transition The transition to match tokens against.
     * @return A list of tokens which are associated with transition.
     */
    public List<Token> getTokensForTransition(@NonNull final AutomatonTransition transition) {
        final List<Token> ret = new ArrayList<>();
        final List<Conjunct> transConjuncts = transition.getConjuncts();
        for (final Token token : this.tokens) {
            for (final Conjunct conjunct : token.getConjuncts()) {
                if (transConjuncts.contains(conjunct)) {
                    ret.add(token);
                    break;
                }
            }
        }
        return ret;
    }

    /*
     * Returns a set of process ids for processes that are inconsistent with the local vector clock.
     * A process is inconsistent if it's vector clock is not equal to or concurrent with this process'.
     *
     * @return A set of process ids, for inconsistent processes.
     */
    public Set<Integer> getInconsistentProcesses(Integer localProcessID) {
        final Set<Integer> ret = new HashSet<>();
        final ProcessState localState = this.states.get(localProcessID);
        for (final Map.Entry<Integer, ProcessState> entry : this.states.entrySet()) {
            final ProcessState state = entry.getValue();
            final VectorClock.Comparison comp = localState.getVC().compareToClock(state.getVC());
            if (comp != VectorClock.Comparison.EQUAL && comp != VectorClock.Comparison.CONCURRENT) {
                ret.add(state.getId());
            }
        }
        return ret;
    }

    /*
     * This method combines the state of all tokens passed in and updates the global view. The tokens
     * must all be returned before this method will update the state in this global view.
     *
     * @throws IllegalArgumentException
     */
    public void reduceStateFromTokens(final List<Token> tokens) throws IllegalArgumentException {
        VectorClock updatedCut = new VectorClock(this.cut);
        final Map<Integer, ProcessState> updatedStates = new HashMap<>(this.states);
        for (Token token : tokens) {
            if (!token.isReturned()) {
                throw new IllegalArgumentException("All tokens must be returned before the state can be reduced from them.");
            }
            updatedCut = updatedCut.merge(token.getCut());
            @NonNull final ProcessState targetState = token.getTargetProcessState();
            updatedStates.put(targetState.getId(), targetState);
        }
        this.cut = updatedCut;
        this.states.putAll(updatedStates);
    }

    /*
     * Checks if the VectorClock of each ProcessState in gv is consistent with that of all other processes
     * which are taking part in the transitoin. If there is a more up to date VectorClock for that
     * process in on of the returned tokens, use that for the comparisson.
     *
     * @param gv     The GlobalView to check for consistency.
     * @param trans  The AutomatonTransition which the considered processes must take part in.
     * @return   true if all vector clock comparisons return EQUAL or CONCURRENT
     */
    public boolean consistent(@NonNull final AutomatonTransition trans) {
        Set<Integer> participatingProcesses = trans.getParticipatingProcesses();
        Set<ProcessState> statesToCheck = new HashSet<>();

        // Filter the states for the ones needed for this transition and use the state from any tokens
        // that have returned from the processes in question instead of the old state.
        for (ProcessState state : this.states.values()) {
            if (participatingProcesses.contains(state.getId())) {
                boolean useTokenState = false;
                for (Token token : this.tokens) {
                    if (token.isReturned() && new Integer(token.getDestination()).equals(state.getId())) {
                        useTokenState = true;
                        statesToCheck.add(token.getTargetProcessState());
                    }
                }
                if (!useTokenState) {
                    statesToCheck.add(state);
                }
            }
        }

        // Compare the vector clock of each state
        for (ProcessState state1 : statesToCheck) {
            for (ProcessState state2 : statesToCheck) {
                if (!state1.equals(state2)) {
                    VectorClock.Comparison comp = state1.getVC().compareToClock(state2.getVC());
                    if (comp != VectorClock.Comparison.CONCURRENT
                            && comp != VectorClock.Comparison.EQUAL) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /*
     * Attempts to merge this GlobalView with gv by treating the members of each as sets,
     * and taking the union.
     *
     * @param gv The GlobalView to merge with this one.
     * @return Returns a new GlobalView if this can be merged with gv, null otherwise.
     */
    public GlobalView merge(@NonNull final GlobalView gv) {
        final VectorClock.Comparison compare = this.cut.compareToClock(gv.cut);
        if (this.currentState.equals(gv.currentState) &&
                (compare == VectorClock.Comparison.CONCURRENT || compare == VectorClock.Comparison.EQUAL)) {
            final GlobalView ret = new GlobalView();
            //XXX: I'm not sure that these are guaranteed to be the same. We may be losing information here.
            //     For example, what happens if the states in gv differ from those in this object?
            ret.states.putAll(this.states);
            ret.states.putAll(gv.states);
            ret.cut = this.cut.merge(gv.cut);
            ret.currentState = this.currentState;
            ret.tokens.addAll(unionMerge(this.tokens, gv.tokens));
            ret.pendingEvents.addAll(unionMerge(this.pendingEvents, gv.pendingEvents));
            ret.pendingTransitions.addAll(unionMerge(this.pendingTransitions, gv.pendingTransitions));
            return ret;
        }
        return null;
    }

    private static <T> Set<T> unionMerge(@NonNull final Collection<T> first, @NonNull final Collection<T> second) {
        final Set<T> firstSet = new HashSet<>(first);
        final Set<T> secondSet = new HashSet<>(second);
        firstSet.addAll(secondSet);
        return firstSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GlobalView that = (GlobalView) o;

        if (!currentState.equals(that.currentState)) return false;
        if (!cut.equals(that.cut)) return false;
        if (!pendingEvents.equals(that.pendingEvents)) return false;
        if (!pendingTransitions.equals(that.pendingTransitions)) return false;
        if (!states.equals(that.states)) return false;
        if (!tokens.equals(that.tokens)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = states.hashCode();
        result = 31 * result + cut.hashCode();
        result = 31 * result + currentState.hashCode();
        result = 31 * result + tokens.hashCode();
        result = 31 * result + pendingEvents.hashCode();
        result = 31 * result + pendingTransitions.hashCode();
        return result;
    }
}