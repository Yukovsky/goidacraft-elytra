package dev.goidacraft.elytra;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Серверный мод управления элитрами по измерениям (NeoForge 1.21.1).
 *
 * <p>Меняет поведение элитр:
 * <ul>
 *     <li>Край (the_end) — элитры полностью запрещены;</li>
 *     <li>Обычный мир (overworld) — только планирование вниз, без разгона и фейерверков;</li>
 *     <li>Ад (the_nether) — без ограничений.</li>
 * </ul>
 *
 * Вся логика выполняется на серверном тике, поэтому работает корректно даже при низком TPS
 * и не требует клиентских модов. Значения настраиваются в {@code config/goidacraft_elytra-server.toml}.
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
