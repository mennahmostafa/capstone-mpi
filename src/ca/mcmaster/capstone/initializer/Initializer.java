package ca.mcmaster.capstone.initializer;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import ca.mcmaster.capstone.monitoralgorithm.NetworkServiceConnection;
import ca.mcmaster.capstone.networking.CapstoneService;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.JsonUtil;
import ca.mcmaster.capstone.networking.util.NpiUpdateCallbackReceiver;

public class Initializer extends Service {
    private static class NetworkInitializer implements Runnable, NpiUpdateCallbackReceiver {
        private final NetworkServiceConnection serviceConnection;
        private int numPeers = 0;
        private NetworkPeerIdentifier localPID;
        private final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
        private final CountDownLatch initializationLatch = new CountDownLatch(1);
        private final CountDownLatch peerCountLatch = new CountDownLatch(1);
        private volatile boolean cancelled = false;
        private static final File NUMPEERS_FILE = new File(Environment.getExternalStorageDirectory(), "monitorInit/numPeers");

        public NetworkInitializer(final NetworkServiceConnection serviceConnection) {
            Log.v("networkInitializer", "created");
            //FIXME: I would like to redo the initializer and combine the configuratoin into a more coherant set of file, but this will do for now
            try {
                final BufferedReader br = new BufferedReader(new FileReader(NUMPEERS_FILE));
                while (br.ready()) {
                    final String line = br.readLine();
                    this.numPeers = Integer.parseInt(line);
                }
                br.close();
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
            this.serviceConnection = serviceConnection;
            cancelled = false;
        }

        @Override
        public void npiUpdate(final Collection<NetworkPeerIdentifier> npiPeers) {
            if (npiPeers.size() == numPeers - 1) { // npiPeers set does not include local PID
                Log.v("networkInitializer", "has enough npi peers - unlatching");
                peerCountLatch.countDown();
                serviceConnection.getNetworkLayer().stopNpiDiscovery();
            }
        }

        private void waitForNetworkLayer() {
            Log.v("networkInitializer", "waitForNetworkLayer");
            while (serviceConnection.getNetworkLayer() == null && !cancelled) {
                try {
                    Log.v("networkInitializer", "waiting 1 second for network layer to appear...");
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    Log.d("initializer", "NetworkLayer connection is not established: " + e.getLocalizedMessage());
                }
            }
        }

        @Override
        public void run() {
            Log.v("networkInitializer", "running");
            waitForNetworkLayer();
            Log.v("networkInitializer", "got network layer");
            serviceConnection.getNetworkLayer().registerNpiUpdateCallback(this);
            if (serviceConnection.getNetworkLayer().getAllNetworkDevices().size() == numPeers) {
                peerCountLatch.countDown();
            }

            localPID = serviceConnection.getNetworkLayer().getLocalNetworkPeerIdentifier();
            Log.v("networkInitializer", "got localPID");
            Log.v("networkInitializer", "getting virtual identifiers");
            this.virtualIdentifiers.putAll(generateVirtualIdentifiers());
            Log.v("networkInitializer", "got virtual identifiers");

            for (final Map.Entry<String, NetworkPeerIdentifier> entry : virtualIdentifiers.entrySet()) {
                Log.v("initializer", entry.getKey() + " - " + entry.getValue());
                if (entry.getValue().equals(localPID)) {
                    Log.v("initializer", "I am " + entry.getKey()  + "!");
                }
            }
            Log.v("networkInitializer", "unlatching initialization latch");
            initializationLatch.countDown();
            Log.v("networkInitializer", "finished");
        }

        private void waitForLatch(final CountDownLatch latch) {
            Log.v("networkInitializer", "waiting for latch: " + latch);
            while (latch.getCount() > 0 && !cancelled) {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException ie) {
                    // don't really care, just need to try again
                }
            }
            Log.v("networkInitializer", "stopped waiting for latch: " + latch);
        }

        private Map<String, NetworkPeerIdentifier> generateVirtualIdentifiers() {
            waitForLatch(peerCountLatch);
            final Map<String, NetworkPeerIdentifier> virtualIdentifiers = new HashMap<>();
            final List<NetworkPeerIdentifier> sortedIdentifiers = new ArrayList<>(serviceConnection.getNetworkLayer().getAllNetworkDevices());
            Collections.sort(sortedIdentifiers, (f, s) -> Integer.compare(f.hashCode(), s.hashCode()));
            for (final NetworkPeerIdentifier networkPeerIdentifier : sortedIdentifiers) {
                final String virtualIdentifier = "x" + (sortedIdentifiers.indexOf(networkPeerIdentifier) + 1);
                virtualIdentifiers.put(virtualIdentifier, networkPeerIdentifier);
            }
            return virtualIdentifiers;
        }

        public NetworkPeerIdentifier getLocalPID() {
            waitForLatch(initializationLatch);
            return localPID;
        }

        public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
            waitForLatch(initializationLatch);
            return virtualIdentifiers;
        }

        public void cancel() {
            Log.v("networkInitializer", "cancelling");
            cancelled = true;
            peerCountLatch.countDown();
            initializationLatch.countDown();
            serviceConnection.getNetworkLayer().unregisterNpiUpdateCallback(this);
        }
    }

    private static class AutomatonInitializer implements Runnable {
        private static final File AUTOMATON_FILE = new File(Environment.getExternalStorageDirectory(), "monitorInit/automaton.json");
        private static final File CONJUNCT_FILE = new File(Environment.getExternalStorageDirectory(), "monitorInit/conjunct_mapping.my");
        private static final File INITIAL_STATE_FILE = new File(Environment.getExternalStorageDirectory(), "monitorInit/initial_state.json");
        private final CountDownLatch latch = new CountDownLatch(1);
        private AutomatonFile automaton = null;
        private final List<ConjunctFromFile> conjunctMap = new ArrayList<>();
        private InitialState initialState = null;

        @Override
        public void run() {
            Log.d("automatonInitializer", "Started");
            try {
                // Parse the automaton file
                this.automaton = JsonUtil.fromJson(FileUtils.readFileToString(AUTOMATON_FILE, Charset.forName("UTF-8")),
                        AutomatonFile.class);
                // Parse the conjunct mapping file
                final BufferedReader mapping = new BufferedReader(new FileReader(CONJUNCT_FILE));
                while (mapping.ready()) {
                    final String line = mapping.readLine();
                    if (!line.startsWith("#")) {
                        final String[] fields = line.split(",");
                        conjunctMap.add(new ConjunctFromFile(fields[1], fields[0], fields[2]));
                    }
                }
                mapping.close();
                //Parse initial valuation
                this.initialState = JsonUtil.fromJson(FileUtils.readFileToString(INITIAL_STATE_FILE, Charset.forName("UTF-8")), InitialState.class);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
            latch.countDown();
        }

        public AutomatonFile getAutomatonFile() {
            waitForLatch(latch);
            return automaton;
        }

        public List<ConjunctFromFile> getConjunctMap() {
            waitForLatch(latch);
            return conjunctMap;
        }

        public InitialState getInitialState() {
            waitForLatch(latch);
            return initialState;
        }

        private void waitForLatch(final CountDownLatch latch) {
            Log.v("automatonInitializer", "waiting for latch: " + latch);
            while (latch.getCount() > 0) {
                try {
                    latch.await(500, TimeUnit.MILLISECONDS);
                } catch (final InterruptedException ie) {
                    // don't really care, just need to try again
                }
            }
            Log.v("automatonInitializer", "stopped waiting for latch: " + latch);
        }
    }

    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private Intent networkServiceIntent;
    private Future<?> networkInitJob = null;

    private final NetworkInitializer network = new NetworkInitializer(networkServiceConnection);
    private final AutomatonInitializer automatonInit = new AutomatonInitializer();

    @Override
    public IBinder onBind(Intent intent) {
        return new InitializerBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("initializer", "creating initializer");
        networkServiceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);

        networkInitJob = Executors.newSingleThreadExecutor().submit(network);
        Executors.newSingleThreadExecutor().submit(automatonInit);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v("initializer", "destroying initializer");
        getApplicationContext().unbindService(networkServiceConnection);
        network.cancel();
        if (networkInitJob != null) {
            networkInitJob.cancel(true);
        }
    }

    public AutomatonFile getAutomatonFile() {
        return automatonInit.getAutomatonFile();
    }

    public List<ConjunctFromFile> getConjunctMap() {
        return automatonInit.getConjunctMap();
    }

    public InitialState getInitialState() { return automatonInit.getInitialState(); }

    public NetworkPeerIdentifier getLocalPID() {
        return network.getLocalPID();
    }

    public Map<String, NetworkPeerIdentifier> getVirtualIdentifiers() {
        return network.getVirtualIdentifiers();
    }
}
