/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import de.eldoria.eldoutilities.localization.Replacement;

public class NotificationData {
    @Expose(deserialize = false)
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(NotificationData.class, new NotificationDataDeserializer())
            .create();
    private final MessageType type;
    /**
     * The unlocalized message.
     * This may be a {@link net.kyori.adventure.text.Component} string representation.
     */
    private final String message;
    /**
     * An array of {@link de.eldoria.eldoutilities.localization.Replacement}s.
     */
    private final Replacement[] replacements;

    public NotificationData(MessageType type, String message, Replacement... replacements) {
        this.type = type;
        this.message = message;
        this.replacements = replacements;
    }

    public static NotificationData fromJson(String json) {
        return GSON.fromJson(json, NotificationData.class);
    }

    public String message() {
        return message;
    }

    public Replacement[] replacements() {
        return replacements;
    }

    public MessageType type() {
        return type;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
