package ca.mcmaster.capstone.monitoralgorithm;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import ca.mcmaster.capstone.initializer.Initializer;
import ca.mcmaster.capstone.initializer.InitializerBinder;

public class InitializerServiceConnection implements ServiceConnection{
    private Initializer initializer;

    @Override
    public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
        this.initializer = ((InitializerBinder) iBinder).getInitializer();
    }

    @Override
    public void onServiceDisconnected(final ComponentName componentName) {
        this.initializer = null;
    }

    public Initializer getInitializer() {
        return this.initializer;
    }
}
