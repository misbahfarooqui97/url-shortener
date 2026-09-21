package com.charlesschwab.urlshortener.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes the system clock as an injectable bean so services can be unit tested with a
 * fixed or controllable {@link Clock} instead of calling {@code Instant.now()} directly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
