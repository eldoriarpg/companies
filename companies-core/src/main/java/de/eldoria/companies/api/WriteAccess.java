/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Hands the write operations of the API the services they need. The services exist only once the economy is set up;
 * until then, and after the plugin was disabled, every write completes exceptionally with an
 * {@link ApiNotReadyException}.
 */
final class WriteAccess {
    @Nullable
    private volatile CompaniesApiImpl.Writes writes;

    /**
     * @param writes the services of the write operations
     */
    void open(CompaniesApiImpl.Writes writes) {
        this.writes = writes;
    }

    /**
     * Refuses write operations from now on.
     */
    void close() {
        writes = null;
    }

    /**
     * Runs a write operation once the services exist.
     *
     * @param operation the operation
     * @param <T>       result of the operation
     * @return the future of the operation, or a failed one while writes are not possible
     */
    <T> CompletableFuture<T> run(Function<CompaniesApiImpl.Writes, CompletableFuture<T>> operation) {
        var current = writes;
        if (current == null) {
            return CompletableFuture.failedFuture(
                    new ApiNotReadyException("Companies can not write before its economy is set up"));
        }
        return operation.apply(current);
    }
}
