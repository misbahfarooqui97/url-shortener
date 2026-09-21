package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.exception.ShortUrlNotFoundException;
import com.charlesschwab.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@link RedirectController}, covering
 * {@code docs/specs/api-specification.md} section 2 with the service layer mocked out.
 */
@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    void redirectsWithFoundStatusAndLocationHeader() throws Exception {
        ShortUrl shortUrl = new ShortUrl("aB91xY1", "https://example.com/products/123",
                "https://example.com/products/123", Instant.now());
        when(shortUrlService.resolve("aB91xY1")).thenReturn(shortUrl);

        mockMvc.perform(get("/aB91xY1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/products/123"));
    }

    @Test
    void returns404ForUnknownCode() throws Exception {
        when(shortUrlService.resolve("missing")).thenThrow(new ShortUrlNotFoundException("missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/missing"));
    }
}
