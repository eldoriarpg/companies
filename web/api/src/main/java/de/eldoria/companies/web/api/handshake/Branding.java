/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import org.jspecify.annotations.Nullable;

/**
 * How a server brands its dashboard. The gateway shows the name on the login page, in the header and in the tab title,
 * and the icon next to it.
 * <p>
 * The gateway keeps icons by their hash. A node may leave out {@code icon} and send only {@code iconHash} when the
 * gateway already has that icon: it says so with {@link Welcome#iconCached()}, and when it does not, the node sends
 * the icon with {@link BrandingUpdate}.
 *
 * @param name the server's name as plain text, at most {@value #MAX_NAME_LENGTH} characters
 * @param icon the icon as a base64 encoded PNG of at most {@value #MAX_ICON_SIZE}×{@value #MAX_ICON_SIZE} pixels and
 * at most {@value #MAX_ICON_ENCODED_BYTES} bytes encoded; none when the gateway has it or the server has no icon
 * @param iconHash the SHA-256 of the PNG bytes as lower-case hex; none when the server has no icon
 */
public record Branding(
        String name, @Nullable String icon, @Nullable String iconHash) {
    /**
     * The most characters of {@link #name()}.
     */
    public static final int MAX_NAME_LENGTH = 64;

    /**
     * The most pixels of the icon's width and height.
     */
    public static final int MAX_ICON_SIZE = 256;

    /**
     * The most bytes of {@link #icon()} as base64 text.
     */
    public static final int MAX_ICON_ENCODED_BYTES = 65_536;
}
