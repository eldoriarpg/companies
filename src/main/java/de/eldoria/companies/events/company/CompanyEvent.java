package de.eldoria.companies.events.company;

import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CompanyEvent<Company extends SimpleCompany> extends Event implements CompanyProvider<Company> {
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
     * @return a company
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
