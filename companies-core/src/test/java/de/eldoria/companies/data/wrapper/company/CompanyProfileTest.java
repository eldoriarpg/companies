/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class CompanyProfileTest {
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final CompanyProfile company = new CompanyProfile(1, "company", LocalDateTime.now(), 1,
            List.of(CompanyMember.of(1, member, 0L)));

    @Test
    void membersAreRecognised() {
        Assertions.assertTrue(company.isMember(member));
        Assertions.assertFalse(company.isMember(stranger));
    }

    @Test
    void ordersOfMembersAreRefusedUnlessAllowed() {
        Assertions.assertFalse(company.mayAcceptOrderOf(member, false));
        Assertions.assertTrue(company.mayAcceptOrderOf(member, true));
    }

    @Test
    void ordersOfOtherPlayersAreAccepted() {
        Assertions.assertTrue(company.mayAcceptOrderOf(stranger, false));
        Assertions.assertTrue(company.mayAcceptOrderOf(stranger, true));
    }
}
