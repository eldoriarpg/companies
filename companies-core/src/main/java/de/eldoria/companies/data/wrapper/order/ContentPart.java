package de.eldoria.companies.data.wrapper.order;

import de.eldoria.companies.components.order.IContentPart;

import java.util.UUID;

public class ContentPart implements IContentPart {
    private final UUID worker;
    private final int amount;

    public ContentPart(UUID worker, int amount) {
        this.worker = worker;
        this.amount = amount;
    }

    @Override
    public UUID worker() {
        return worker;
    }

    @Override
    public int amount() {
        return amount;
    }
}
