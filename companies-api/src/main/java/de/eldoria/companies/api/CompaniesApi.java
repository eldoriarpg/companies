package de.eldoria.companies.api;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.IMaterialPrice;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;

public abstract class CompaniesApi {
    private static CompaniesApi instance;

    static CompaniesApi getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Api was called before the api was loaded");
        }
        return instance;
    }

    protected static void initInstance(CompaniesApi instance) {
        if (CompaniesApi.instance != null) {
            throw new IllegalStateException("Api is already initialized.");
        }
        CompaniesApi.instance = instance;
    }

    /**
     * Get the company profile of a player
     *
     * @param player player
     * @return optional holding the company profile if the player is part of a company
     */
    public abstract Optional<? extends ICompanyProfile> getPlayerCompanyProfile(OfflinePlayer player);

    /**
     * Get the company profile of a player.
     *
     * @param player player
     * @return optional holding the company profile if the player is part of a company
     */
    public abstract Optional<? extends ISimpleCompany> getPlayerCompany(OfflinePlayer player);

    /**
     * Returns stats for the company.
     *
     * @param company company to check
     * @return company stats
     */
    public abstract ICompanyStats getCompanyStats(ISimpleCompany company);

    /**
     * Get the company by name.
     *
     * @param name name. Not case sensitive
     * @return optional holding a company if available
     */
    public abstract Optional<? extends ISimpleCompany> getCompanyByName(String name);

    /**
     * Get the company by id.
     *
     * @param id positive id of the company
     * @return optional holding a company if available
     */
    public abstract Optional<? extends ISimpleCompany> getCompanyById(int id);

    /**
     * Retrieve a order by the id
     *
     * @param id order id
     * @return optional hodling the order id available
     */
    public abstract Optional<? extends ISimpleOrder> retrieveOrderById(int id);

    /**
     * Retrieve all orders assigned to a company
     *
     * @param company company to check. Can be a {@link ISimpleCompany#forId(int)}.
     * @param min     min order state
     * @param max     max order state
     * @return List of orders. List may be empty
     */
    public abstract List<? extends ISimpleOrder> retrieveOrdersByCompany(ISimpleCompany company, OrderState min, OrderState max);

    /**
     * Retrieve all orders assigned to a company
     *
     * @param player player to check.
     * @param min    min order state
     * @param max    max order state
     * @return List of orders. List may be empty
     */
    public abstract List<? extends ISimpleOrder> retrieveOrdersByPlayer(OfflinePlayer player, OrderState min, OrderState max);

    /**
     * Retrieve the material price for a material
     *
     * @param material material to check.
     * @return optional which holds the material price. This result may be cached or be retrived from the database
     */
    public abstract Optional<? extends IMaterialPrice> retrieveMaterialPrice(Material material);
}
