package com.mabini.parking.device.dto;

/** One piece of gate hardware: when we last heard from it, and whether that counts as online. */
public record DeviceStatus(String deviceId, long secondsAgo, boolean online) {
}
