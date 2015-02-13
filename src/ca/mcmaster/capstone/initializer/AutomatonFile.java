package ca.mcmaster.capstone.initializer;

import com.google.gson.annotations.SerializedName;

import java.util.HashSet;
import java.util.Set;

import lombok.NonNull;
import lombok.Value;

@Value
public class AutomatonFile {
    @SerializedName("state_names") Set<Name> stateNames = new HashSet<>();
    Set<Transition> transitions = new HashSet<>();

    @Value
    public static class Name {
        @NonNull String label;
        @NonNull String type;
    }

    @Value
    public static class Transition {
        @NonNull String source;
        @NonNull String destination;
        @NonNull String predicate;
    }
}
