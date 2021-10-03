package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A company level changed.
 * <p>
 * Use {@link CompanyLevelDownEvent} or {@link CompanyLevelUpEvent}.
 */
public class CompanyLevelChangeEvent extends CompanyEvent<ICompanyProfile> {
    public static HandlerList HANDLERS = new HandlerList();

    private final ICompanyLevel oldLevel;
    private final ICompanyLevel newLevel;

    public CompanyLevelChangeEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company, true);
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    /**
     * The old level before the change
     *
     * @return company level
     */
    public ICompanyLevel oldLevel() {
        return oldLevel;
    }

    /**
     * The new level after the change
     *
     * @return company level
     */
    public ICompanyLevel newLevel() {
        return newLevel;
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
