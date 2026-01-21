package me.temxs27.hyfine.core;

import me.temxs27.hyfine.preset.OptimizationPreset;

public class OptimizationManager {
    private static volatile OptimizationPreset currentPreset = OptimizationPreset.BALANCED;

    public static void setPreset(OptimizationPreset preset) {
        currentPreset = preset;
        System.out.println("[HyFine] Preset cambiado a: " + preset.name());
    }

    public static OptimizationPreset getPreset() {
        return currentPreset;
    }
}