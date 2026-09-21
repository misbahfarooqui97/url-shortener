package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.exception.InvalidUrlException;
import com.charlesschwab.urlshortener.exception.ShortCodeGenerationException;
import com.charlesschwab.urlshortener.service.AnalyticsResult;
import com.charlesschwab.urlshortener.service.ShortUrlService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@link ShortUrlController} and the shared
 * {@link GlobalExceptionHandler}, covering the create and analytics endpoints against
 * {@code docs/specs/api-specification.md} sections 1 and 3, with the service layer
 * mocked out.
 */
@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        Instant createdAt = Instant.parse("2026-09-20T15:30:00Z");
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com/products/123",
                "https://example.com/products/123", createdAt);
        when(shortUrlService.createShortUrl("https://example.com/products/123")).thenReturn(shortUrl);

        mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new com.charlesschwab.urlshortener.web.dto.CreateShortUrlRequest(
                                        "https://example.com/products/123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("aB91xY1"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/products/123"))
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.endsWith("/aB91xY1")))
                .andExpect(jsonPath("$.createdAt").value("2026-09-20T15:30:00Z"));
    }

    @Test
    void createReturns400ForBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content("{\"url\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/short-urls"));
    }

    @Test
    void createReturns400WhenServiceRejectsUrl() throws Exception {
        when(shortUrlService.createShortUrl(anyString()))
                .thenThrow(new InvalidUrlException("url must be an absolute http or https URL"));

        mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content("{\"url\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("url must be an absolute http or https URL"));
    }

    @Test
    void createReturns500WhenCodeGenerationExhausted() throws Exception {
        when(shortUrlService.createShortUrl(anyString())).thenThrow(new ShortCodeGenerationException());

        mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content("{\"url\": \"https://example.com\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void analyticsReturns200WithAggregatedData() throws Exception {
        AnalyticsResult result = new AnalyticsResult(
                "aB91xY1", "https://example.com/products/123", 42L,
                Instant.parse("2026-09-20T15:31:10Z"), Instant.parse("2026-09-21T09:02:44Z"));
        when(shortUrlService.getAnalytics("aB91xY1")).thenReturn(result);

        mockMvc.perform(get("/api/v1/short-urls/aB91xY1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("aB91xY1"))
                .andExpect(jsonPath("$.totalClicks").value(42))
                .andExpect(jsonPath("$.firstAccessedAt").value("2026-09-20T15:31:10Z"))
                .andExpect(jsonPath("$.lastAccessedAt").value("2026-09-21T09:02:44Z"));
    }

    @Test
    void analyticsReturnsZeroClicksAndNullTimestampsForNeverAccessedCode() throws Exception {
        AnalyticsResult result = new AnalyticsResult("aB91xY1", "https://example.com", 0L, null, null);
        when(shortUrlService.getAnalytics("aB91xY1")).thenReturn(result);

        mockMvc.perform(get("/api/v1/short-urls/aB91xY1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.firstAccessedAt").value(nullValue()))
                .andExpect(jsonPath("$.lastAccessedAt").value(nullValue()));
    }

    @Test
    void analyticsReturns404ForUnknownCode() throws Exception {
        when(shortUrlService.getAnalytics("missing"))
                .thenThrow(new com.charlesschwab.urlshortener.exception.ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/short-urls/missing/analytics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
