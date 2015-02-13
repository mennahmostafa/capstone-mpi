package ca.mcmaster.capstone.networking.structures;

import java.io.Serializable;
import java.util.Set;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Builder;

import static ca.mcmaster.capstone.networking.util.JsonUtil.asJson;

@Value
public class PayloadObject<T> implements Serializable {

    public enum Status {
        OK,
        ERROR,
    }

    @NonNull T payload;
    long wallClockCreationTime;
    int nsdPeersSetHash;
    int nsdPeersSetCount;
    @NonNull Status status;

    @Builder
    public PayloadObject(@NonNull final T payload, @NonNull final Set<NetworkPeerIdentifier> nsdPeers, @NonNull final Status status) {
        this.payload = payload;
        this.nsdPeersSetCount = nsdPeers.size();
        this.nsdPeersSetHash = nsdPeers.hashCode();
        this.status = status;
        this.wallClockCreationTime = System.nanoTime();
    }

    @Override
    public String toString() {
        return asJson(this);
    }
}
