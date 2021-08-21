package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

public class CompanyLeaveEvent extends CompanyEvent<CompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyLeaveEvent(CompanyProfile company, OfflinePlayer player) {
        super(company);
        this.player = player;
    }

    @Override
    public OfflinePlayer player() {
        return player;
    }
}
