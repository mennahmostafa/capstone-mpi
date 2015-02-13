package ca.mcmaster.capstone.networking.structures;

import android.os.Build;

import lombok.Getter;
import lombok.Value;

import static ca.mcmaster.capstone.networking.util.JsonUtil.asJson;

@Value
public class DeviceInfo {
    @Getter private static final String serial = Build.SERIAL;
    String ip;
    int port;
    DeviceLocation location;

    @Override
    public String toString() {
        return asJson(this);
    }
}
