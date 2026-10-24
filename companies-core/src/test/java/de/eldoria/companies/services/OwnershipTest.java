/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class OwnershipTest {
    private final CompanyMember owner = CompanyMember.of(1, UUID.randomUUID(), CompanyPermission.OWNER.mask());
    private final CompanyMember member = CompanyMember.of(1, UUID.randomUUID(), CompanyPermission.KICK.mask());

    @Test
    void ownerTransfersToAMember() {
        Assertions.assertEquals(Ownership.Transfer.ALLOWED, Ownership.transferCheck(owner, Optional.of(member)));
    }

    @Test
    void onlyTheOwnerTransfers() {
        Assertions.assertEquals(Ownership.Transfer.NOT_OWNER, Ownership.transferCheck(member, Optional.of(owner)));
    }

    @Test
    void targetHasToBeAMemberOfTheCompany() {
        var outsider = CompanyMember.of(2, UUID.randomUUID(), 0);

        Assertions.assertEquals(Ownership.Transfer.NOT_MEMBER, Ownership.transferCheck(owner, Optional.empty()));
        Assertions.assertEquals(Ownership.Transfer.NOT_MEMBER, Ownership.transferCheck(owner, Optional.of(outsider)));
    }

    @Test
    void ownerCanNotTransferToThemselves() {
        Assertions.assertEquals(Ownership.Transfer.SELF, Ownership.transferCheck(owner, Optional.of(owner)));
    }

    @Test
    void membersLeaveFreely() {
        Assertions.assertEquals(Ownership.Leave.ALLOWED, Ownership.leaveCheck(member, 2));
    }

    @Test
    void ownerWithMembersHasToTransferFirst() {
        Assertions.assertEquals(Ownership.Leave.TRANSFER_FIRST, Ownership.leaveCheck(owner, 2));
    }

    @Test
    void lastOwnerHasToDisband() {
        Assertions.assertEquals(Ownership.Leave.DISBAND_INSTEAD, Ownership.leaveCheck(owner, 1));
    }
}
