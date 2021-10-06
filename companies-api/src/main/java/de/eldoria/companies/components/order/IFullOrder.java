package de.eldoria.companies.components.order;

import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

public interface IFullOrder extends ISimpleOrder {
    List<? extends IOrderContent> contents();

    double price();

    int amount();

    int materialsAmount();

    int delivered();

    double progress();

    Optional<? extends IOrderContent> content(Material material);

    boolean isDone();
}
