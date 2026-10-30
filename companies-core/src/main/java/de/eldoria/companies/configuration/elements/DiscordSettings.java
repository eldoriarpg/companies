/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.configuration.elements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.eldoria.companies.integrations.discord.WebhookEvent;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Discord webhook, the {@code discord} section of the config.
 * <p>
 * The url can come from the environment variable {@code COMPANIES_DISCORD_WEBHOOK} instead of the file. It is kept
 * apart from the file's value and never written, so it stays out of the file, the database that copies the file to the
 * other servers, and debug reports.
 */
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "CanBeFinal"})
public class DiscordSettings {
    private static final int MAX_PER_MINUTE = 60;
    private static final int MAX_QUEUE = 1000;

    private boolean enabled = false;
    private String url = "";

    @JsonIgnore
    @Overwrite(env = @Env("COMPANIES_DISCORD_WEBHOOK"))
    private String urlOverride;

    private List<WebhookEvent> events = new ArrayList<>(List.of(
            WebhookEvent.ORDER_CREATED,
            WebhookEvent.ORDER_COMPLETED,
            WebhookEvent.LEVEL_UP,
            WebhookEvent.OFFER_STARTED,
            WebhookEvent.GOAL_PROGRESS,
            WebhookEvent.CONTEST_RESULT,
            WebhookEvent.EVENT_STARTED,
            WebhookEvent.EVENT_ENDED));
    private String username = "Companies";
    private String avatarUrl = "";
    private int maxPerMinute = 20;
    private int queueSize = 100;

    public DiscordSettings() {}

    /**
     * Whether messages are posted to the webhook.
     *
     * @return true when enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * The webhook url: the environment variable {@code COMPANIES_DISCORD_WEBHOOK} where it is set, otherwise the file's.
     *
     * @return the url, empty when unset
     */
    public String url() {
        return present(urlOverride).orElseGet(() -> url == null ? "" : url.trim());
    }

    /**
     * @return whether the url comes from the environment variable {@code COMPANIES_DISCORD_WEBHOOK}
     */
    public boolean urlFromEnvironment() {
        return present(urlOverride).isPresent();
    }

    /**
     * Whether the event is posted.
     *
     * @param event the event
     * @return true when the event is in the list of events
     */
    public boolean posts(WebhookEvent event) {
        return events != null && events.contains(event);
    }

    /**
     * The name the messages are posted under.
     *
     * @return the user name, empty to keep the one set on the webhook
     */
    public Optional<String> username() {
        return blankToEmpty(username);
    }

    /**
     * The avatar the messages are posted with.
     *
     * @return the avatar url, empty to keep the one set on the webhook
     */
    public Optional<String> avatarUrl() {
        return blankToEmpty(avatarUrl);
    }

    /**
     * Requests this node sends per minute at most, between 1 and 60.
     *
     * @return requests per minute
     */
    public int maxPerMinute() {
        return Math.clamp(maxPerMinute, 1, MAX_PER_MINUTE);
    }

    /**
     * Messages waiting to be sent at most, between 1 and 1000. When full, the oldest one is dropped.
     *
     * @return queue capacity
     */
    public int queueSize() {
        return Math.clamp(queueSize, 1, MAX_QUEUE);
    }

    private static Optional<String> present(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<String> blankToEmpty(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
}
