/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

import java.time.Instant;
import java.util.List;

/**
 * One Discord embed, cut to Discord's limits on creation.
 *
 * @param title       title, at most 256 characters
 * @param description description, at most 4096 characters
 * @param color       side color as RGB
 * @param fields      fields, at most 25
 * @param timestamp   time shown in the footer
 */
public record Embed(String title, String description, int color, List<Field> fields, Instant timestamp) {
    static final int TITLE_LIMIT = 256;
    static final int DESCRIPTION_LIMIT = 4096;
    static final int FIELD_LIMIT = 25;
    static final int FIELD_NAME_LIMIT = 256;
    static final int FIELD_VALUE_LIMIT = 1024;

    public Embed {
        title = DiscordText.cut(title, TITLE_LIMIT);
        description = DiscordText.cut(description, DESCRIPTION_LIMIT);
        fields = List.copyOf(fields.subList(0, Math.min(fields.size(), FIELD_LIMIT)));
    }

    /**
     * The characters Discord counts against its limit of 6000 per message.
     *
     * @return counted characters
     */
    public int length() {
        return title.length()
                + description.length()
                + fields.stream()
                        .mapToInt(field -> field.name().length() + field.value().length())
                        .sum();
    }

    /**
     * One field of an embed.
     *
     * @param name   name, at most 256 characters
     * @param value  value, at most 1024 characters
     * @param inline whether the field is shown next to others
     */
    public record Field(String name, String value, boolean inline) {
        public Field {
            name = DiscordText.cut(name, FIELD_NAME_LIMIT);
            value = DiscordText.cut(value, FIELD_VALUE_LIMIT);
        }
    }
}
