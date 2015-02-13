package ca.mcmaster.capstone.monitoralgorithm;

//import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.ToString;
import lombok.Value;

@Value
public final class Event {
    public static enum EventType {SEND, RECEIVE, INTERNAL}

    int eid;
    int pid;
    EventType type;
    Valuation val;
    VectorClock VC;

    /*
     * Returns a ProcessState based on this event's vector clock and variable valuation.
     *
     * @return A new ProcessState based on this event's vector clock and variable valuation.
     */
    public ProcessState getState() {
        return new ProcessState(this.pid, this.val, this.VC);
    }

    @Override
    public String toString() {
        return "Event{" +
                "eid=" + eid +
                ", pid=" + pid +
                ", type=" + type +
                ", val=" + val +
                ", VC=" + VC +
                '}';
    }
}
