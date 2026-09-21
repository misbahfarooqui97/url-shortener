package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.web.dto.CreateShortUrlRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test exercising the full stack (real Spring MVC dispatch, real H2
 * datastore, no mocks) through the exact flow a client would use: create a short URL,
 * follow the redirect, then read back analytics reflecting that redirect. This is the
 * acceptance criterion from {@code docs/specs/requirements-specification.md} section 7.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createFollowRedirectThenReadAnalytics() throws Exception {
        String originalUrl = "https://example.com/e2e/" + System.nanoTime();

        String createBody = mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(originalUrl))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String code = objectMapper.readTree(createBody).get("code").asText();

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", originalUrl));

        mockMvc.perform(get("/api/v1/short-urls/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$.originalUrl").value(originalUrl));
    }

    @Test
    void resubmittingSameUrlReusesExistingCode() throws Exception {
        String originalUrl = "https://example.com/dupe/" + System.nanoTime();

        String firstBody = mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(originalUrl))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondBody = mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest(originalUrl))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstCode = objectMapper.readTree(firstBody).get("code").asText();
        String secondCode = objectMapper.readTree(secondBody).get("code").asText();

        org.assertj.core.api.Assertions.assertThat(firstCode).isEqualTo(secondCode);
    }

    @Test
    void unknownCodeReturns404OnRedirectAndAnalytics() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/short-urls/does-not-exist/analytics"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidUrlOnCreateReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/short-urls")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateShortUrlRequest("javascript:alert(1)"))))
                .andExpect(status().isBadRequest());
    }
}
