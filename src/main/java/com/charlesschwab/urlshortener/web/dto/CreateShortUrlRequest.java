package com.charlesschwab.urlshortener.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/short-urls}. Bean Validation here is a first,
 * cheap filter for the obviously-missing case; the authoritative validation rules
 * (scheme, well-formedness) live in {@link com.charlesschwab.urlshortener.service.UrlValidator}
 * per FR-2, so both layers must be kept in sync with the API specification.
 */
public record CreateShortUrlRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url exceeds maximum length of 2048 characters")
        String url) {
}
