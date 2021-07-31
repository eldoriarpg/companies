package de.eldoria.companies.data.wrapper.order;

import java.util.UUID;

public class ContentPart {
    UUID worker;
    int amount;

    public ContentPart(UUID worker, int amount) {
        this.worker = worker;
        this.amount = amount;
    }

    public UUID worker() {
        return worker;
    }

    public int amount() {
        return amount;
    }
}
