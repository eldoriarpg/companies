/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An open invite of a player into a company.
 *
 * @param id          id of the invite
 * @param company     id of the inviting company
 * @param companyName name of the inviting company
 * @param target      invited player
 * @param inviter     member who sent the invite
 * @param created     when the invite was sent or last renewed
 * @param expires     when the invite expires
 */
public record Invite(
        int id,
        int company,
        String companyName,
        UUID target,
        UUID inviter,
        LocalDateTime created,
        LocalDateTime expires) {}
