package ca.mcmaster.capstone.monitoralgorithm;

import android.os.Binder;

public class MonitorBinder extends Binder {
    private final Monitor monitor;

    public MonitorBinder(final Monitor monitor) {
        this.monitor = monitor;
    }

    public Monitor getMonitor() {
        return monitor;
    }
}
