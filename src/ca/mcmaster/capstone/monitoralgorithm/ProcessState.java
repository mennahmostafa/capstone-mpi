package ca.mcmaster.capstone.monitoralgorithm;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@ToString
public class ProcessState implements Serializable{
    @NonNull @Getter private final Integer id;
    @NonNull private final Valuation val;
    @NonNull private final VectorClock VC;

    /*
     * Construct a new ProcessState.
     *
     * @param id An identifier for this ProcessState.
     * @param val The valuation of the process' variables.
     * @param VC The vector clock for the process.
     */
    public ProcessState(@NonNull final Integer id, @NonNull final Valuation val, @NonNull final VectorClock VC) {
        this.id = id;
        this.val = new Valuation(val);
        this.VC = new VectorClock(VC);
    }

    public ProcessState(@NonNull final ProcessState state) {
        this.id = state.id;
        this.val = new Valuation(state.val);
        this.VC = new VectorClock(state.VC);
    }

    public Valuation getVal() {
        return new Valuation(val);
    }

    public VectorClock getVC() {
        return new VectorClock(VC);
    }

    /*
         * Updates the ProcessState with the state after event occurs.
         *
         * @param event The event to update to.
         * @return A new ProcessState updated with event.
         */
    public ProcessState update(@NonNull final Event event) {
        return new ProcessState(this.id, event.getVal(), this.VC.merge(event.getVC()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProcessState that = (ProcessState) o;

        if (!VC.equals(that.VC)) return false;
        if (!id.equals(that.id)) return false;
        if (!val.equals(that.val)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + val.hashCode();
        result = 31 * result + VC.hashCode();
        return result;
    }
}
