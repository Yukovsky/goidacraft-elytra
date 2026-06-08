package dev.goidacraft.elytra;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Серверный конфиг ({@code config/goidacraft_elytra-server.toml}).
 * Все значения горячо перечитываются на загрузке/перезагрузке конфига.
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
                .comment("Список registry-id предметов, считающихся ограничиваемыми элитрами (точное совпадение).")
                .defineListAllowEmpty("elytraIds",
                        DEFAULT_IDS,
                        () -> "minecraft:elytra",
                        o -> o instanceof String);

        AUTO_DETECT_BY_NAME = b
                .comment(
                        "Если true — любой предмет, в registry-id которого есть слово 'elytra',",
                        "считается элитрой, НО только если он реально функционирует как элитра (canElytraFly).",
                        "Предметы с 'elytra' в имени, но без функционала планирования, игнорируются.")
                .define("autoDetectByName", false);

        MAX_HORIZONTAL = b
                .comment("Максимальный модуль горизонтальной скорости в обычном мире (блоков/тик). Блокирует разгон.")
                .defineInRange("maxHorizontal", 1.0, 0.0, 100.0);

        MAX_VERTICAL_UP = b
                .comment("Потолок вертикальной скорости: vy зажимается до min(vy, это значение).",
                        "Должно быть <= 0, чтобы гарантировать снижение (планирование вниз).")
                .defineInRange("maxVerticalUp", -0.05, -100.0, 100.0);

        NOTIFY_COOLDOWN_TICKS = b
                .comment("Кулдаун между повторными сообщениями игроку, в тиках (20 = 1 секунда).")
                .defineInRange("notifyCooldownTicks", 40, 0, 1200);

        SPEC = b.build();
    }

    private ElytraConfig() {
    }

    /** Перестроить кэш id из конфига. Вызывается на загрузке/перезагрузке. */
    public static void rebuildCache() {
        Set<String> set = new HashSet<>();
        for (String s : ELYTRA_IDS.get()) {
            if (s != null && !s.isBlank()) {
                set.add(s.toLowerCase(Locale.ROOT));
            }
        }
        idCache = set;
    }

    /** Явный список id (lower-case). */
    public static Set<String> explicitIds() {
        return idCache;
    }
}
