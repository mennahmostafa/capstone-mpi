package ca.mcmaster.capstone.networking.util;

import java.util.Set;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import lombok.NonNull;

public interface NetworkLayer {

    /**
     * {@inheritDoc}
     * @return NetworkPeerIdentifier for the local device
     */
    NetworkPeerIdentifier getLocalNetworkPeerIdentifier();

    void stopNpiDiscovery();

    void registerNpiUpdateCallback(NpiUpdateCallbackReceiver npiUpdateCallbackReceiver);

    void unregisterNpiUpdateCallback(NpiUpdateCallbackReceiver npiUpdateCallbackReceiver);

    /**
     * Broadcasts a Token to a specific peer
     * @param destination
     * @param token
     */
    void sendTokenToPeer(NetworkPeerIdentifier destination, Token token);

    /**
     * Called by the Server when a Token is received over the network
     * @param token
     */
    void receiveTokenInternal(Token token);

    /**
     * Called by the local monitoring process when it wishes to poll for tokens.
     * This is a blocking call - if no tokens are available, the calling thread
     * will wait until there is one.
     * @return the first token in the queue
     * @throws InterruptedException
     */
    Token receiveToken() throws InterruptedException;

    /**
     * Sends an event to the monitor. Must be called by the monitored process when an event occurs.
     * @param event
     */
    void sendEventToMonitor(Event event);

    /**
     * Called by the Server when an Event is received over the network
     * @param event
     */
    void receiveEventExternal(Event event);

    /**
     * Called by the local monitoring process when it wishes to poll for events.
     * This is a blocking call - if no events are available, the calling thread
     * will wait until there is one.
     * @return the first event in the queue
     * @throws InterruptedException
     */
    Event receiveEvent() throws InterruptedException;

    /**
     * Get the set of known peers.
     *
     * Each peer in this set has a unique IP/port pair from any other peer in the set.
     * Each peer was also confirmed to be online and reachable when it was discovered;
     * however, no guarantee can be made that the peer has not gone offline some time
     * in between when it was last contacted and when this method is called.
     * @return the set of known peers
     */
    Set<NetworkPeerIdentifier> getKnownPeers();

    /**
     * Get the set of all known devices, including the local one.
     *
     * Each peer in this set has a unique IP/port pair from any other peer in the set.
     * Each peer was also confirmed to be online and reachable when it was discovered;
     * however, no guarantee can be made that the peer has not gone offline some time
     * in between when it was last contacted and when this method is called.
     * @return the set of known peers, including this device
     */
    Set<NetworkPeerIdentifier> getAllNetworkDevices();

    /**
     * Called by the Monitor when the property it monitors is satisfied.
     */
    void signalMonitorSatisfied();

    /**
     * Called by the Monitor when the property it montors is violated.
     */
    void signalMonitorViolated();

    /**
     * Called by processes which are to be monitored by a Monitor, if they require knowledge of when
     * the Monitor detects satisfaction or violation of its property.
     * @param monitorSatisfactionStateListener the process to be monitored and which requires state knowledge.
     */
    void registerMonitorStateListener(MonitorSatisfactionStateListener monitorSatisfactionStateListener);

    /**
     * Called by a process which has previously registered itself a a Monitor state listener when it no longer
     * requires updates on changes to the monitor state.
     * @param monitorSatisfactionStateListener the process to unregister.
     */
    void unregisterMonitorStateListener(MonitorSatisfactionStateListener monitorSatisfactionStateListener);
}
