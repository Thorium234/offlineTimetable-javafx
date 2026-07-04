package com.thorium.application.port;

import java.lang.reflect.Method;
import java.nio.file.Path;

public final class BootstrapFactory {

    private static final String IMPL_CLASS = "com.thorium.infrastructure.ApplicationBootstrap";

    private BootstrapFactory() {}

    public static Bootstrap create(Path databasePath) {
        try {
            Class<?> cls = Class.forName(IMPL_CLASS);
            Method factory = cls.getMethod("create", Path.class);
            return (Bootstrap) factory.invoke(null, databasePath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Bootstrap: " + IMPL_CLASS, e);
        }
    }
}
