package de.eldoria.companies.services.messages;

import com.comphenix.protocol.events.PacketContainer;
import de.eldoria.companies.Companies;
import net.kyori.adventure.text.TextComponent;

import java.util.function.Function;
import java.util.logging.Level;

public class AdventureComponentAdapter {
    private static Function<PacketContainer, String> adapter = AdventureComponentAdapter::adapter;

    public static String rawMessage(PacketContainer packet) {
        return adapter.apply(packet);
    }

    private static String adapter(PacketContainer packet) {
        buildAdapter(packet);
        return adapter.apply(packet);
    }

    private static void buildAdapter(PacketContainer packet) {
        try {
            var field = packet.getHandle().getClass().getField("adventure$message");
            adapter = container -> {
                try {
                    return  ((TextComponent) field.get(container.getHandle())).content();
                } catch (IllegalAccessException e) {
                    Companies.logger().log(Level.WARNING, "Could not read field value of adventure$message");
                }
                return getSafeString(container);
            };
        } catch (NoSuchFieldException e) {
            adapter = AdventureComponentAdapter::getSafeString;
        }
    }

    private static String getSafeString(PacketContainer container) {
        if (container.getStrings().size() == 0) {
            return "";
        }
        return container.getStrings().read(0);
    }
}
