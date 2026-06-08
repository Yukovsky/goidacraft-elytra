package dev.goidacraft.elytra;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Locale;

/**
 * Серверные обработчики поведения элитр. Три слоя:
 * <ol>
 *     <li>превентивный ПКМ (надевание элитры / запуск фейерверка);</li>
 *     <li>реакция на смену экипировки (любой способ надевания в Крае);</li>
 *     <li>тик-страховка (кламп скорости, остановка полёта, принудительное снятие).</li>
 * </ol>
 */
@EventBusSubscriber(modid = ElytraControlMod.MOD_ID)
public final class ElytraEventHandler {

    private ElytraEventHandler() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Слой 1 — превентивный ПКМ
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> dim = player.level().dimension();
        ItemStack stack = event.getItemStack();

        // Край — запрет надевания элитры через ПКМ из хотбара (без кулдауна: явное действие игрока).
        if (dim == Level.END && isRestrictedElytra(stack, player)) {
            event.setCanceled(true);
            actionBar(player, "§c[Элитры] В Крае элитры не работают!");
            return;
        }

        // Обычный мир — запрет фейерверка в полёте.
        if (dim == Level.OVERWORLD && player.isFallFlying() && stack.is(Items.FIREWORK_ROCKET)) {
            event.setCanceled(true);
            if (NotifyCooldowns.canNotify(player, "fw", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
                actionBar(player, "§c[Элитры] Использование фейерверков на элитрах запрещено!");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Слой 2 — реакция на надевание (drag, shift-клик, раздатчик, /item, другие моды)
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.CHEST) {
            return;
        }
        if (player.level().dimension() != Level.END) {
            return;
        }
        if (!isRestrictedElytra(event.getTo(), player)) {
            return;
        }
        if (unequip(player) && NotifyCooldowns.canNotify(player, "end", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
            actionBar(player, "§c[Элитры] В Крае надевать элитры запрещено!");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Слой 3 — тик-страховка
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean flying = player.isFallFlying();
        boolean started = NotifyCooldowns.startedFlying(player, flying);
        ResourceKey<Level> dim = player.level().dimension();

        // ── Край ──
        if (dim == Level.END) {
            if (flying) {
                player.stopFallFlying();
                Vec3 v = player.getDeltaMovement();
                player.setDeltaMovement(0.0, v.y, 0.0);
                player.hurtMarked = true;
            }
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (isRestrictedElytra(chest, player) && unequip(player)
                    && NotifyCooldowns.canNotify(player, "end", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
                actionBar(player, "§c[Элитры] В Крае надевать элитры запрещено!");
            }
            return;
        }

        // ── Обычный мир ──
        if (dim == Level.OVERWORLD && flying) {
            Vec3 v = player.getDeltaMovement();
            double maxUp = ElytraConfig.MAX_VERTICAL_UP.get();
            double maxH = ElytraConfig.MAX_HORIZONTAL.get();

            double vy = Math.min(v.y, maxUp);
            double vx = v.x;
            double vz = v.z;
            double h = Math.sqrt(vx * vx + vz * vz);

            boolean boosted = false;
            if (h > maxH) {
                double s = h > 1.0e-6 ? maxH / h : 0.0;
                vx *= s;
                vz *= s;
                boosted = true;
            }

            player.setDeltaMovement(vx, vy, vz);
            player.hurtMarked = true; // форс-синк скорости на клиент (ключевое отличие от KubeJS-скрипта)

            if (boosted) {
                if (NotifyCooldowns.canNotify(player, "ow_boost", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
                    actionBar(player, "§e[Элитры] Ускорение заблокировано!");
                }
            } else if (started) {
                // Одноразовая подсказка в момент начала планирования (action bar сам гаснет ~через 2-3 c).
                actionBar(player, "§7[Элитры] Только планирование вниз");
            }
        }

        // ── Ад ── без ограничений.
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NotifyCooldowns.clear(event.getEntity().getUUID());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Хелперы
    // ─────────────────────────────────────────────────────────────────────────

    /** Снять элитру с CHEST-слота и вернуть в инвентарь (или дропнуть). */
    private static boolean unequip(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!isRestrictedElytra(chest, player)) {
            return false;
        }
        ItemStack copy = chest.copy();
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
        player.inventoryMenu.broadcastChanges();
        return true;
    }

    /**
     * Является ли предмет ограничиваемой элитрой:
     * (1) точное совпадение id из конфига, либо
     * (2) автодетект по слову 'elytra' в id + реальный функционал элитры.
     */
    static boolean isRestrictedElytra(ItemStack stack, LivingEntity entity) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key.toString().toLowerCase(Locale.ROOT);

        if (ElytraConfig.explicitIds().contains(id)) {
            return true;
        }
        if (ElytraConfig.AUTO_DETECT_BY_NAME.get() && id.contains("elytra")) {
            return functionsAsElytra(stack, entity);
        }
        return false;
    }

    /** Реально ли предмет даёт полёт-планирование (отсекает посторонние предметы с 'elytra' в имени). */
    private static boolean functionsAsElytra(ItemStack stack, LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return stack.canElytraFly(entity);
    }

    private static void actionBar(ServerPlayer player, String legacyText) {
        player.displayClientMessage(Component.literal(legacyText), true);
    }
}
