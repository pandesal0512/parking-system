package com.mabini.parking.config;

import com.mabini.parking.common.AppTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Every class that needs "the current time" must inject this Clock instead of calling
 * LocalDateTime.now() directly. That lets tests and the demo pretend it is another time.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(AppTime.ZONE);
    }
}
