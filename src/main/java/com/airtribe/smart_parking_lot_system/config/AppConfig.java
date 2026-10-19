package com.airtribe.smart_parking_lot_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Injected so tests can freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
