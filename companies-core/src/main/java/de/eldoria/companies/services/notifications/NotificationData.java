/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class NotificationData {
    @Expose(deserialize = false)
    private static final Gson GSON = new GsonBuilder().create();
    /**
     * The localized message.
     * This may be a {@link net.kyori.adventure.text.Component} string representation.
     */
    private final String message;

    public NotificationData(String message) {
        this.message = message;
    }

    public static NotificationData fromJson(String json) {
        return GSON.fromJson(json, NotificationData.class);
    }

    public String message() {
        return message;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
