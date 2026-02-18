package me.temxs27.hyfine.core;

import me.temxs27.hyfine.preset.OptimizationPreset;

/**
 * Manages the current optimization preset for the HyFine plugin.
 * Uses a volatile field to ensure visibility across threads.
 */
public class OptimizationManager {

    // Volatile ensures that changes to currentPreset are immediately visible to other threads
    private static volatile OptimizationPreset currentPreset = OptimizationPreset.BALANCED;

    // Logging is disabled by default; enable with /hylog command
    private static volatile boolean loggingEnabled = false;

    // View radius optimization is enabled by default; disable with /hyvoff command
    private static volatile boolean viewRadiusEnabled = true;

    /**
     * Sets the current optimization preset.
     * Logs the change to the console only if logging is enabled.
     *
     * @param preset The new preset to set.
     */
    public static void setPreset(OptimizationPreset preset) {
        currentPreset = preset;
        if (loggingEnabled) {
            System.out.println("[HyFine] Preset changed to: " + preset.name());
        }
    }

    /**
     * Gets the currently active optimization preset.
     *
     * @return The current preset.
     */
    public static OptimizationPreset getPreset() {
        return currentPreset;
    }

    // --- Logging Control ---

    /**
     * Checks if logging is currently enabled.
     * @return true if logging is enabled.
     */
    public static boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * Toggles the logging state.
     * @return The new logging state after toggling.
     */
    public static boolean toggleLogging() {
        loggingEnabled = !loggingEnabled;
        return loggingEnabled;
    }

    // --- View Radius Control ---

    /**
     * Checks if the view radius optimization is currently enabled.
     * @return true if view radius optimization is enabled.
     */
    public static boolean isViewRadiusEnabled() {
        return viewRadiusEnabled;
    }

    /**
     * Toggles the view radius optimization state.
     * @return The new view radius state after toggling.
     */
    public static boolean toggleViewRadius() {
        viewRadiusEnabled = !viewRadiusEnabled;
        return viewRadiusEnabled;
    }
}
