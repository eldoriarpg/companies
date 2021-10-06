package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.company.ICompanyMember;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.util.Colors;
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
                .text("<%s>", Colors.HEADING).text(name()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.level").text(": <%s>%s - %s",
                        Colors.VALUE, level.level(), level.levelName())
                .newLine()
                .text("<%s>", Colors.NAME).localeCode("words.founded").text(": <%s>%s", Colors.VALUE, foundedString()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.leader").text(": <%s>%s", Colors.VALUE, owner().player().getName()).newLine()
                .text("<%s>", Colors.NAME).localeCode("words.member")
                .text(": <%s>%s <click:run_command:/company member id %s><%s>[", Colors.VALUE, members().size(), id(), Colors.SHOW)
                .localeCode("words.list").text("]</click>").newLine();

        return composer.build();

    }
}
