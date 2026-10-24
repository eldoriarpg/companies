/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository.impl;

import de.chojo.sadu.queries.api.configuration.ConnectedQueryConfiguration;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.queries.configuration.ConnectedQueryConfigurationImpl;
import de.eldoria.companies.data.repository.ConstraintViolations;

import java.sql.SQLException;
import java.util.List;

/**
 * Transactions that collect their errors, so a repository can turn expected constraint violations into results and
 * roll back explicitly.
 */
public final class Transactions {
    private Transactions() {
        throw new UnsupportedOperationException("This is a utility class.");
    }

    /**
     * Opens a transaction that collects errors instead of logging them. It is committed on close only when no error
     * was collected.
     *
     * @param errors list receiving the errors
     * @return transaction
     */
    public static ConnectedQueryConfigurationImpl open(List<SQLException> errors) {
        return QueryConfiguration.getDefault()
                .edit()
                .setExceptionHandler(errors::add)
                .build()
                .withSingleTransaction();
    }

    /**
     * Rolls a transaction back and logs the collected errors, except constraint violations.
     *
     * @param conf   transaction to roll back
     * @param errors collected errors
     * @return true when every error was a constraint violation
     */
    public static boolean rollback(ConnectedQueryConfiguration conf, List<SQLException> errors) {
        try {
            conf.connection().rollback();
        } catch (SQLException e) {
            errors.add(e);
        }
        if (errors.stream().allMatch(ConstraintViolations::isViolation)) return true;
        errors.forEach(QueryConfiguration.getDefault().exceptionHandler());
        return false;
    }
}
