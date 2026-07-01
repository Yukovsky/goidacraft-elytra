package dev.goidacraft.elytra;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Server-side per-dimension elytra control mod (NeoForge 1.21.1).
 *
 * <p>Changes elytra behaviour:
 * <ul>
 *     <li>The End (the_end) — elytras are completely forbidden;</li>
 *     <li>Overworld (overworld) — gliding downward only, no acceleration or fireworks;</li>
 *     <li>The Nether (the_nether) — unrestricted.</li>
 * </ul>
 *
 * All logic runs on the server tick, so it works correctly even at low TPS
 * and requires no client-side mods. Values are configurable in {@code config/goidacraft_elytra-server.toml}.
 */
@Mod(ElytraControlMod.MOD_ID)
public class ElytraControlMod {
    public static final String MOD_ID = "goidacraft_elytra";

    public ElytraControlMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, ElytraConfig.SPEC);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ElytraConfig.SPEC) {
            ElytraConfig.rebuildCache();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ElytraConfig.SPEC) {
            ElytraConfig.rebuildCache();
        }
    }
}
