/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.JoinMode;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of a company profile its members can edit.
 *
 * @param description free text, stored as entered
 * @param tag         upper case tag of letters and digits
 * @param color       name of a named text colour or a {@code #rrggbb} value
 * @param joinMode    how players become members
 */
public record CompanyDetails(
        @Nullable String description,
        @Nullable String tag,
        @Nullable String color,
        JoinMode joinMode) {
    /**
     * Details of a company without description, tag and colour that only accepts invited players.
     */
    public static final CompanyDetails NONE = new CompanyDetails(null, null, null, JoinMode.INVITE_ONLY);

    /**
     * Copies these details with another description.
     *
     * @param description new description, or null to remove it
     * @return changed copy
     */
    public CompanyDetails withDescription(@Nullable String description) {
        return new CompanyDetails(description, tag, color, joinMode);
    }

    /**
     * Copies these details with another tag.
     *
     * @param tag new tag, or null to remove it
     * @return changed copy
     */
    public CompanyDetails withTag(@Nullable String tag) {
        return new CompanyDetails(description, tag, color, joinMode);
    }

    /**
     * Copies these details with another colour.
     *
     * @param color new colour, or null to remove it
     * @return changed copy
     */
    public CompanyDetails withColor(@Nullable String color) {
        return new CompanyDetails(description, tag, color, joinMode);
    }

    /**
     * Copies these details with another join mode.
     *
     * @param joinMode new join mode
     * @return changed copy
     */
    public CompanyDetails withJoinMode(JoinMode joinMode) {
        return new CompanyDetails(description, tag, color, joinMode);
    }
}
