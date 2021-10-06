package de.eldoria.companies.data.wrapper.company;

import java.time.LocalDateTime;

public class CompanyRank extends CompanyStats {
    private final int rank;

    public CompanyRank(int rank, int id, String name, LocalDateTime founded, int memberCount, int orderCount, double price, int amount) {
        super(id, name, founded, memberCount, orderCount, price, amount);
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
