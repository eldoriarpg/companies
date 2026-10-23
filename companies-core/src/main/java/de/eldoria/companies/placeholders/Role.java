/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import java.util.Locale;

/**
 * The role of a player in the company, as the placeholders name it.
 */
public enum Role {
    /**
     * The owner of the company.
     */
    OWNER,
    /**
     * Any other member.
     */
    MEMBER;

    /**
     * @return locale key of the role's name
     */
    public String translationKey() {
        return "placeholder.role." + name().toLowerCase(Locale.ROOT);
    }
}
