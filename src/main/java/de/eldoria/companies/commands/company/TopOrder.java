package de.eldoria.companies.commands.company;

public enum TopOrder {
    ORDERS("order_count DESC"),
    EARNED("price DESC"),
    MEMBER("member_count DESC"),
    ITEMS_DELIVERED("amount DESC");

    private final String orderColumn;

    TopOrder(String orderColumn) {
        this.orderColumn = orderColumn;
    }

    public String orderColumn() {
        return orderColumn;
    }
}
