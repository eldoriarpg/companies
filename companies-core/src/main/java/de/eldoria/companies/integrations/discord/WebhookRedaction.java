/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.util.regex.Pattern;

/**
 * Hides webhook secrets in text that leaves the plugin, such as log lines and debug reports.
 * <p>
 * A webhook url carries its secret in the path, {@code /webhooks/<id>/<token>}, so the token is masked wherever such a
 * path appears. A relay with another path is only known by its full url, which is masked as a whole.
 */
public final class WebhookRedaction {
    /**
     * Finds the token of a webhook path. Group 1 is everything before the token.
     */
    public static final Pattern TOKEN_PATH = Pattern.compile("(?i)(/webhooks/[^/\\s\"']+/)[^\\s/?#\"']+");

    /**
     * Masks the token of a webhook path.
     */
    public static final String TOKEN_REPLACEMENT = "$1*****";

    private static final String MASK = "*****";

    private final String url;

    /**
     * @param url the resolved webhook url, empty when none is known
     */
    public WebhookRedaction(String url) {
        this.url = url;
    }

    /**
     * Masks webhook tokens and the configured url in the text.
     *
     * @param text text that may contain the url
     * @return the text without the secret
     */
    public String redact(String text) {
        var masked = url.isEmpty() ? text : text.replace(url, MASK);
        return TOKEN_PATH.matcher(masked).replaceAll(TOKEN_REPLACEMENT);
    }
}
