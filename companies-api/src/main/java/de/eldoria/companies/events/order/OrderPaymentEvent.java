package de.eldoria.companies.events.order;

import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.events.PlayerProvider;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Cancellable;

public class OrderPaymentEvent extends OrderEvent<ISimpleOrder> implements Cancellable, PlayerProvider<OfflinePlayer> {
    private final OfflinePlayer player;
    private final double amount;
    private boolean cancelled;

    public OrderPaymentEvent(ISimpleOrder order, OfflinePlayer player, double amount) {
        super(order, true);
        this.player = player;
        this.amount = amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    public OfflinePlayer player() {
        return player;
    }

    public double amount() {
        return amount;
    }


}
