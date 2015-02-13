package ca.mcmaster.capstone.networking.structures;

import android.location.Location;

import lombok.Value;
import lombok.experimental.Builder;

@Value
public class DeviceLocation {
    double latitude, longitude, altitude, barometerPressure, speed;
    float bearing, accuracy;
    float[] gravity, linearAccleration;

    @Builder
    public DeviceLocation(final Location location, final double barometerPressure, final float[] gravity, final float[] linearAccleration) {
        final double latitude, longitude, altitude, speed;
        final float bearing, accuracy;
        if (location == null) {
            latitude = 0;
            longitude = 0;
            altitude = 0;
            bearing = 0;
            accuracy = 0;
            speed = 0;
        } else {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
            altitude = location.getAltitude();
            bearing = location.getBearing();
            accuracy = location.getAccuracy();
            speed = location.getSpeed();
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.barometerPressure = barometerPressure;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.speed = speed;
        this.gravity = gravity;
        this.linearAccleration = linearAccleration;
    }
}
