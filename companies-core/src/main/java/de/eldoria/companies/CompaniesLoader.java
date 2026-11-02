/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads the libraries of Companies listed in {@code paper-libraries.json} before the plugin starts: from Paper's
 * mirror of Maven Central first, then from EldoNexus, which holds the Eldoria libraries and mirrors what the Central
 * mirror lacks. The repositories the build used are ignored, so this class alone decides where libraries come from.
 */
@SuppressWarnings("UnstableApiUsage")
public class CompaniesLoader implements PluginLoader {
    private static final String LIBRARIES = "/paper-libraries.json";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var resolver = new MavenLibraryResolver();
        resolver.addRepository(repository("central", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR));
        resolver.addRepository(repository("eldonexus-releases", "https://eldonexus.de/repository/maven-releases/"));
        resolver.addRepository(repository("eldonexus-proxies", "https://eldonexus.de/repository/maven-proxies/"));
        libraries()
                .getAsJsonArray("dependencies")
                .forEach(library ->
                        resolver.addDependency(new Dependency(new DefaultArtifact(library.getAsString()), null)));
        classpathBuilder.addLibrary(resolver);
    }

    private static RemoteRepository repository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url).build();
    }

    private static JsonObject libraries() {
        try (InputStream in = Objects.requireNonNull(
                CompaniesLoader.class.getResourceAsStream(LIBRARIES), LIBRARIES + " is missing")) {
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + LIBRARIES, e);
        }
    }
}
