package com.dimrule;

import com.dimrule.command.DimRuleCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Dimensional Rule Isolation — every dimension (including datapack-defined ones) gets its own
 * {@code /gamerule} values.
 *
 * <p>Server-side only: no registry content, no packets, so a vanilla client can join.
 */
public final class DimensionRuleIsolation implements ModInitializer {
    public static final String MOD_ID = "dimensional_rule_isolation";
    public static final Logger LOG = LoggerFactory.getLogger("DimensionalRuleIsolation");

    public static Path configPath(net.minecraft.server.MinecraftServer server) {
        return server.getServerDirectory()
                .resolve("config")
                .resolve("dimensional-rule-isolation")
                .resolve("dimension_rules.json");
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DimRuleCommand.register(dispatcher)
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                DimensionRules.preload(configPath(server)));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DimensionRules.bootstrap(server, configPath(server));
            RecipeIsolation.rebuild(server);
            LOG.info("Dimensional Rule Isolation ready: {} dimension(s) with independent rules",
                    DimensionRules.count());
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                RecipeIsolation.rebuild(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DimensionRules.save();
            LOG.info("Dimensional Rule Isolation: rules saved.");
        });
    }
}