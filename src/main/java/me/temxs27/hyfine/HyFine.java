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

public class HyFine extends JavaPlugin {

    private static HyFine instance;
    private OptimizationEngine optimizationEngine;
    private PerformanceMonitor performanceMonitor;

    public HyFine(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        System.out.println("[HyFine] Plugin initializing...");
    }

    @Override
    protected void setup() {
        super.setup();

        this.performanceMonitor = new PerformanceMonitor();
        this.optimizationEngine = new OptimizationEngine(this);

        this.optimizationEngine.start();

        this.getCommandRegistry().registerCommand(
                new HyFineCommand("hyfine", "HyFine main command", false)
        );

        this.getCommandRegistry().registerCommand(
                new HyFineLowCommand("hyflow", "Set LOW preset", false)
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

        System.out.println("[HyFine] Optimization engine started!");
        System.out.println("[HyFine] Core systems initialized!");
    }

    public static HyFine getInstance() {
        return instance;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public OptimizationEngine getOptimizationEngine() {
        return optimizationEngine;
    }
}