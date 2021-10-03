package de.eldoria.companies.components.order;

public interface IMaterialPrice {
    String material();

    double avgPrice();

    double minPrice();

    double maxPrice();
}
