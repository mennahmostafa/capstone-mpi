package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Monitor;
import ca.mcmaster.capstone.monitoralgorithm.MonitorBinder;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.JsonUtil;
import ca.mcmaster.capstone.networking.util.NpiUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.PeerUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.SensorUpdateCallbackReceiver;

import static ca.mcmaster.capstone.networking.util.JsonUtil.asJson;

public class CapstoneActivity extends Activity implements NpiUpdateCallbackReceiver,
                                                            PeerUpdateCallbackReceiver<DeviceInfo> {

    public static final String LOG_TAG = "CapstoneActivity";

    protected TextView jsonTextView;
    protected ListView listView;
    private final NetworkServiceConnection networkServiceConnection = new NetworkServiceConnection();
    private final MonitorServiceConnection monitorServiceConnection = new MonitorServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();
    private final List<NetworkPeerIdentifier> nsdPeers = new ArrayList<>();
    private Intent networkServiceIntent;
    private Intent monitorServiceIntent;
    private Intent initializerServiceIntent;

    private NetworkPeerIdentifier networkPeerIdentifier = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log("Starting");
        setContentView(R.layout.activity_location);

        networkServiceIntent = new Intent(this, CapstoneService.class);
        monitorServiceIntent = new Intent(this, Monitor.class);
        initializerServiceIntent = new Intent(this, Initializer.class);

        listView = (ListView) findViewById(R.id.listView);
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nsdPeers));
        listView.setOnItemClickListener((adapterView, view, i, l)
                                                -> getPeerUpdate((NetworkPeerIdentifier)listView.getItemAtPosition(i)));

        jsonTextView = (TextView) findViewById(R.id.jsonTextView);

        final Button reconnectButton = (Button) findViewById(R.id.reconnectButton);
        reconnectButton.setOnClickListener(v -> {
            reconnect();
            updateSelfInfo();
            ((ArrayAdapter<DeviceInfo>) listView.getAdapter()).notifyDataSetChanged();
            log("Known peers: " + nsdPeers);
        });

        final Button stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(view -> {
            stopNetworkService();
            stopMonitorService();
            stopInitializerService();
            disconnect();
        });

        final Button cubeButton = (Button) findViewById(R.id.cube);
        cubeButton.setOnClickListener(v -> {
            if(networkPeerIdentifier != null) {
                Intent i = new Intent(CapstoneActivity.this, CubeActivity.class);
                startActivity(i);
            }
            else
                Toast.makeText(CapstoneActivity.this, "NSD Conenction invalid", Toast.LENGTH_SHORT).show();
        });

        final Button nfcButton = (Button) findViewById(R.id.nfc);
        nfcButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (networkPeerIdentifier != null) {
                    Intent i = new Intent(CapstoneActivity.this, NfcActivity.class);
                    startActivity(i);
                }
                else
                    Toast.makeText(CapstoneActivity.this, "NSD Connection invalid", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPeerUpdate(final NetworkPeerIdentifier peer) {
        if (networkServiceConnection.isBound()) {
            networkServiceConnection.getService().sendHandshakeToPeer(peer);
            networkServiceConnection.getService().requestUpdateFromPeer(peer, this);
        }
    }

    @Override
    public void npiUpdate(final Collection<NetworkPeerIdentifier> npiPeers) {
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> {
            CapstoneActivity.this.nsdPeers.clear();
            CapstoneActivity.this.nsdPeers.addAll(npiPeers);
            ((ArrayAdapter<DeviceInfo>) listView.getAdapter()).notifyDataSetChanged();
        });
    }

    @Override
    public void peerUpdate(final DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return;
        }
        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setTitle(deviceInfo.getIp() + ":" + deviceInfo.getPort())
                .setMessage(asJson(deviceInfo))
                .create().show();
    }

    private void stopNetworkService() {
        if (!networkServiceConnection.isBound()) {
            Toast.makeText(this, "Network service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Network Service");
        npiUpdate(Collections.<NetworkPeerIdentifier>emptySet());
        stopService(networkServiceIntent);
        Toast.makeText(this, "Network service stopped", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitorService() {
        if (!monitorServiceConnection.isBound()) {
            Toast.makeText(this, "Monitor service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Monitor Service");
        stopService(monitorServiceIntent);
        Toast.makeText(this, "Monitor service stopped", Toast.LENGTH_SHORT).show();
    }

    private void stopInitializerService() {
        if (!initializerServiceConnection.isBound()) {
            Toast.makeText(this, "Initializer service not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        jsonTextView.setText("Not connected to Initializer Service");
        stopService(initializerServiceIntent);
        Toast.makeText(this, "Initializer service stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        reconnect();
    }

    @Override
    public void onPause() {
        super.onPause();
        disconnect();
    }

    private void reconnect() {
        startService(networkServiceIntent);
        getApplicationContext().bindService(networkServiceIntent, networkServiceConnection, BIND_AUTO_CREATE);
        startService(monitorServiceIntent);
        getApplicationContext().bindService(monitorServiceIntent, monitorServiceConnection, BIND_AUTO_CREATE);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);
    }

    private void disconnect() {
        if (networkServiceConnection.isBound()) {
            networkServiceConnection.getService().unregisterNpiUpdateCallback(CapstoneActivity.this);
            attemptUnbind(networkServiceConnection);
        } else {
            log("Network service not bound, cannot disconnect");
        }
        if (monitorServiceConnection.isBound()) {
            attemptUnbind(monitorServiceConnection);
        } else {
            log("Monitor service not bound, cannot disconnect");
        }
        if (initializerServiceConnection.isBound()) {
            attemptUnbind(initializerServiceConnection);
        } else {
            log("Initializer service not bound, cannot disconnect");
        }
    }

    private void attemptUnbind(final ServiceConnection serviceConnection) {
        try {
            getApplicationContext().unbindService(serviceConnection);
        } catch (final IllegalArgumentException iae) {
            log("Could not unbind service: " + serviceConnection + "; not currently bound. " + iae.getLocalizedMessage());
        }
    }

    private void updateSelfInfo() {
        if (!networkServiceConnection.isBound()) {
            Toast.makeText(this, "Service connection not established", Toast.LENGTH_LONG).show();
            log("Service connection not established");
            return;
        }
        final Handler mainHandler = new Handler(this.getMainLooper());
        mainHandler.post(() -> jsonTextView.setText(JsonUtil.asJson(networkServiceConnection.getService().getLocalNetworkPeerIdentifier())));
    }

    private static void log(final String message) {
        Log.v(LOG_TAG, message);
    }

    public class NetworkServiceConnection implements ServiceConnection {

        private CapstoneService service;
        private CapstoneService.CapstoneNetworkServiceBinder binder;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Network service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.binder = (CapstoneService.CapstoneNetworkServiceBinder) service;
            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerNpiUpdateCallback(CapstoneActivity.this);
            CapstoneActivity.this.networkPeerIdentifier = this.service.getLocalNetworkPeerIdentifier();
            updateSelfInfo();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Network service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null;
        }

        public CapstoneService getService() {
            return service;
        }
    }

    public class MonitorServiceConnection implements ServiceConnection {

        private Monitor service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Monitor service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.service = ((MonitorBinder) service).getMonitor();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Monitor service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null;
        }

        public Monitor getService() {
            return service;
        }
    }

    public class InitializerServiceConnection implements ServiceConnection {

        private Initializer service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CapstoneActivity.this, "Initializer service connected", Toast.LENGTH_LONG).show();
            log("Service connected");

            this.service = ((InitializerBinder) service).getInitializer();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CapstoneActivity.this, "Initializer service disconnected", Toast.LENGTH_LONG).show();
            log("Service disconnected");
            this.service = null;
        }

        public boolean isBound() {
            return this.service != null;
        }

        public Initializer getService() {
            return service;
        }
    }
}
