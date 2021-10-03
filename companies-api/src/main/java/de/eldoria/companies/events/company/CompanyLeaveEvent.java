package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

public class CompanyLeaveEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyLeaveEvent(ICompanyProfile company, OfflinePlayer player) {
        super(company);
        this.player = player;
    }

    @Override
    public OfflinePlayer player() {
        return player;
    }
}
