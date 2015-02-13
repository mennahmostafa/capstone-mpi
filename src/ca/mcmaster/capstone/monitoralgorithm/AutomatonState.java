package ca.mcmaster.capstone.monitoralgorithm;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/* Class to represent an automaton state.*/
@Value @AllArgsConstructor
public class AutomatonState {
    @NonNull String stateName;
    @NonNull Automaton.Evaluation stateType;

    public AutomatonState(@NonNull final AutomatonState state) {
        this.stateName = state.stateName;
        this.stateType = state.stateType;
    }

    @Override
    public String toString() {
        return "AutomatonState{" +
                "stateName='" + stateName + '\'' +
                ", stateType=" + stateType +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AutomatonState that = (AutomatonState) o;

        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null)
            return false;
        if (stateType != that.stateType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = stateName != null ? stateName.hashCode() : 0;
        result = 31 * result + (stateType != null ? stateType.hashCode() : 0);
        return result;
    }
}