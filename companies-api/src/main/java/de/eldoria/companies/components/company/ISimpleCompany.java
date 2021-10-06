package de.eldoria.companies.components.company;

import java.time.LocalDateTime;

public interface ISimpleCompany {
    /**
     * Creates a simple company from an id.
     * This company is not usable and should only be used for requesting data.
     *
     * @param id if of company
     * @return new simle company with this id
     */
    static ISimpleCompany forId(int id) {
        return new ISimpleCompany() {
            @Override
            public int id() {
                return id;
            }

            @Override
            public String name() {
                return "";
            }

            @Override
            public LocalDateTime founded() {
                return null;
            }

            @Override
            public String foundedString() {
                return null;
            }

            @Override
            public int level() {
                return 1;
            }
        };
    }

    int id();

    String name();

    LocalDateTime founded();

    String foundedString();

    int level();
}
