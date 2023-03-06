/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

public interface ICompanyMember {
    boolean hasPermission(CompanyPermission permissions);

    boolean hasPermissions(CompanyPermission... permissions);

    boolean hasAnyPermissions(CompanyPermission... permissions);

    ICompanyMember addPermission(CompanyPermission permission);

    ICompanyMember removePermission(CompanyPermission permission);

    OfflinePlayer player();

    int company();

    UUID uuid();

    long permission();

    List<CompanyPermission> permissions();

    boolean isOwner();
}
