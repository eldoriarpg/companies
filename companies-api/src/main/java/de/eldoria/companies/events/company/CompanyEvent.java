package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.events.components.CompanyProvider;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company event.
 * <p>
 * This event is for internal use and should not be subscribed directly
 *
 * @param <Company> type of company implementation
 */
public class CompanyEvent<Company extends ISimpleCompany> extends Event implements CompanyProvider<Company> {
    public static HandlerList HANDLERS = new HandlerList();

    private final Company company;

    public CompanyEvent(Company company) {
        this.company = company;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * The involved company
     *
     * @return company
     */
    @Override
    public Company company() {
        return company;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
