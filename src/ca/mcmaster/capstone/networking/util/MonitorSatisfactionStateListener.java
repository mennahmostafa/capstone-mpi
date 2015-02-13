package ca.mcmaster.capstone.networking.util;

public interface MonitorSatisfactionStateListener {
    void onMonitorSatisfied();
    void onMonitorViolated();
}
