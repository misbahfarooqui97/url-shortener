package com.charlesschwab.urlshortener.web;

import com.charlesschwab.urlshortener.domain.ShortUrl;
import com.charlesschwab.urlshortener.service.ShortUrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes redirect resolution, per {@code docs/specs/api-specification.md} section 2.
 * Rooted at {@code /} (not {@code /api/v1/...}) since a short link must be as short as
 * possible.
 */
@RestController
public class RedirectController {

    private final ShortUrlService shortUrlService;

    public RedirectController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        ShortUrl shortUrl = shortUrlService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortUrl.getOriginalUrl())
                .build();
    }
}
