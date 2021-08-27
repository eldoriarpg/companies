package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;

/**
 * Indicates that a user was kicked from a company.
 */
public class CompanyKickEvent extends CompanyEvent<CompanyProfile> implements PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;

    public CompanyKickEvent(CompanyProfile simpleCompany, OfflinePlayer player) {
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
