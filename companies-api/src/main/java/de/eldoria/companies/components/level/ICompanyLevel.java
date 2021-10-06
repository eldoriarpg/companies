package de.eldoria.companies.components.level;

public interface ICompanyLevel {
    String asComponent();

    int level();

    String levelName();

    ILevelRequirement requirement();

    ILevelSettings settings();
}
