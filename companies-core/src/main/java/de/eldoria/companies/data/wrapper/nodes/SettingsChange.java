/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.nodes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A revision of a shared file as its history shows it.
 *
 * @param revision the revision
 * @param actor    who stored it, such as an admin's name, {@code startup} or {@code sync}
 * @param node     the node that stored it
 * @param changed  when, in UTC
 */
public record SettingsChange(int revision, String actor, UUID node, LocalDateTime changed) {}
