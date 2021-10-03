package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

/**
 * Indicates that a user was kicked from a company.
 */
public class CompanyKickEvent extends CompanyEvent<ICompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyKickEvent(ICompanyProfile simpleCompany, OfflinePlayer player) {
        super(simpleCompany);
        this.player = player;
    }

    /**
     * The player which was kicked.
     *
     * @return the kicked player
     */
    @Override
    public OfflinePlayer player() {
        return player;
    }
}
