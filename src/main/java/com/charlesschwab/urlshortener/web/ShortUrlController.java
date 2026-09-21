package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.service.AnalyticsResult;
import com.charlesschwab.urlshortener.service.ShortUrlService;
import com.charlesschwab.urlshortener.web.dto.AnalyticsResponse;
import com.charlesschwab.urlshortener.web.dto.CreateShortUrlRequest;
import com.charlesschwab.urlshortener.web.dto.CreateShortUrlResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes short URL creation and analytics, per {@code docs/specs/api-specification.md}
 * sections 1 and 3. Redirect handling lives in {@link RedirectController} since it is
 * rooted at {@code /} rather than {@code /api/v1/short-urls}.
 */
@RestController
@RequestMapping("/api/v1/short-urls")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request,
            HttpServletRequest httpRequest) {
        ShortUrl shortUrl = shortUrlService.createShortUrl(request.url());

        String shortUrlLink = baseUrl(httpRequest) + "/" + shortUrl.getCode();
        CreateShortUrlResponse response = new CreateShortUrlResponse(
                shortUrl.getCode(), shortUrlLink, shortUrl.getOriginalUrl(), shortUrl.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}/analytics")
    public AnalyticsResponse analytics(@PathVariable String code) {
        AnalyticsResult result = shortUrlService.getAnalytics(code);
        return new AnalyticsResponse(
                result.code(), result.originalUrl(), result.totalClicks(),
                result.firstAccessedAt(), result.lastAccessedAt());
    }

    private String baseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean isDefaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        return isDefaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }
}
