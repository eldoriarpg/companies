/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KickRuleTest {
    private static CompanyMember member(CompanyPermission... permissions) {
        return CompanyMember.of(1, UUID.randomUUID(), CompanyPermission.composePermissions(permissions));
    }

    @Test
    void ownerKicksAMemberWithKick() {
        assertTrue(MemberService.mayKick(member(CompanyPermission.OWNER), member(CompanyPermission.KICK)));
    }

    @Test
    void ownerKicksAMemberWithoutPermissions() {
        assertTrue(MemberService.mayKick(member(CompanyPermission.OWNER), member()));
    }

    @Test
    void memberWithKickKicksAMemberWithoutKick() {
        assertTrue(MemberService.mayKick(member(CompanyPermission.KICK), member(CompanyPermission.INVITE)));
    }

    @Test
    void memberWithKickCannotKickAnotherMemberWithKick() {
        assertFalse(MemberService.mayKick(member(CompanyPermission.KICK), member(CompanyPermission.KICK)));
    }

    @Test
    void memberWithoutKickCannotKick() {
        assertFalse(MemberService.mayKick(member(CompanyPermission.MANAGE_ORDERS), member()));
    }

    @Test
    void nobodyKicksTheOwner() {
        assertFalse(MemberService.mayKick(member(CompanyPermission.KICK), member(CompanyPermission.OWNER)));
        assertFalse(MemberService.mayKick(member(CompanyPermission.OWNER), member(CompanyPermission.OWNER)));
    }
}
