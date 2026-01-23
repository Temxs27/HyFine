package me.temxs27.hyfine;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.temxs27.hyfine.command.HyFineBalancedCommand;
import me.temxs27.hyfine.command.HyFineCommand;
import me.temxs27.hyfine.command.HyFineLowCommand;
import me.temxs27.hyfine.command.HyFineStatsCommand;
import me.temxs27.hyfine.command.HyFineUltraCommand;
import me.temxs27.hyfine.core.PerformanceMonitor;
import me.temxs27.hyfine.optimization.OptimizationEngine;

import javax.annotation.Nonnull;

/**
 * Main class for the HyFine optimization plugin.
 * Extends JavaPlugin to integrate with the Hytale server plugin system.
 * Initializes the PerformanceMonitor and OptimizationEngine, and registers commands.
 */
public class HyFine extends JavaPlugin {

    // Singleton instance to provide global access to the plugin
    private static HyFine instance;

    // Core components of the plugin
    private OptimizationEngine optimizationEngine;
    private PerformanceMonitor performanceMonitor;

    /**
     * Constructor for the HyFine plugin.
     * Called by the server when the plugin is loaded.
     *
     * @param init The initialization data provided by the server.
     */
    public HyFine(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        System.out.println("[HyFine] Plugin initializing...");
    }

    /**
     * Setup method called during the plugin's setup phase.
     * Initializes core components and registers commands.
     */
    @Override
    protected void setup() {
        super.setup(); // Call parent setup if needed

        // Initialize the performance monitoring system
        this.performanceMonitor = new PerformanceMonitor();
        // Initialize the optimization engine, passing the plugin instance
        this.optimizationEngine = new OptimizationEngine(this);

        // Start the optimization engine's background thread
        this.optimizationEngine.start();

        // Register all HyFine commands with the server's command registry
        this.getCommandRegistry().registerCommand(
                new HyFineCommand("hyfine", "HyFine main command", false)
        );

        this.getCommandRegistry().registerCommand(
                new HyFineLowCommand("hyflow", "Set LOW preset", false) // Corrected command name
        );

        this.getCommandRegistry().registerCommand(
                new HyFineBalancedCommand("hyfbalanced", "Set BALANCED preset", false)
        );

        this.getCommandRegistry().registerCommand(
                new HyFineUltraCommand("hyfultra", "Set ULTRA preset", false)
        );

        this.getCommandRegistry().registerCommand(
                new HyFineStatsCommand("hyfstats", "Show performance stats", false)
        );

        // Log successful startup
        System.out.println("[HyFine] Optimization engine started!");
        System.out.println("[HyFine] Core systems initialized!");
    }

    /**
     * Provides access to the singleton instance of the HyFine plugin.
     *
     * @return The HyFine plugin instance.
     */
    public static HyFine getInstance() {
        return instance;
    }

    /**
     * Getter for the PerformanceMonitor instance.
     *
     * @return The PerformanceMonitor used by the plugin.
     */
    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    /**
     * Getter for the OptimizationEngine instance.
     *
     * @return The OptimizationEngine used by the plugin.
     */
    public OptimizationEngine getOptimizationEngine() {
        return optimizationEngine;
    }
}
