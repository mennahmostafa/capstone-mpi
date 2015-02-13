package ca.mcmaster.capstone.networking.structures;

import android.net.nsd.NsdServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

public final class NetworkPeerIdentifier implements Parcelable {

    private static final ConcurrentMap<Identifier, NetworkPeerIdentifier> cache = new ConcurrentHashMap<>();

    @NonNull @Getter private final InetAddress host;
    @NonNull @Getter private final String serviceName;
    @NonNull @Getter private final String serviceType;
    @Getter private final int port;

    private NetworkPeerIdentifier(@NonNull final NsdServiceInfo nsdServiceInfo) {
        this.host = nsdServiceInfo.getHost();
        this.serviceName = nsdServiceInfo.getServiceName();
        this.serviceType = nsdServiceInfo.getServiceType();
        this.port = nsdServiceInfo.getPort();
    }

    public static NetworkPeerIdentifier get(@NonNull final NsdServiceInfo nsdServiceInfo) {
        final Identifier identifier = Identifier.getIdentifier(nsdServiceInfo.getHost(), nsdServiceInfo.getPort());
        cache.putIfAbsent(identifier, new NetworkPeerIdentifier(nsdServiceInfo));
        return cache.get(identifier);
    }

    public NsdServiceInfo getNsdServiceInfo() {
        final NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setHost(getHost());
        nsdServiceInfo.setServiceName(getServiceName());
        nsdServiceInfo.setServiceType(getServiceType());
        nsdServiceInfo.setPort(getPort());
        return nsdServiceInfo;
    }

    @Override
    public int describeContents() {
        return getNsdServiceInfo().describeContents();
    }

    @Override
    public String toString() {
        return getNsdServiceInfo().toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        getNsdServiceInfo().writeToParcel(dest, flags);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof NetworkPeerIdentifier)) return false;

        final NetworkPeerIdentifier that = (NetworkPeerIdentifier) o;

        return (this.getHost().getHostAddress().contains(that.getHost().getHostAddress())
                        || that.getHost().getHostAddress().contains(this.getHost().getHostAddress()))
                       && this.getPort() == that.getPort();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(getHost())
                .append(getPort())
                .toHashCode();
    }

    @Value @RequiredArgsConstructor(staticName = "getIdentifier")
    private static class Identifier {
        @NonNull InetAddress host;
        int port;
    }

    public String toLogString() {
        return "{" +
                "serviceName='" + serviceName + '\'' +
                ", host=" + host +
                '}';
    }
}
