package com.charlesschwab.urlshortener.service;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for {@link ShortUrlService#createShortUrl(String)}: verifies that
 * simultaneous requests to shorten different URLs all succeed with unique codes, and no
 * race condition causes code collisions or silent failures (NFR-1 requirement).
 */
@SpringBootTest
@ActiveProfiles("test")
class ShortUrlServiceConcurrencyTest {

    @Autowired
    private ShortUrlService shortUrlService;

    /**
     * Launches 50 threads, each creating a short URL for a unique original URL.
     * Asserts that all 50 short codes are unique (no collisions) and all requests complete
     * successfully. Uses a {@link CountDownLatch} to coordinate test thread startup for
     * maximum overlap (stricter concurrency test than sequential request timing).
     */
    @Test
    void simultaneousCreationProducesUniqueCodesWithoutCollisions() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<String> codes = new HashSet<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String url = "https://example.com/product/" + index;
                    ShortUrl shortUrl = shortUrlService.createShortUrl(url);
                    synchronized (codes) {
                        codes.add(shortUrl.getCode());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isZero();
        assertThat(codes).hasSize(threadCount);
    }

    /**
     * Launches 10 threads simultaneously attempting to create a short URL for the same
     * original URL. By the ADR-001 duplicate-submission policy, only the first thread
     * should create a new row; the remaining 9 should reuse the same code. Asserts that
     * all 10 threads receive the same code and no code-generation errors occur.
     */
    @Test
    void duplicateSubmissionsUnderConcurrencyReuseExistingCode() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Set<String> codes = new HashSet<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        String sharedUrl = "https://example.com/shared-resource";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ShortUrl shortUrl = shortUrlService.createShortUrl(sharedUrl);
                    synchronized (codes) {
                        codes.add(shortUrl.getCode());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isZero();
        assertThat(codes).hasSize(1);
    }
}
