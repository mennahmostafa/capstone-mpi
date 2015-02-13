package ca.mcmaster.capstone.monitoralgorithm;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.mcmaster.capstone.initializer.AutomatonFile;
import ca.mcmaster.capstone.initializer.ConjunctFromFile;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent an Automaton.*/
@EqualsAndHashCode @ToString
public class Automaton {

    public static final String LOG_TAG = "automaton";

    public static enum Evaluation {SATISFIED, VIOLATED, UNDECIDED}

    public static final Automaton INSTANCE = new Automaton();

    private AutomatonState initialState;
    private Map<String, AutomatonState> states = new HashMap<>();
    private Set<AutomatonTransition> transitions = new HashSet<>();

    private Automaton() {};

    public void processAutomatonFile(final AutomatonFile automatonFile, final List<ConjunctFromFile> conjunctMap,
                                            final Map<String, NetworkPeerIdentifier> virtualIdentifierMap) {
        final Set<AutomatonFile.Name> names = automatonFile.getStateNames();
        final Set<AutomatonFile.Transition> transitions = automatonFile.getTransitions();

        for (final AutomatonFile.Name name : names) {
            if (name.getType().equals("initial")) {
                states.put(name.getLabel(), new AutomatonState(name.getLabel(), Evaluation.UNDECIDED));
                initialState = states.get(name.getLabel());
            //FIXME: When we implement LTL4 this conditoin needs to be split up
            } else if (name.getType().equals("possible satisfaction") || name.getType().equals("possible violation")) {
                states.put(name.getLabel(), new AutomatonState(name.getLabel(), Evaluation.UNDECIDED));
            } else if (name.getType().equals("satisfaction")) {
                states.put(name.getLabel(), new AutomatonState(name.getLabel(), Evaluation.SATISFIED));
            } else if (name.getType().equals("violation")) {
                states.put(name.getLabel(), new AutomatonState(name.getLabel(), Evaluation.VIOLATED));
            } else {
                Log.d(LOG_TAG, "i should be throwing right now!");
                throw new IllegalArgumentException("Tried to process AutomatonState with unrecognized type.");
            }
        }

        for (final AutomatonFile.Transition transition: transitions) {
            final AutomatonState source = states.get(transition.getSource());
            final AutomatonState destination = states.get(transition.getDestination());
            final String[] conjuncts = transition.getPredicate().split("&");
            final List<ConjunctFromFile> conjunctsForTransition = new ArrayList<>();
            for (final ConjunctFromFile conj : conjunctMap) {
                if (Arrays.asList(conjuncts).contains(conj.getName())) {
                    conjunctsForTransition.add(conj);
                }
            }

            final List<Conjunct> conjunctsWithExpresssions = new ArrayList<>();
            for (final ConjunctFromFile conjunct : conjunctsForTransition) {
                conjunctsWithExpresssions.add(new Conjunct(virtualIdentifierMap.get("x" + conjunct.getOwnerProcess()), conjunct.getExpression()));
            }

            INSTANCE.transitions.add(new AutomatonTransition(source, destination, conjunctsWithExpresssions));
        }
        Log.d(LOG_TAG, "states: " + INSTANCE.states.toString());
        Log.d(LOG_TAG, "transitions: " + INSTANCE.transitions.toString());
    }

    /*
     * Gets the initial state of the automaton.
     *
     * @return The initial state of the automaton.
     */
    public AutomatonState getInitialState() {
        return initialState;
    }

    /*
     * Computes the next state based on the given GlobalView.
     *
     * @param gv The GlobalView to use to compute the next state.
     * @return The next state of the automaton.
     */
    public AutomatonState advance(@NonNull final GlobalView gv) {
        for (final AutomatonTransition transition : transitions) {
            if (transition.getFrom().equals(gv.getCurrentState()) && !transition.getFrom().equals(transition.getTo())) {
                if (transition.evaluate(gv.getStates().values()) == Conjunct.Evaluation.TRUE) {
                    Log.d(LOG_TAG, "Advanced to state: " + transition.getTo().getStateName());
                    return transition.getTo();
                }
            }
        }
        return gv.getCurrentState();
    }

    public Set<AutomatonTransition> getTransitions() {
        return transitions;
    }
}
