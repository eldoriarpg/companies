/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.testing;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Creates stand-ins for API interfaces in tests. A stub answers the methods named in its map with the mapped value,
 * {@code toString}, {@code equals} and {@code hashCode} by identity, and fails on every other call.
 */
public final class Stubs {
    private Stubs() {}

    /**
     * Creates a stub.
     *
     * @param type    interface to implement
     * @param answers return values by method name
     * @param <T>     type of the interface
     * @return the stub
     */
    public static <T> T of(Class<T> type, Map<String, Object> answers) {
        var stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            var name = method.getName();
            if (answers.containsKey(name)) return answers.get(name);
            return switch (name) {
                case "toString" -> type.getSimpleName() + answers;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(name);
            };
        });
        return type.cast(stub);
    }
}
