/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CompanyProfile extends SimpleCompany implements ICompanyProfile {
    private final List<CompanyMember> members;

    public CompanyProfile(int id, String name, LocalDateTime founded, int level, List<CompanyMember> members) {
        super(id, name, founded, level);
        this.members = members;
    }

    @Override
    public Optional<CompanyMember> member(OfflinePlayer player) {
        return members.stream().filter(m -> m.uuid().equals(player.getUniqueId())).findAny();
    }

    @Override
    public List<? extends ICompanyMember> members() {
        return members;
    }

    @Override
    public List<CompanyMember> members(CompanyPermission... permissions) {
        return members.stream().filter(m -> m.hasPermissions(permissions)).collect(Collectors.toList());
    }

    @Override
    public CompanyMember owner() {
        return members.stream().filter(m -> m.hasPermission(CompanyPermission.OWNER)).findFirst().get();
    }

    @Override
    public Optional<CompanyMember> memberByName(String name) {
        return members.stream()
                .filter(member -> Optional.ofNullable(member.player().getName())
                        .map(n -> n.equalsIgnoreCase(name)).orElse(false))
                .findFirst();
    }

    public String asExternalProfileComponent(Configuration configuration) {
        var level = configuration.companySettings().level(level()).orElse(CompanyLevel.DEFAULT);
        var composer = MessageComposer.create()
                .text("<heading>").text(name()).newLine()
                .text("<name>").localeCode("words.level").text(": <value>%s - %s", level.level(), level.levelName())
                .newLine()
                .text("<name>").localeCode("words.founded").text(": <value>%s", foundedString()).newLine()
                .text("<name>").localeCode("words.leader").text(": <value>%s", owner().player().getName()).newLine()
                .text("<name>").localeCode("words.member")
                .text(": <value>%s <click:run_command:/company member id %s><show>[", members().size(), id())
                .localeCode("words.list").text("]</click>").newLine();

        return composer.build();

    }
}
