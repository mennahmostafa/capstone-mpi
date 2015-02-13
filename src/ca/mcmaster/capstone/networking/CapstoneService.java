package ca.mcmaster.capstone.networking;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Token;
import ca.mcmaster.capstone.networking.structures.DeviceInfo;
import ca.mcmaster.capstone.networking.structures.DeviceLocation;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.structures.PayloadObject;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import ca.mcmaster.capstone.networking.util.NetworkLayer;
import ca.mcmaster.capstone.networking.util.NpiUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.PeerUpdateCallbackReceiver;
import ca.mcmaster.capstone.networking.util.SensorUpdateCallbackReceiver;
import lombok.NonNull;

import static ca.mcmaster.capstone.networking.util.JsonUtil.asJson;
import static ca.mcmaster.capstone.networking.util.JsonUtil.fromJson;

public final class CapstoneService extends Service implements NetworkLayer {

    private static final String NSD_LOCATION_SERVICE_NAME = "CapstoneLocationNSD";
    private static final String NSD_LOCATION_SERVICE_TYPE = "_http._tcp.";
    public static final String LOG_TAG = "CapstoneService";

    private InetAddress ipAddress;

    private LocationManager locationManager;
    private WifiManager wifiManager;
    private SensorManager sensorManager;
    private Sensor barometer, gravitySensor;
    private LocationProvider gpsProvider;
    private double barometerPressure;

    private Location lastLocation;
    private BarometerEventListener barometerEventListener;
    private GravitySensorEventListener gravitySensorEventListener;
    private CapstoneLocationListener locationListener;

    private RequestQueue volleyRequestQueue;
    private CapstoneServer locationServer;

    private volatile NsdManager.RegistrationListener nsdRegistrationListener;
    private volatile NsdManager.DiscoveryListener nsdDiscoveryListener;
    private volatile NsdManager nsdManager;

    private final Set<NetworkPeerIdentifier> nsdPeers =
            Collections.synchronizedSet(new HashSet<>());

    private final Set<SensorUpdateCallbackReceiver<DeviceInfo>> sensorUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<PeerUpdateCallbackReceiver<NetworkPeerIdentifier>> peerUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final Set<NpiUpdateCallbackReceiver> npiUpdateCallbackReceivers =
            Collections.synchronizedSet(new HashSet<>());
    private final BlockingQueue<Event> incomingEventQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Token> tokenQueue = new LinkedBlockingQueue<>();
    private volatile boolean nsdBound;

    private final float[] gravity = new float[3];
    private final float[] linearAcceleration = new float[3];
    private final Set<MonitorSatisfactionStateListener> monitorStateListeners = new HashSet<>();

    @Override
    public void onCreate() {
        logv("Created");
        System.setProperty("http.keepAlive", "false");

        this.wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        this.ipAddress = findIpAddress();

        createPersistentNotification();
        setupLocationServices();
        setupBarometerService();
        setupGravitySensorService();
        setupLocalHttpClient();
        setupLocalHttpServer();
        setupNsdRegistration();

        // Sometimes getting the system NSD_SERVICE blocks for a few minutes or indefinitely... start these components
        // async so we can eventually get updates when ready while allowing the UI to populate/appear in the meantime,
        // even if blank
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                logv("Async launching NSD services");
                nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
                setupNsdDiscovery();
                nsdRestart();
                logv("Done NSD async start");
                return null;
            }
        }.execute();

        Toast.makeText(getApplicationContext(), "Capstone Location Service starting", Toast.LENGTH_LONG).show();
        logv("Capstone Location Service starting");
    }

    private void setupNsdRegistration() {
        logv("Setting up NSD registration...");
        logv("Attempting to register NSD service: " + getLocalNsdServiceInfo());

        nsdRegistrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
                logd("Failed to register service " + nsdServiceInfo + ". Error: " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
                logd("Failed to unregister service " + nsdServiceInfo + ". Error: " + errorCode);
            }

            @Override
            public void onServiceRegistered(final NsdServiceInfo nsdServiceInfo) {
                logv("NSD service registered: " + nsdServiceInfo);
//                assignedNsdServiceName = localNsdServiceInfo.getServiceName();
            }

            @Override
            public void onServiceUnregistered(final NsdServiceInfo nsdServiceInfo) {
                logv("Local NSD service unregistered: " + nsdServiceInfo);
//                assignedNsdServiceName = null;
            }
        };
        logv("Done");
    }

    private void nsdRegister() {
        try {
            nsdManager.registerService(getLocalNsdServiceInfo(), NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener);
        } catch (final IllegalArgumentException iae) {
            logd(iae.getLocalizedMessage());
        }
    }

    private void setupNsdDiscovery() {
        logv("Setting up NSD discovery...");
        nsdDiscoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(final String regType, final int errorCode) {
                logd("NSD start discovery failed, error: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(final String regType, final int errorCode) {
                logd("NSD stop discovery failed, error: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onDiscoveryStarted(final String regType) {
                logv("Discovery started for: " + regType);
            }

            @Override
            public void onDiscoveryStopped(final String regType) {
                logv("Discovery stopped for: " + regType);
            }

            @Override
            public void onServiceFound(final NsdServiceInfo nsdPeerInfo) {
                if (nsdPeerInfo == null
                        || nsdPeerInfo.getServiceType() == null
                        || nsdPeerInfo.getServiceName() == null) {
                    return;
                }

                if (!nsdPeerInfo.getServiceType().equals(getNsdServiceType())) {
                    logv("Unknown Service Type: " + nsdPeerInfo);
                } else if (nsdPeerInfo.getServiceName().contains(getLocalNsdServiceName())) {
                    //logv("Same machine: " + nsdPeerInfo);
                } else if (nsdPeerInfo.getServiceName().contains(getNsdServiceName())) {
                    nsdManager.resolveService(nsdPeerInfo, new NsdResolveListener());
                } else {
                    logv("Could not register NSD service: " + nsdPeerInfo);
                }
            }

            @Override
            public void onServiceLost(final NsdServiceInfo nsdServiceInfo) {
                if (nsdServiceInfo.getHost() == null) {
                    return;
                }
                final NetworkPeerIdentifier networkPeerIdentifier = NetworkPeerIdentifier.get(nsdServiceInfo);
                nsdPeers.remove(networkPeerIdentifier);
                updateNpiCallbackListeners();
            }
        };
        logv("Done");
    }

    public NsdServiceInfo getLocalNsdServiceInfo() {
        final NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(getLocalNsdServiceName());
        nsdServiceInfo.setServiceType(getNsdServiceType());
        nsdServiceInfo.setPort(locationServer.getListeningPort());
        nsdServiceInfo.setHost(getIpAddress());
        return nsdServiceInfo;
    }

    /**
     * {@inheritDoc}
     * @return NetworkPeerIdentifier for the local device
     */
    @Override
    public NetworkPeerIdentifier getLocalNetworkPeerIdentifier() {
        return NetworkPeerIdentifier.get(getLocalNsdServiceInfo());
    }

    private void nsdDiscover() {
        try {
            nsdManager.discoverServices(getNsdServiceType(), NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener);
        } catch (final IllegalArgumentException iae) {
            logd(iae.getLocalizedMessage());
        }
    }

    private void updateNpiCallbackListeners() {
        logv("Updating NPI receivers with new peer list: " + nsdPeers);
        for (final NpiUpdateCallbackReceiver npiUpdateCallbackReceiver : npiUpdateCallbackReceivers) {
            npiUpdateCallbackReceiver.npiUpdate(nsdPeers);
        }
    }

    private void nsdRestart() {
        if (nsdBound) {
            return;
        }
        new AsyncTask<Void, Void, Void>() {
            @Override
            public Void doInBackground(final Void... voids) {
                while (nsdManager == null) {
                    try {
                        Thread.sleep(100);
                    } catch (final InterruptedException ie) {
                        // ignore
                    }
                }
                nsdRegister();
                nsdDiscover();
                nsdBound = true;
                return null;
            }
        }.execute();
    }

    private void setupLocalHttpClient() {
        logv("Setting up local HTTP client...");
        volleyRequestQueue = Volley.newRequestQueue(this);
        logv("Done");
    }

    private void setupLocalHttpServer() {
        logv("Setting up local HTTP server...");
        if (locationServer != null && locationServer.wasStarted()) {
            locationServer.stop();
        }
        locationServer = new CapstoneServer(this.getIpAddress(), this);
        try {
            locationServer.start();
        } catch (final IOException ioe) {
            Log.e(LOG_TAG, "Error starting NanoHTTPD server", ioe);
        }
        logv("Done");
    }

    private void setupBarometerService() {
        logv("Setting up barometer service...");
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        barometerEventListener = new BarometerEventListener();
        sensorManager.registerListener(barometerEventListener, barometer, SensorManager.SENSOR_DELAY_NORMAL);
        logv("Done");
    }

    private void setupGravitySensorService() {
        logv("Setting up gravity sensor service...");
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravitySensorEventListener = new GravitySensorEventListener();
        sensorManager.registerListener(gravitySensorEventListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        logv("Done");
    }

    private void setupLocationServices() {
        logv("Setting up location services...");
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);

        locationListener = new CapstoneLocationListener();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        logv("Done");
    }

    private void createPersistentNotification() {
        logv("Creating persistent notification...");
        final Notification notification = new Notification(0, "Capstone Location",
                                                                  System.currentTimeMillis());
        final Intent notificationIntent = new Intent(this, CapstoneActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, "Capstone Location Service",
                                               "GPS Tracking", pendingIntent);
        startForeground(100, notification);
        logv("Done");
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return new CapstoneNetworkServiceBinder();
    }

    @Override
    public void onRebind(final Intent intent) {
        for (final NpiUpdateCallbackReceiver NpiUpdateCallbackReceiver : npiUpdateCallbackReceivers) {
            NpiUpdateCallbackReceiver.npiUpdate(nsdPeers);
        }
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        return false;
    }

    @Override
    public void stopNpiDiscovery() {
        if (!nsdBound) {
            logv("Cannot stop NSD discovery - not bound");
            return;
        }
        nsdManager.unregisterService(nsdRegistrationListener);
        nsdManager.stopServiceDiscovery(nsdDiscoveryListener);
        nsdBound = false;
        logv("Stopped NSD discovery");
    }

    public void stopLocationService() {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Toast.makeText(getApplicationContext(), "Capstone Location Service stopping", Toast.LENGTH_LONG).show();
        locationManager.removeUpdates(locationListener);
        sensorManager.unregisterListener(barometerEventListener);
        sensorManager.unregisterListener(gravitySensorEventListener);
        sensorUpdateCallbackReceivers.clear();
        peerUpdateCallbackReceivers.clear();
        npiUpdateCallbackReceivers.clear();
        stopNpiDiscovery();
        locationServer.stop();
        volleyRequestQueue.stop();
        stopLocationService();
        logv("Capstone Location Service stopping");
    }

    DeviceInfo getStatus() {
        final DeviceLocation deviceLocation = new DeviceLocation(lastLocation, barometerPressure, gravity, linearAcceleration);
        return new DeviceInfo(getLocalNsdServiceInfo().getHost().getHostAddress(),
                                     locationServer.getListeningPort(), deviceLocation);
    }

    String getStatusAsJson() {
        final DeviceInfo deviceInfo = getStatus();
        return asJson(deviceInfo);
    }

    void registerSensorUpdateCallback(@NonNull final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver) {
        this.sensorUpdateCallbackReceivers.add(sensorUpdateCallbackReceiver);
    }

    void unregisterSensorUpdateCallback(@NonNull final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver) {
        this.sensorUpdateCallbackReceivers.remove(sensorUpdateCallbackReceiver);
    }

    @Override
    public void registerNpiUpdateCallback(@NonNull final NpiUpdateCallbackReceiver npiUpdateCallbackReceiver) {
        this.npiUpdateCallbackReceivers.add(npiUpdateCallbackReceiver);
    }

    @Override
    public void unregisterNpiUpdateCallback(@NonNull final NpiUpdateCallbackReceiver npiUpdateCallbackReceiver) {
        this.npiUpdateCallbackReceivers.remove(npiUpdateCallbackReceiver);
    }

    void sendHandshakeToPeer(@NonNull final NetworkPeerIdentifier nsdPeer) {
        final Set<NetworkPeerIdentifier> peersPlusSelf = new HashSet<>();
        peersPlusSelf.addAll(nsdPeers);
        peersPlusSelf.add(getLocalNetworkPeerIdentifier());
        peersPlusSelf.remove(nsdPeer);

        for (final NetworkPeerIdentifier networkPeerIdentifier : peersPlusSelf) {
            sendNsdInfoToPeer(nsdPeer, networkPeerIdentifier);
        }
    }

    private void postDataToPeer(@NonNull final NetworkPeerIdentifier peer,
                                    final Object data,
                                    @NonNull final Response.Listener<JSONObject> successListener,
                                    @NonNull final Response.ErrorListener errorListener,
                                    @NonNull final CapstoneServer.RequestMethod requestMethod) {

        final String contentBody = asJson(data);
        final JSONObject payload;
        try {
            payload = new JSONObject(contentBody);
        } catch (final JSONException e) {
            logv("Could not POST data, JSONException: " + e.getLocalizedMessage());
            logv("Data: " + data);
            logv("As JSON: " + contentBody);
            return;
        }

        queueVolleyRequest(Request.Method.POST, peer, payload, requestMethod, successListener, errorListener);
    }

    private void queueVolleyRequest(final int method,
                                    @NonNull final NetworkPeerIdentifier peer,
                                    final JSONObject payload,
                                    @NonNull final CapstoneServer.RequestMethod requestMethod,
                                    @NonNull final Response.Listener<JSONObject> successListener,
                                    @NonNull final Response.ErrorListener errorListener) {
        if (peer == null || peer.getHost() == null) {
            logv("Could not queue Volley request:");
            logv("method = [" + method + "], peer = [" + peer + "], payload = [" + payload + "], requestMethod = [" + requestMethod + "], successListener = [" + successListener + "], errorListener = [" + errorListener + "]");
            return;
        }
        final String peerUrl = getUrlStringForPeer(peer);
        final JsonObjectRequest request = new JsonObjectRequest(
                method,
                peerUrl,
                payload,
                successListener,
                errorListener
        ) {
            @Override
            public Map<String, String> getHeaders() {
                final Map<String, String> headers = new HashMap<>();
                headers.put(CapstoneServer.KEY_REQUEST_METHOD, requestMethod.toString());
                headers.put("Accept-Encoding", "");
                return headers;
            }
        };
        volleyRequestQueue.add(request);
    }

    private void sendNsdInfoToPeer(@NonNull final NetworkPeerIdentifier destination, @NonNull final NetworkPeerIdentifier info) {
        final Response.Listener<JSONObject> successListener = jsonObject -> {
            final TypeToken<PayloadObject<NetworkPeerIdentifier>> type = new TypeToken<PayloadObject<NetworkPeerIdentifier>>(){};
            final PayloadObject<NetworkPeerIdentifier> payloadObject = fromJson(jsonObject.toString(), type);
            logv("Received peer NSD info payload: " + payloadObject);
            if (payloadObject != null) {
                final NetworkPeerIdentifier networkPeerIdentifier = payloadObject.getPayload();
                if (!nsdPeers.contains(networkPeerIdentifier)) {
                    nsdDiscoveryListener.onServiceFound(networkPeerIdentifier.getNsdServiceInfo());
                }
            } else {
                logv("Payload object was null - refusing to hand off to NSD Discovery Listener");
            }
        };
        final Response.ErrorListener errorListener = volleyError -> {
            logv("Volley POST info error: " + volleyError);
            nsdDiscoveryListener.onServiceLost(destination.getNsdServiceInfo());
        };
        final CapstoneServer.RequestMethod requestMethod = CapstoneServer.RequestMethod.IDENTIFY;

        postDataToPeer(destination, info, successListener, errorListener, requestMethod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendTokenToPeer(@NonNull final NetworkPeerIdentifier destination, @NonNull final Token token) {
        final Response.Listener<JSONObject> successListener = j -> {};
        final Response.ErrorListener errorListener = error -> {
            logv("Volley POST info error: " + error);
            logd("sendTokenToPeer --");
            logd("Destination: " + destination);
            logd("Token: " + token);
        };
        final CapstoneServer.RequestMethod requestMethod = CapstoneServer.RequestMethod.SEND_TOKEN;

        postDataToPeer(destination, token, successListener, errorListener, requestMethod);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveTokenInternal(@NonNull final Token token) {
        logv("receiveTokenInternal: " + token);
        this.tokenQueue.add(token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Token receiveToken() throws InterruptedException {
        logv("receiveToken");
        return this.tokenQueue.take();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEventToMonitor(@NonNull final Event event) {
        logv("sendEventToMonitor: " + event);
        this.incomingEventQueue.add(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveEventExternal(@NonNull final Event event) {
        logv("receiveEventExternal: " + event);
        this.incomingEventQueue.add(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Event receiveEvent() throws InterruptedException {
        logv("receiveEvent");
        return this.incomingEventQueue.take();
    }

    void addSelfIdentifiedPeer(@NonNull final NetworkPeerIdentifier networkPeerIdentifier) {
        if (networkPeerIdentifier == null
                || networkPeerIdentifier.getHost() == null
                || networkPeerIdentifier.getPort() == 0
                || networkPeerIdentifier.getServiceName() == null
                || networkPeerIdentifier.getServiceType() == null) {
            return;
        }
        new NsdResolveListener().onServiceResolved(networkPeerIdentifier.getNsdServiceInfo());
    }

    private void getDataFromPeer(@NonNull final NetworkPeerIdentifier peer,
                                     @NonNull final Response.Listener<JSONObject> successListener,
                                     @NonNull final Response.ErrorListener errorListener,
                                     @NonNull final CapstoneServer.RequestMethod requestMethod) {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(getStatusAsJson());
        } catch (final JSONException jse) {
            logd("Could not serialize local device info as JSON: " + jse.getLocalizedMessage());
            // FIXME: we don't actually use this data when we receive it, but we can probably find some actually useful metadata to include here
        }
        queueVolleyRequest(Request.Method.GET, peer, jsonObject, requestMethod, successListener, errorListener);
    }

    static String getUrlStringForPeer(@NonNull final NetworkPeerIdentifier peer) {
        return "http://" + peer.getHost().getHostAddress() + ":" + peer.getPort();
    }

    void requestUpdateFromPeer(@NonNull final NetworkPeerIdentifier peer,
                               @NonNull final PeerUpdateCallbackReceiver<DeviceInfo> callbackReceiver) {
        final Response.Listener<JSONObject> successListener = jsonObject -> {
            try {
                final TypeToken<PayloadObject<DeviceInfo>> type = new TypeToken<PayloadObject<DeviceInfo>>(){};
                final PayloadObject<DeviceInfo> payloadObject = fromJson(jsonObject.toString(), type);
                if (payloadObject != null) {
                    callbackReceiver.peerUpdate(payloadObject.getPayload());
                }
            } catch (final JsonSyntaxException jse) {
                logv("Bad JSON syntax in peer response, got: " + jsonObject);
            }
        };
        final Response.ErrorListener errorListener = error -> {
            logv("Volley GET update error: " + error);
            nsdDiscoveryListener.onServiceLost(peer.getNsdServiceInfo());
        };
        final CapstoneServer.RequestMethod requestMethod = CapstoneServer.RequestMethod.UPDATE;

        getDataFromPeer(peer, successListener, errorListener, requestMethod);
    }

    String getNsdServiceType() {
        return NSD_LOCATION_SERVICE_TYPE;
    }

    String getNsdServiceName() {
        return NSD_LOCATION_SERVICE_NAME;
    }

    private String getLocalNsdServiceName() {
        return getNsdServiceName() + "-" + Build.SERIAL;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public Set<NetworkPeerIdentifier> getKnownPeers() {
        return new HashSet<>(nsdPeers);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public Set<NetworkPeerIdentifier> getAllNetworkDevices() {
        final Set<NetworkPeerIdentifier> devices = getKnownPeers();
        devices.add(getLocalNetworkPeerIdentifier());
        return devices;
    }

    @Override
    public void registerMonitorStateListener(@NonNull final MonitorSatisfactionStateListener monitorSatisfactionStateListener) {
        monitorStateListeners.add(monitorSatisfactionStateListener);
    }

    @Override
    public void unregisterMonitorStateListener(@NonNull final MonitorSatisfactionStateListener monitorSatisfactionStateListener) {
        monitorStateListeners.remove(monitorSatisfactionStateListener);
    }

    @Override
    public void signalMonitorSatisfied() {
        for (final MonitorSatisfactionStateListener monitorSatisfactionStateListener : monitorStateListeners) {
            monitorSatisfactionStateListener.onMonitorSatisfied();
        }
    }

    @Override
    public void signalMonitorViolated() {
        for (final MonitorSatisfactionStateListener monitorSatisfactionStateListener : monitorStateListeners) {
            monitorSatisfactionStateListener.onMonitorViolated();
        }
    }

    private InetAddress findIpAddress() {
        int ip = wifiManager.getConnectionInfo().getIpAddress();

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ip = Integer.reverseBytes(ip);
        }

        final byte[] ipByteArray = BigInteger.valueOf(ip).toByteArray();

        try {
            return InetAddress.getByAddress(ipByteArray);
        } catch (final UnknownHostException ex) {
            logv("Unable to determine WiFi IP address!");
            Toast.makeText(this, "Unable to determine WiFi IP address!", Toast.LENGTH_LONG).show();
        }

        // we weren't able to determine our WiFi IP... try looking for any other available IPs, even
        // though they probably aren't going to actually work for us
        try {
            for (final NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.getName().toLowerCase().startsWith("wl") || networkInterface.getName().toLowerCase().startsWith("ap")) {
                    for (final InetAddress ipAddress : Collections.list(networkInterface.getInetAddresses())) {
                        if (!ipAddress.isLoopbackAddress()) {
                            logv("Identified local network interface " + networkInterface + " with IP address " + ipAddress);
                            return ipAddress;
                        }
                    }
                }
            }
        } catch (final SocketException se) {
            Log.e(LOG_TAG, "Error when attempting to determine local IP", se);
        }
        logv("Could not determine any valid local network interface!");
        return null;
    }

    private InetAddress getIpAddress() {
        if (ipAddress != null) {
            return ipAddress;
        }

        this.ipAddress = findIpAddress();
        return this.ipAddress;
    }

    private static void logv(@NonNull final String message) {
        Log.v(LOG_TAG, message);
    }

    private static void logd(@NonNull final String message) {
        Log.d(LOG_TAG, message);
    }

    public class CapstoneNetworkServiceBinder extends Binder {
        public CapstoneService getService() {
            return CapstoneService.this;
        }
    }

    private class CapstoneLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(final Location location) {
            lastLocation = location;
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
            }
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
        }

        @Override
        public void onProviderEnabled(final String provider) {
        }

        @Override
        public void onProviderDisabled(final String provider) {

        }
    }

    private class BarometerEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(final SensorEvent event) {
            barometerPressure = event.values[0];
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
            }
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private class GravitySensorEventListener implements SensorEventListener {

        public static final float ALPHA = 0.7f;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linearAcceleration[0] = event.values[0] - gravity[0];
            linearAcceleration[1] = event.values[1] - gravity[1];
            linearAcceleration[2] = event.values[2] - gravity[2];
            for (final SensorUpdateCallbackReceiver<DeviceInfo> sensorUpdateCallbackReceiver : sensorUpdateCallbackReceivers) {
                sensorUpdateCallbackReceiver.update(getStatus());
            }
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private class NsdResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(final NsdServiceInfo nsdServiceInfo, final int errorCode) {
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo nsdServiceInfo) {
            if (nsdServiceInfo.getHost().getHostAddress().equals(getIpAddress().getHostAddress())
                    || nsdServiceInfo.getServiceName().contains(getLocalNsdServiceName())) {
                return;
            }

            boolean isOnline = true;
            try {
                final InetSocketAddress inetSocketAddress = new InetSocketAddress(nsdServiceInfo.getHost(), nsdServiceInfo.getPort());
                final Socket socket = new Socket();
                socket.connect(inetSocketAddress, 1000);
            } catch (final IOException ioe) {
                isOnline = false;
            }
            if (isOnline) {
                final NetworkPeerIdentifier networkPeerIdentifier = NetworkPeerIdentifier.get(nsdServiceInfo);
                boolean newPeer = nsdPeers.add(networkPeerIdentifier);
                if (newPeer) {
                    sendHandshakeToPeer(networkPeerIdentifier);
                    updateNpiCallbackListeners();
                }
            }
        }
    }
}
