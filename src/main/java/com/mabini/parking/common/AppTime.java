package com.mabini.parking.common;

import java.time.ZoneId;

/** The one place that decides which time zone the whole system uses. */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Manila");

    private AppTime() {
    }
}
