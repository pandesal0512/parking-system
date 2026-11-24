package com.mabini.parking;

import com.mabini.parking.common.AppTime;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ParkingApplication {

    public static void main(String[] args) {
        // Same time zone on every machine, so reservation times mean the same thing everywhere.
        TimeZone.setDefault(TimeZone.getTimeZone(AppTime.ZONE));
        SpringApplication.run(ParkingApplication.class, args);
    }
}
