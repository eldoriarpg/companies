package de.eldoria.companies.components.company;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;

public interface ICompanyProfile extends ISimpleCompany {
    Optional<? extends ICompanyMember> member(OfflinePlayer player);

    List<? extends ICompanyMember> members();

    List<? extends ICompanyMember> members(CompanyPermission... permissions);

    ICompanyMember owner();

    Optional<? extends ICompanyMember> memberByName(String name);
}
