/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.time.LocalDateTime;

/**
 * A company without its members.
 */
public interface ISimpleCompany {
    /**
     * Creates a company that only knows its id. Its name is empty, its founding time is null and its level is 1. Use
     * it where only the id matters.
     *
     * @param id id of the company
     * @return a company with this id
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

    /**
     * The id of the company.
     *
     * @return id
     */
    int id();

    /**
     * The name of the company.
     *
     * @return name
     */
    String name();

    /**
     * When the company was founded.
     *
     * @return founding time in the server's time zone
     */
    LocalDateTime founded();

    /**
     * When the company was founded, formatted for display.
     *
     * @return formatted founding time
     */
    String foundedString();

    /**
     * The current level of the company.
     *
     * @return level, starting at 1
     */
    int level();
}
