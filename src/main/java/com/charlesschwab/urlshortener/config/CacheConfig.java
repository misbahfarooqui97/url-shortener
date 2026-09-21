package com.charlesschwab.urlshortener.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring's cache abstraction. The cache manager itself is auto-configured by
 * Spring Boot from Caffeine (present on the classpath) and the
 * {@code spring.cache.*} properties in {@code application.properties}; this class only
 * turns caching on. See {@link com.charlesschwab.urlshortener.service.CachedShortUrlLookup}
 * for the one cache this application defines, and the caching ADR in
 * {@code docs/engineering-summary.md} for the rationale and trade-offs.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
