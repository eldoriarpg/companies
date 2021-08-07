package de.eldoria.companies.orders;

public enum OrderState {
    /**
     * The order is not claimed by any company
     */
    UNCLAIMED(0),
    /**
     * The order is claimed by a company and must be delivered in the next n days.
     */
    CLAIMED(100),
    /**
     * The order is delivered by the company and can be received by the client.
     */
    DELIVERED(200),
    /**
     * The order was received by the client
     */
    RECEIVED(300);

    private final int stateId;

    OrderState(int stateId) {
        this.stateId = stateId;
    }

    public int stateId() {
        return stateId;
    }

    public static OrderState byId(int stateId) {
        for (var value : values()) if (value.stateId == stateId) return value;
        return null;
    }
}
