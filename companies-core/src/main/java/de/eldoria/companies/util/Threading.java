/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Threading {
    public static final ExecutorService VIRTUAL = Executors.newVirtualThreadPerTaskExecutor();
}
