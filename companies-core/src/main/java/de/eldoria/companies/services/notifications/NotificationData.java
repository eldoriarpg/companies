/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.notifications;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

/**
 * @param message The localized message.
 *                This may be a {@link net.kyori.adventure.text.Component} string representation.
 */
public record NotificationData(String message) {
    @Expose(deserialize = false)
    private static final Gson GSON = new GsonBuilder().create();

    public static NotificationData fromJson(String json) {
        return GSON.fromJson(json, NotificationData.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
