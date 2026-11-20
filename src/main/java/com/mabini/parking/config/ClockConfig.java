package com.mabini.parking.config;

import com.mabini.parking.common.AppTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Every class that needs "the current time" must inject this Clock instead of calling
 * LocalDateTime.now() directly. That lets tests and the demo pretend it is another time
 * (the demo page moves the OffsetClock forward; see DemoController).
 */
@Configuration
public class ClockConfig {

    @Bean
    public OffsetClock clock() {
        return new OffsetClock(Clock.system(AppTime.ZONE));
    }
}
