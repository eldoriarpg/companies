package de.eldoria.companies.events.company;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.level.ICompanyLevel;

/**
 * A company level changed.
 * <p>
 * Use {@link CompanyLevelDownEvent} or {@link CompanyLevelUpEvent}.
 */
public class CompanyLevelChangeEvent extends CompanyEvent<ICompanyProfile> {
    private final ICompanyLevel oldLevel;
    private final ICompanyLevel newLevel;

    public CompanyLevelChangeEvent(ICompanyProfile company, ICompanyLevel oldLevel, ICompanyLevel newLevel) {
        super(company);
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
}
