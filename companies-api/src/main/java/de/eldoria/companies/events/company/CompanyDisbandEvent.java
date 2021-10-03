package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company gets disbanded by its owner.
 */
public class CompanyDisbandEvent extends CompanyEvent<ICompanyProfile> {
    public static HandlerList HANDLERS = new HandlerList();

    public CompanyDisbandEvent(ICompanyProfile company) {
        super(company);
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
