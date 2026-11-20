package com.mabini.parking.demo;

import com.mabini.parking.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;

/** The server's current time, so the pages follow the server clock (matters in demo time mode). */
@RestController
@RequestMapping("/api/time")
public class TimeController {

    public record ServerTime(LocalDateTime now, boolean demoTimeControl) {
    }

    private final Clock clock;
    private final AppProperties props;

    public TimeController(Clock clock, AppProperties props) {
        this.clock = clock;
        this.props = props;
    }

    @GetMapping
    public ServerTime now() {
        return new ServerTime(LocalDateTime.now(clock), props.demo().timeControl());
    }
}
