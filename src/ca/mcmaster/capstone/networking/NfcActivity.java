package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import lombok.NonNull;
import lombok.Value;

import static ca.mcmaster.capstone.util.CollectionUtils.each;
import static ca.mcmaster.capstone.util.CollectionUtils.filter;
import static ca.mcmaster.capstone.util.FileUtil.getLines;

public class NfcActivity extends Activity implements MonitorSatisfactionStateListener {

    public static final Pattern NFC_TAG_ID_PATTERN = Pattern.compile("^([0-9A-Za-z]+)\\s+([\\d]+)\\s+([\\w]+)$");
    public static final Pattern NFC_PATH_PATTERN = Pattern.compile("^(\\w+):((?:\\s+\\w+)*)$");
    public static final Set<NfcTagIDs> NFC_TAG_IDS = new HashSet<>();
    public static final String NFC_INIT_STORAGE_DIRECTORY = Environment.getExternalStorageDirectory().getPath() + "/nfcInit/";
    public static final String GLOBAL_NFC_TAG_SET_CONFIG_FILENAME = "destinations.txt";
    public static final String LOCAL_NFC_TAG_LIST_CONFIG_FILENAME = "destinationList.txt";
    public static final String LOG_TAG = "NfcActivity";

    protected NfcAdapter nfcAdapter;
    protected PendingIntent nfcPendingIntent;
    private NetworkPeerIdentifier NSD;
    private Map<String, List<NfcTagIDs>> destinations = new HashMap<>();

    private int eventCounter = 0;
    private String variableName;
    private Boolean satisfaction = null;

    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

    @Override
    public void onMonitorSatisfied() {
        satisfaction = true;
        updateUI();
    }

    @Override
    public void onMonitorViolated() {
        satisfaction = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        enableForegroundMode();
    }

    @Override
    protected void onPause() {
        super.onPause();

        disableForegroundMode();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nfc);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass())
                                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        try {
            NFC_TAG_IDS.addAll(getNfcTagIDsFromFile(NFC_INIT_STORAGE_DIRECTORY + GLOBAL_NFC_TAG_SET_CONFIG_FILENAME));
        } catch (final IOException ioe) {
            Toast.makeText(getApplicationContext(), "Destination file could not be read!", Toast.LENGTH_LONG).show();
        }

        try {
            destinations.putAll(getNfcTagPathFromFile(NFC_INIT_STORAGE_DIRECTORY + LOCAL_NFC_TAG_LIST_CONFIG_FILENAME));
        } catch (final IOException ioe) {
            Toast.makeText(getApplicationContext(), "Destination list config file could not be read!", Toast.LENGTH_LONG).show();
        }
        updateUI();

        final Intent serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        final Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent,
                initializerServiceConnection,
                BIND_AUTO_CREATE);
    }

    public static Set<NfcTagIDs> getNfcTagIDsFromFile(@NonNull final String path) throws IOException {
        final Set<NfcTagIDs> destinations = new HashSet<>();
        // TODO: implement and use CollectionUtils.map
        each(getLines(new File(path)), line -> {
            final Matcher matcher = NFC_TAG_ID_PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid NFC Tag ID line: \"" + line + "\" in file " + path);
            }
            final String uuid = matcher.group(1);
            final double id = Double.parseDouble(matcher.group(2));
            final String label = matcher.group(3);
            destinations.add(new NfcTagIDs(uuid, id, label));
        });
        return destinations;
    }

    public static Map<String, List<NfcTagIDs>> getNfcTagPathFromFile(@NonNull final String path) throws IOException {
        final Map<String, List<NfcTagIDs>> destinations = new HashMap<>();
        each(getLines(new File(path)), line -> {
            final Matcher matcher = NFC_PATH_PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalStateException("Invalid NFC path line: \"" + line + "\" in file " + path);
            }
            final String virtualID = matcher.group(1);
            final String destinationString = matcher.group(2);
            final List<String> destinationList = Arrays.asList(destinationString.split("\\s+"));
            final List<NfcTagIDs> tagIDs = new ArrayList<>();
            each(destinationList, str -> tagIDs.addAll(filter(NFC_TAG_IDS, tag -> tag.getLabel().equals(str))));
            destinations.put(virtualID, tagIDs);
        });
        return destinations;
    }

    public void updateUI() {
        runOnUiThread(() -> {
            if (variableName == null) {
                return;
            }
            final TextView text = (TextView) findViewById(R.id.next_destination);

            if (destinations.get(variableName).isEmpty()) {
                text.setText(variableName + ": " + "You're done!\nSatisfied? " + satisfaction);
            } else {
                text.setText(variableName + ": " + destinations.get(variableName).get(0).getLabel() + "\nSatisfied? " + satisfaction);
            }
        });
    }

    public void sendEvent(final double value) {
        waitForNetworkLayer();
        final Valuation valuation = new Valuation(new HashMap<String, Double>() {{
            put(NfcActivity.this.variableName, value);
        }});
        ++eventCounter;
        final Event e = new Event(eventCounter, NSD, Event.EventType.INTERNAL, valuation,
                new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
                    put(serviceConnection.getService().getLocalNetworkPeerIdentifier(), eventCounter);
                    for (final NetworkPeerIdentifier peer : serviceConnection.getService().getKnownPeers()) {
                        put(peer, 0);
                    }
                }}));
        Toast.makeText(NfcActivity.this, "Event has left the building", Toast.LENGTH_SHORT).show();
        serviceConnection.getService().sendEventToMonitor(e);
    }

    private void waitForNetworkLayer() {
        Log.v(LOG_TAG, "waitForNetworkLayer");
        while (serviceConnection.getService() == null) {
            try {
                Log.v(LOG_TAG, "waiting 1 second for network layer to appear...");
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Log.d(LOG_TAG, "NetworkLayer connection is not established: " + e.getLocalizedMessage());
            }
        }
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (intent.getAction().equals(NfcAdapter.ACTION_TAG_DISCOVERED)) {
            final String uuid = byteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));

            if (uuid.equals(destinations.get(variableName).get(0).getUuid())) {
                destinations.get(variableName).remove(0);
            }

            for (final NfcTagIDs nfcTagID : filter(NFC_TAG_IDS, id -> id.getUuid().equals(uuid))) {
                sendEvent(nfcTagID.getId());
            }

            updateUI();
        }
    }

    public void enableForegroundMode() {
        Log.d(LOG_TAG, "enableForegroundMode");

        final IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        final IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
        nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
    }

    public void disableForegroundMode() {
        Log.d(LOG_TAG, "disableForegroundMode");

        nfcAdapter.disableForegroundDispatch(this);
    }

    static String byteArrayToHexString(@NonNull final byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "X", bi);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return super.onOptionsItemSelected(item);
    }

    @Value private static class NfcTagIDs {
        String uuid;
        double id;
        String label;
    }

    public class LocationServiceConnection implements ServiceConnection {

        private CapstoneService service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(NfcActivity.this, "Service connected", Toast.LENGTH_LONG).show();

            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerMonitorStateListener(NfcActivity.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(NfcActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
            this.service = null;
        }

        public CapstoneService getService() {
            return service;
        }
    }

    public class InitializerServiceConnection implements ServiceConnection{
        private Initializer initializer;

        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
            this.initializer = ((InitializerBinder) iBinder).getInitializer();

            NfcActivity.this.NSD = initializer.getLocalPID();
            //FIXME: this is for testing out simple test case. More work is needed for more complex variableGlobalText arrangements
            for (Map.Entry<String, NetworkPeerIdentifier> virtualID : initializer.getVirtualIdentifiers().entrySet()) {
                if (virtualID.getValue() == NSD) {
                    NfcActivity.this.variableName = virtualID.getKey();
                    updateUI();
                    break;
                }
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            this.initializer = null;
        }

        public Initializer getInitializer() {
            return this.initializer;
        }
    }

}
