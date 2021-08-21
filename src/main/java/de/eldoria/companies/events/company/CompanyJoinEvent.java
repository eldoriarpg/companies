package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

/**
 * Indicates that a user joined a company.
 */
public class CompanyJoinEvent extends CompanyEvent<CompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyJoinEvent(CompanyProfile simpleCompany, OfflinePlayer player) {
        super(simpleCompany);
        this.player = player;
    }

    @Override
    public OfflinePlayer player() {
        return player;
    }
}
