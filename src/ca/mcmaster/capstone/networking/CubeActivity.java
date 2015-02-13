package ca.mcmaster.capstone.networking;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import ca.mcmaster.capstone.R;
import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;
import ca.mcmaster.capstone.monitoralgorithm.Event;
import ca.mcmaster.capstone.monitoralgorithm.Valuation;
import ca.mcmaster.capstone.monitoralgorithm.VectorClock;
import ca.mcmaster.capstone.networking.structures.NetworkPeerIdentifier;
import ca.mcmaster.capstone.networking.util.MonitorSatisfactionStateListener;
import lombok.NonNull;

public class CubeActivity extends Activity implements MonitorSatisfactionStateListener {

    public static final String LOG_TAG = "CubeActivity";

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;
    private int eventCounter = 0;
    private boolean isFlat = true;
    private double flat = 1.0;
    private NetworkPeerIdentifier NSD;
    private String variableName;

    private final float[] gravity = new float[3];
    private OpenGLRenderer renderer;
    private final LocationServiceConnection serviceConnection = new LocationServiceConnection();
    private final InitializerServiceConnection initializerServiceConnection = new InitializerServiceConnection();

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cube);

        Intent serviceIntent = new Intent(this, CapstoneService.class);
        getApplicationContext().bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);

        Intent initializerServiceIntent = new Intent(this, Initializer.class);
        getApplicationContext().bindService(initializerServiceIntent, initializerServiceConnection, BIND_AUTO_CREATE);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        setupGravitySensorService();

        LinearLayout gl = (LinearLayout) findViewById(R.id.gl_layout);

        final GLSurfaceView view = new GLSurfaceView(this);
        renderer = new OpenGLRenderer();
        view.setRenderer(renderer);
        gl.addView(view);
    }

    public void setLabelText(@NonNull final String satisfactionState) {
        runOnUiThread(() -> {
            final TextView globalText = (TextView) findViewById(R.id.cube_global_info);

            final StringBuilder text = new StringBuilder();
            text.append("Virtual ID: ").append(variableName).append("\n");
            text.append("localID: ").append(NSD.toString()).append("\n");
            text.append("Satisfaction: ").append(satisfactionState);

            globalText.setText(text.toString());
        });
    }

    private void setupGravitySensorService() {
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mSensorEventListener = new GravitySensorEventListener();
        mSensorManager.registerListener(mSensorEventListener, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(mSensorEventListener);
        getApplicationContext().unbindService(serviceConnection);
        getApplicationContext().unbindService(initializerServiceConnection);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMonitorSatisfied() {
        Log.d("MonitorState", "Monitor is satisfied !!!");
        setLabelText("satisfied");
    }

    @Override
    public void onMonitorViolated() {
        Log.d("MonitorState", "Monitor is violated !!!");
        setLabelText("violated");
    }


    private class GravitySensorEventListener implements SensorEventListener {

        public static final float ALPHA = 0.7f;

        @Override
        public void onSensorChanged(final SensorEvent event) {
            gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * event.values[0];
            gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * event.values[1];
            gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * event.values[2];

            final float[] z_axis = new float[] {0, 0, 1};

            float[] target_dir = normalise( gravity );
            float rot_angle = (float) Math.acos( dot_product(target_dir,z_axis) );

            if (Math.abs(rot_angle) > Double.MIN_VALUE) {
                renderer.axis = normalise(cross_product(target_dir, z_axis));
                renderer.angle = rot_angle;
            }

            boolean previouslyFlat = isFlat;

            isFlat = checkCondition(gravity);

            if (isFlat) {
                renderer.setColor(new float[] {
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f,
                        0f,  1.0f,  0f,  1.0f
                });
            } else {
                renderer.setColor(new float[] {
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f,
                        1.0f,  1.0f,  1.0f,  1.0f
                });
            }
            if (previouslyFlat != isFlat) {
                if (flat == 0.0) {
                    flat = 1.0;
                } else {
                    flat = 0.0;
                }
                sendEvent(flat);
            }
        }

        public void sendEvent(double value) {
            waitForNetworkLayer();
            final Valuation valuation = new Valuation(new HashMap<String, Double>() {{
                put(CubeActivity.this.variableName, value);
            }});
            ++eventCounter;
            final Event e = new Event(eventCounter, NSD, Event.EventType.INTERNAL, valuation,
                    new VectorClock(new HashMap<NetworkPeerIdentifier, Integer>() {{
                        put(serviceConnection.getService().getLocalNetworkPeerIdentifier(), eventCounter);
                        for (final NetworkPeerIdentifier peer : serviceConnection.getService().getKnownPeers()) {
                            put(peer, 0);
                        }
                    }}));
            if (eventCounter != 0) {
                Toast.makeText(CubeActivity.this, "Event has left the building", Toast.LENGTH_SHORT).show();
                serviceConnection.getService().sendEventToMonitor(e);
            }
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

        public boolean checkCondition(float[] gravity) {
            float dot = dot_product(gravity, new float[] {0, 0, 1});

            return dot < 0.5;
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
        }
    }

    private float[] normalise(float[] gravity) {
        float sum = 0;
        for (float f : gravity) {
            sum += f*f;
        }
        float magnitude = (float) Math.sqrt(sum);
        float[] result = new float[gravity.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = gravity[i] / magnitude;
        }
        return result;
    }

    public float[] cross_product(float[] v1, float[] v2){
        float[] cross =  new float[3];

        cross[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        cross[1] = (v1[0] * v2[2]) - (v1[2] * v2[0]);
        cross[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);

        return cross;
    }

    public float dot_product(float[] v1, float[] v2){
        float dot = 0;

        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensionality mismatch");
        }

        for (int i = 0; i < v1.length; ++i) {
            dot += v1[i] * v2[i];
        }

        return dot;
    }


    public class LocationServiceConnection implements ServiceConnection {

        private CapstoneService service;

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            Toast.makeText(CubeActivity.this, "Service connected", Toast.LENGTH_LONG).show();

            this.service = ((CapstoneService.CapstoneNetworkServiceBinder) service).getService();
            this.service.registerMonitorStateListener(CubeActivity.this);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            Toast.makeText(CubeActivity.this, "Service disconnected", Toast.LENGTH_LONG).show();
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

            CubeActivity.this.NSD = initializer.getLocalPID();
            //FIXME: this is for testing out simple test case. More work is needed for more complex variableGlobalText arrangements
            for (Map.Entry<String, NetworkPeerIdentifier> virtualID : initializer.getVirtualIdentifiers().entrySet()) {
                if (virtualID.getValue() == NSD) {
                    CubeActivity.this.variableName = virtualID.getKey();
                    break;
                }
            }
            Log.d("cube", "I am: " + CubeActivity.this.variableName);
            setLabelText("indeterminate");
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
