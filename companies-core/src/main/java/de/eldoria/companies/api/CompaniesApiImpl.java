package de.eldoria.companies.api;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.components.company.ISimpleCompany;
import de.eldoria.companies.components.order.ISimpleOrder;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;

public class CompaniesApiImpl extends CompaniesApi {
    private final ACompanyData companyData;
    private final AOrderData orderData;

    private CompaniesApiImpl(ACompanyData companyData, AOrderData orderData) {
        this.companyData = companyData;
        this.orderData = orderData;
        initInstance(this);
    }

    public static CompaniesApiImpl create(ACompanyData companyData, AOrderData orderData) {
        return new CompaniesApiImpl(companyData, orderData);
    }

    @Override
    public Optional<? extends ICompanyProfile> getPlayerCompanyProfile(OfflinePlayer player) {
        return companyData.retrievePlayerCompanyProfile(player).join();
    }

    @Override
    public Optional<? extends ISimpleCompany> getPlayerCompany(OfflinePlayer player) {
        return companyData.retrievePlayerCompany(player).join();
    }

    @Override
    public ICompanyStats getCompanyStats(ISimpleCompany company) {
        return companyData.retrieveCompanyStats(company).join();
    }

    @Override
    public Optional<? extends ISimpleCompany> getCompanyByName(String name) {
        return companyData.retrieveCompanyByName(name).join();
    }

    @Override
    public Optional<? extends ISimpleCompany> getCompanyById(int id) {
        return companyData.retrieveCompanyById(id).join();
    }

    @Override
    public Optional<? extends ISimpleOrder> retrieveOrderById(int id) {
        return orderData.retrieveOrderById(id).join();
    }

    @Override
    public List<? extends ISimpleOrder> retrieveOrdersByCompany(ISimpleCompany company, OrderState min, OrderState max) {
        return orderData.retrieveOrdersByCompany(company, min, max).join();
    }

    @Override
    public List<? extends SimpleOrder> retrieveOrdersByPlayer(OfflinePlayer player, OrderState min, OrderState max) {
        return orderData.retrieveOrdersByPlayer(player, min, max).join();
    }
}
