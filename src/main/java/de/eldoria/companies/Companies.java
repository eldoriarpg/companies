package de.eldoria.companies;

import de.eldoria.companies.commands.Company;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.CompanyData;
import de.eldoria.companies.data.OrderData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.eldoutilities.plugin.EldoPlugin;
import net.milkbowl.vault.economy.Economy;

public class Companies extends EldoPlugin {
    private Configuration configuration;
    private CompanyData companyData;
    private OrderData orderData;
    private Economy economy;

    @Override
    public void onPluginEnable() {
        configuration = new Configuration(this);

        MessageSender.create(this, "§c[C]");
        ILocalizer.create(this, "en_US", "de_DE").setLocale("en_US");

        var economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        } else {
            logger().severe("No vault provider registered.");
            getPluginManager().disablePlugin(this);
            return;
        }

        registerCommand("company", new Company(this, companyData, orderData, economy, configuration));
        registerCommand("order", new Order(this, companyData, orderData, economy, configuration));
    }

    @Override
    public void onPluginDisable() {

    }
}
