package de.eldoria.companies.data.wrapper.company;

import de.eldoria.companies.data.wrapper.order.FullOrder;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class CompanyProfile extends SimpleCompany {
    List<CompanyMember> members;

    public CompanyProfile(int id, String name, LocalDateTime founded, List<CompanyMember> members) {
        super(id, name, founded);
        this.members = members;
    }

    public Optional<CompanyMember> member(OfflinePlayer player) {
        return members.stream().filter(m -> m.uuid() == player.getUniqueId()).findAny();
    }
}
