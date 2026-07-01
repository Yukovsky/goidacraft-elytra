package dev.goidacraft.elytra;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Server config ({@code config/goidacraft_elytra-server.toml}).
 * All values are hot-reloaded on config load/reload.
 */
public final class ElytraConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ELYTRA_IDS;
    public static final ModConfigSpec.BooleanValue AUTO_DETECT_BY_NAME;
    public static final ModConfigSpec.DoubleValue MAX_HORIZONTAL;
    public static final ModConfigSpec.DoubleValue MAX_VERTICAL_UP;
    public static final ModConfigSpec.IntValue NOTIFY_COOLDOWN_TICKS;

    private static final List<String> DEFAULT_IDS = List.of(
            "minecraft:elytra",
            "betterend:elytra_armored",
            "betterend:elytra_crystalite");

    private static volatile Set<String> idCache = new HashSet<>(DEFAULT_IDS);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        ELYTRA_IDS = b
                .comment("List of item registry ids treated as restricted elytras (exact match).")
                .defineListAllowEmpty("elytraIds",
                        DEFAULT_IDS,
                        () -> "minecraft:elytra",
                        o -> o instanceof String);

        AUTO_DETECT_BY_NAME = b
                .comment(
                        "If true, any item whose registry id contains the word 'elytra'",
                        "is treated as an elytra, BUT only if it actually functions as one (canElytraFly).",
                        "Items with 'elytra' in the name but without gliding functionality are ignored.")
                .define("autoDetectByName", false);

        MAX_HORIZONTAL = b
                .comment("Maximum horizontal speed magnitude in the Overworld (blocks/tick). Blocks acceleration.")
                .defineInRange("maxHorizontal", 1.0, 0.0, 100.0);

        MAX_VERTICAL_UP = b
                .comment("Vertical speed ceiling: vy is clamped to min(vy, this value).",
                        "Must be <= 0 to guarantee descent (gliding downward).")
                .defineInRange("maxVerticalUp", -0.05, -100.0, 100.0);

        NOTIFY_COOLDOWN_TICKS = b
                .comment("Cooldown between repeated notifications to the player, in ticks (20 = 1 second).")
                .defineInRange("notifyCooldownTicks", 40, 0, 1200);

        SPEC = b.build();
    }

    private ElytraConfig() {
    }

    /** Rebuilds the id cache from the config. Called on load/reload. */
    public static void rebuildCache() {
        Set<String> set = new HashSet<>();
        for (String s : ELYTRA_IDS.get()) {
            if (s != null && !s.isBlank()) {
                set.add(s.toLowerCase(Locale.ROOT));
            }
        }
        idCache = set;
    }

    /** Explicit list of ids (lower-case). */
    public static Set<String> explicitIds() {
        return idCache;
    }
}
