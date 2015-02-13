package ca.mcmaster.capstone.monitoralgorithm;

import java.util.HashMap;
import java.util.Map;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/* Class to represent a vector clock.*/
@EqualsAndHashCode
public class VectorClock {
    public static enum Comparison { EQUAL, BIGGER, SMALLER, CONCURRENT }

    private final Map<Integer, Integer> consistentCut = new HashMap<>();

    public VectorClock(@NonNull final VectorClock vc) {
        this.consistentCut.putAll(vc.consistentCut);
    }

    /*
     * Constructs a vector clock from a List of Integers.
     *
     * @param consistentCut A List of Integers representing a consistent cut of all known processes.
     */
    public VectorClock(@NonNull final Map<Integer, Integer> consistentCut) {
        this.consistentCut.putAll(consistentCut);
    }

    /*
     * Compare two vector clocks.
     *
     * @param clock The clock to compare this to.
     * @return EQUAL if both clock are the same, BIGGER if this has a more recent event, SMALLER
     *         if clock has a more recent event, CONCURRENT each has some more recent events.
     * @throws IllegalArgumentException If the clocks are of different size this exception is thrown.
     */
    public Comparison compareToClock(@NonNull final VectorClock clock) {
        if (this.size() != clock.size()) {
            throw new IllegalArgumentException("Clock passed to merge must match size of caller.");
        }

        boolean bigger = false;
        boolean smaller = false;
        for (final Map.Entry<Integer, Integer> entry : this.consistentCut.entrySet()) {
            if (entry.getValue() > clock.process(entry.getKey())) {
                bigger = true;
            } else if (entry.getValue() < clock.process(entry.getKey())) {
                smaller = true;
            }
        }

        if (bigger && smaller) {
            return Comparison.CONCURRENT;
        } else if (bigger) {
            return Comparison.BIGGER;
        } else if (smaller) {
            return Comparison.SMALLER;
        }
        return Comparison.EQUAL;
    }

    /*
     * Merge two vector clocks so that that merged clock contains the most recently occurring events
     * in each process represented by the vector clocks.
     *
     * @param clock The clock to merge with the caller.
     * @return this merged with clock such that the new clock contains the most recent events from
     *         each clock being merged.
     * @throws IllegalArgumentException If the clocks are of different size this exception is thrown.
     */
    public VectorClock merge(@NonNull final VectorClock clock) {
        if (this.size() != clock.size()) {
            throw new IllegalArgumentException("Clock passed to merge must match size of caller.");
        }

        final Map<Integer, Integer> merged = new HashMap<>();
        for (final Map.Entry<Integer, Integer> entry : this.consistentCut.entrySet()) {
            if (entry.getValue() > clock.process(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            } else {
                merged.put(entry.getKey(), clock.process(entry.getKey()));
            }
        }

        return new VectorClock(merged);
    }

    /*
     * Get the clock for the ith process kept in this VectorClock.
     *
     * @param i The index of the process whose clock to return.
     * @return The clock for the ith process.
     */
    public int process(@NonNull final Integer pid) {
        return consistentCut.get(pid);
    }

    /*
     * Returns the number of processes represented in the VectorClock.
     *
     * @return The number of processes represented in this VectorClock.
     */
    public int size() {
        return consistentCut.size();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : consistentCut.entrySet()) {
            final String entryStr = "{" + entry.getKey().toLogString() + ", " + entry.getValue() + "}";
            builder.append(entryStr);
        }
        return "VectorClock{" +
                builder.toString() +
                '}';
    }
}
