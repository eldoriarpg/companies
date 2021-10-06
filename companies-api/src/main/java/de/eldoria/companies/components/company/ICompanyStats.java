package de.eldoria.companies.components.company;

import java.time.LocalDateTime;

public interface ICompanyStats {
    int id();

    String name();

    LocalDateTime founded();

    int memberCount();

    int orderCount();

    double price();

    int deliveredItems();
}
