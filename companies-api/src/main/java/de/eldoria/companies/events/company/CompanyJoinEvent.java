package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

/**
 * Indicates that a user joined a company.
 */
public class CompanyJoinEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyJoinEvent(ICompanyProfile simpleCompany, OfflinePlayer player) {
        super(simpleCompany);
        this.player = player;
    }

    @Override
    public OfflinePlayer player() {
        return player;
    }
}
