package com.palmer.billingstatementgenerator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Centralizes access to persistent user preferences for the application.
 * Preferences are stored under the OS user preferences store using the
 * application's package path as the node key.
 *
 * <p>Keys managed here:
 * <ul>
 *   <li>{@code hasLaunched} — whether the app has completed at least one launch</li>
 * </ul>
 */
public final class AppPreferences {
    private static final Logger logger = LoggerFactory.getLogger(AppPreferences.class);

    private static final String NODE_PATH = "com/palmer/billingstatementgenerator";
    private static final String KEY_HAS_LAUNCHED = "hasLaunched";
    private static final String KEY_LICENSE_KEY = "licenseKey";

    private AppPreferences() {
    }

    private static Preferences node() {
        return Preferences.userRoot().node(NODE_PATH);
    }

    /**
     * Returns {@code true} if the application has completed at least one launch.
     *
     * @return whether the app has been launched before
     */
    public static boolean hasLaunched() {
        return node().getBoolean(KEY_HAS_LAUNCHED, false);
    }

    /**
     * Sets the launched flag. Pass {@code false} to reset help prompts so the
     * instructions tab is shown on next launch.
     *
     * @param value
     *         {@code true} after a completed launch; {@code false} to reset
     */
    public static void setHasLaunched(boolean value) {
        node().putBoolean(KEY_HAS_LAUNCHED, value);
    }

    public static String getLicenseKey() {
        return node().get(KEY_LICENSE_KEY, null);
    }

    public static void setLicenseKey(String licenseKey) {
        node().put(KEY_LICENSE_KEY, licenseKey);

        try {
            node().flush();
        } catch (BackingStoreException e) {
            logger.warn("Failed to flush license key to preferences store", e);
        }
    }

    public static void removeLicenseKey() {
        node().remove(KEY_LICENSE_KEY);
        try {
            node().flush();
        } catch (java.util.prefs.BackingStoreException e) {
            logger.warn("Failed to flush license key removal", e);
        }
    }
}