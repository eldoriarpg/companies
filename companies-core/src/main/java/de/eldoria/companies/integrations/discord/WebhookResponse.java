/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The answer to a webhook request.
 *
 * @param status  HTTP status
 * @param body    response body, empty when there is none
 * @param headers response headers with lower case names, one value each
 */
public record WebhookResponse(int status, String body, Map<String, String> headers) {
    public WebhookResponse {
        headers = headers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a));
    }

    /**
     * A header of the response.
     *
     * @param name header name, in any case
     * @return the value, empty when the header is missing
     */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }
}
