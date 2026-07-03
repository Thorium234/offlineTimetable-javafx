package com.thorium.ui.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class Messages {

    private static ResourceBundle bundle;
    private static Locale currentLocale = Locale.getDefault();

    private Messages() {}

    public static void init(Locale locale) {
        currentLocale = locale != null ? locale : Locale.getDefault();
        try {
            bundle = ResourceBundle.getBundle("i18n.messages", currentLocale);
        } catch (MissingResourceException e) {
            bundle = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        }
    }

    public static void init() {
        init(Locale.getDefault());
    }

    public static String get(String key) {
        if (bundle == null) {
            init();
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        String pattern = get(key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    public static Locale getCurrentLocale() {
        return currentLocale;
    }
}
