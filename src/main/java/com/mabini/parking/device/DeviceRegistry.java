package com.mabini.parking.device;

import com.mabini.parking.config.AppProperties;
import com.mabini.parking.device.dto.DeviceStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each gate device was last heard from, so the dashboard can show "online" or "offline".
 * Kept in memory only: after a restart it fills up again as the devices send their next heartbeat.
 * It uses the REAL clock on purpose (the demo clock must not make real hardware look offline).
 */
@Component
public class DeviceRegistry {

    private static final int MAX_DEVICES = 50; // a runaway client cannot grow this without limit

    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    private final AppProperties props;

    public DeviceRegistry(AppProperties props) {
        this.props = props;
    }

    public void touch(String deviceId) {
        if (lastSeen.containsKey(deviceId) || lastSeen.size() < MAX_DEVICES) {
            lastSeen.put(deviceId, Instant.now());
        }
    }

    public List<DeviceStatus> list() {
        Instant now = Instant.now();
        long limit = props.device().onlineSeconds();
        return lastSeen.entrySet().stream()
                .map(e -> {
                    long ago = Math.max(0, Duration.between(e.getValue(), now).getSeconds());
                    return new DeviceStatus(e.getKey(), ago, ago <= limit);
                })
                .sorted(Comparator.comparing(DeviceStatus::deviceId))
                .toList();
    }
}
