package ca.mcmaster.capstone.networking.util;

import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;

import java.util.Collection;

public interface NpiUpdateCallbackReceiver {

    public void npiUpdate(final Collection<NetworkPeerIdentifier> npiPeers);

}
