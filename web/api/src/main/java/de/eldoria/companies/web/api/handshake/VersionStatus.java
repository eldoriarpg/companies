/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.api.handshake;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * The state of the plugin version in the gateway's version policy. The dates and texts are set for {@link
 * VersionState#DEPRECATED} and {@link VersionState#DISCONTINUED} only.
 *
 * @param status supported, deprecated or discontinued
 * @param since when the version was discontinued
 * @param discontinuedAt when a deprecated version will be discontinued
 * @param updateTo the version to update to
 * @param message a plain sentence explaining the problem and what to do, shown to admins and players as is
 */
public record VersionStatus(
        VersionState status,
        @Nullable LocalDate since,
        @Nullable LocalDate discontinuedAt,
        @Nullable String updateTo,
        @Nullable String message) {}
