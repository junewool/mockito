/*
 * Copyright (c) 2016 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.configuration.plugins;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.mockito.internal.util.collections.Iterables;
import org.mockito.plugins.PluginSwitch;

class PluginInitializer {

    private final PluginSwitch pluginSwitch;
    private final Set<String> alias;

    PluginInitializer(PluginSwitch pluginSwitch, Set<String> alias) {
        this.pluginSwitch = pluginSwitch;
        this.alias = alias;
    }

    /**
     * Equivalent to {@link java.util.ServiceLoader#load} but without requiring
     * Java 6 / Android 2.3 (Gingerbread).
     */
    public <T> T loadImpl(Class<T> service) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        Enumeration<URL> resources;
        try {
            resources = loader.getResources("mockito-extensions/" + service.getName());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + service, e);
        }

        try {
            String classOrAlias =
                    new PluginFinder(pluginSwitch).findPluginClass(Iterables.toIterable(resources));
            if (classOrAlias != null) {
                if (alias.contains(classOrAlias)) {
                    classOrAlias = DefaultMockitoPlugins.getDefaultPluginClass(classOrAlias);
                }
                Class<?> pluginClass = loader.loadClass(classOrAlias);
                addReads(pluginClass);
                Object plugin = pluginClass.getDeclaredConstructor().newInstance();
                return service.cast(plugin);
            }
            return null;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load " + service + " implementation declared in " + resources, e);
        }
    }

    public <T> List<T> loadImpls(Class<T> service) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        Enumeration<URL> resources;
        try {
            resources = loader.getResources("mockito-extensions/" + service.getName());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + service, e);
        }

        try {
            List<String> classesOrAliases =
                    new PluginFinder(pluginSwitch)
                            .findPluginClasses(Iterables.toIterable(resources));
            List<T> impls = new ArrayList<>();
            for (String classOrAlias : classesOrAliases) {
                if (alias.contains(classOrAlias)) {
                    classOrAlias = DefaultMockitoPlugins.getDefaultPluginClass(classOrAlias);
                }
                Class<?> pluginClass = loader.loadClass(classOrAlias);
                addReads(pluginClass);
                Object plugin = pluginClass.getDeclaredConstructor().newInstance();
                impls.add(service.cast(plugin));
            }
            return impls;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load " + service + " implementation declared in " + resources, e);
        }
    }

    private static void addReads(Class<?> pluginClass) {
        try {
            Method getModule = Class.class.getMethod("getModule");
            Method addReads =
                    getModule.getReturnType().getMethod("addReads", getModule.getReturnType());
            addReads.invoke(
                    getModule.invoke(PluginInitializer.class), getModule.invoke(pluginClass));
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
