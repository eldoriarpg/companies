package de.eldoria.companies.events.order;

import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;

public class OrderPaymentEvent extends OrderEvent<SimpleOrder> implements Cancellable, PlayerProvider<OfflinePlayer>{
    private final OfflinePlayer player;
    private final double amount;
    private boolean cancelled;

    public OrderPaymentEvent(SimpleOrder order, OfflinePlayer player, double amount) {
        super(order, true);
        this.player = player;
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    public OfflinePlayer player() {
        return player;
    }

    public double amount() {
        return amount;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }


}
