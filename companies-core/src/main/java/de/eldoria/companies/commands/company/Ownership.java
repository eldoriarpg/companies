/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.components.company.ICompanyMember;

import java.util.Optional;

/**
 * Rules for passing on and giving up the ownership of a company.
 */
public final class Ownership {
    private Ownership() {}

    /**
     * Checks whether a member may hand the company to another member.
     *
     * @param member member who wants to transfer the company
     * @param target member who would become the owner, empty when the name is not a member
     * @return the outcome of the check
     */
    public static Transfer transferCheck(ICompanyMember member, Optional<? extends ICompanyMember> target) {
        if (!member.isOwner()) return Transfer.NOT_OWNER;
        if (target.isEmpty() || target.get().company() != member.company()) return Transfer.NOT_MEMBER;
        if (target.get().uuid().equals(member.uuid())) return Transfer.SELF;
        return Transfer.ALLOWED;
    }

    /**
     * Checks whether a member may leave the company. An owner has to hand the company on first, or disband it when
     * nobody else is left.
     *
     * @param member      member who wants to leave
     * @param memberCount members of the company, including the leaving one
     * @return the outcome of the check
     */
    public static Leave leaveCheck(ICompanyMember member, int memberCount) {
        if (!member.isOwner()) return Leave.ALLOWED;
        return memberCount > 1 ? Leave.TRANSFER_FIRST : Leave.DISBAND_INSTEAD;
    }

    /**
     * Outcome of a transfer check.
     */
    public enum Transfer {
        ALLOWED,
        NOT_OWNER,
        NOT_MEMBER,
        SELF
    }

    /**
     * Outcome of a leave check.
     */
    public enum Leave {
        ALLOWED,
        TRANSFER_FIRST,
        DISBAND_INSTEAD
    }
}
