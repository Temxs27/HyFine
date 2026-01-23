package me.temxs27.hyfine.core;

import me.temxs27.hyfine.preset.OptimizationPreset;

/**
 * Manages the current optimization preset for the HyFine plugin.
 * Uses a volatile field to ensure visibility across threads.
 */
public class OptimizationManager {

    // Volatile ensures that changes to currentPreset are immediately visible to other threads
    private static volatile OptimizationPreset currentPreset = OptimizationPreset.BALANCED;

    /**
     * Sets the current optimization preset.
     * Logs the change to the console.
     *
     * @param preset The new preset to set.
     */
    public static void setPreset(OptimizationPreset preset) {
        currentPreset = preset;
        // Log the preset change in English
        System.out.println("[HyFine] Preset changed to: " + preset.name());
    }

    /**
     * Gets the currently active optimization preset.
     *
     * @return The current preset.
     */
    public static OptimizationPreset getPreset() {
        return currentPreset;
    }
}
