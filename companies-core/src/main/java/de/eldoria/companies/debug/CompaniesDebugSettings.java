/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.debug;

import de.eldoria.companies.integrations.discord.WebhookRedaction;
import de.eldoria.eldoutilities.debug.DebugSettings;
import de.eldoria.eldoutilities.debug.Filter;

import java.util.regex.Pattern;

/**
 * The debug report settings of the plugin.
 * <p>
 * They add two filters to the defaults, which already hide passwords, tokens and credentials in urls. A Discord
 * webhook url keeps its secret in the path, so the token of every webhook path is masked, and the {@code url} of the
 * {@code discord} section is masked as a whole in case it points at a relay with another path. An {@code env:}
 * reference stays visible, since it names a variable and holds no secret.
 */
public final class CompaniesDebugSettings {
    /**
     * Masks the webhook token in any text.
     */
    public static final Filter WEBHOOK_TOKEN =
            new Filter(WebhookRedaction.TOKEN_PATH, WebhookRedaction.TOKEN_REPLACEMENT);

    /**
     * Masks the {@code url} of the {@code discord} section in YAML.
     */
    public static final Filter DISCORD_URL = new Filter(
            Pattern.compile("(?m)^(discord:[ \\t]*\\R(?:(?:[ \\t]+.*)?\\R)*?[ \\t]+url:[ \\t]*)"
                    + "(?![\"']?env:)(?![\"']{2}[ \\t]*$)\\S[^\\r\\n]*$"),
            "$1*****");

    private CompaniesDebugSettings() {}

    /**
     * The settings debug reports of this plugin are created with.
     *
     * @return default filters plus the webhook filters
     */
    public static DebugSettings create() {
        return DebugSettings.builder()
                .withDefaultFilter()
                .withFilter(WEBHOOK_TOKEN, DISCORD_URL)
                .build();
    }
}
