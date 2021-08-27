package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.permissions.CompanyPermission;
import de.eldoria.eldoutilities.localization.MessageComposer;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CompanyProfile extends SimpleCompany {
    private final List<CompanyMember> members;

    public CompanyProfile(int id, String name, LocalDateTime founded, int level, List<CompanyMember> members) {
        super(id, name, founded, level);
        this.members = members;
    }

    public Optional<CompanyMember> member(OfflinePlayer player) {
        return members.stream().filter(m -> m.uuid().equals(player.getUniqueId())).findAny();
    }

    public List<CompanyMember> members() {
        return members;
    }

    public List<CompanyMember> members(CompanyPermission... permissions) {
        return members.stream().filter(m -> m.hasPermissions(permissions)).collect(Collectors.toList());
    }

    public CompanyMember owner() {
        return members.stream().filter(m -> m.hasPermission(CompanyPermission.OWNER)).findFirst().get();
    }

    public Optional<CompanyMember> memberByName(String name) {
        return members.stream()
                .filter(member -> Optional.ofNullable(member.player().getName())
                        .map(n -> n.equalsIgnoreCase(name)).orElse(false))
                .findFirst();
    }

    public String asExternalProfileComponent(Configuration configuration) {
        var level = configuration.companySettings().level(level());
        var composer = MessageComposer.create()
                .text(name()).newLine()
                .localeCode("Level").text(": %s - %s",
                        level.map(CompanyLevel::level).orElse(-1), level.map(CompanyLevel::levelName).orElse("Unkown Level"));
        composer.newLine()
                .localeCode("Founded").text(": %s", foundedString()).newLine()
                .localeCode("Leader").text(": %s", owner().player().getName()).newLine()
                .localeCode("Member").text(": %s <click:run_command:/company member id %s>[", members().size(), id()).localeCode("list").text("]</click>").newLine();

        return composer.build();

    }
}
