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
 * Server-side elytra behaviour handlers. Three layers:
 * <ol>
 *     <li>preventive right-click interception (equipping the elytra / launching a firework);</li>
 *     <li>equipment-change reaction (catches any other way to equip it in the End);</li>
 *     <li>per-tick safety net (speed clamping, stopping flight, forced unequip).</li>
 * </ol>
 */
@EventBusSubscriber(modid = ElytraControlMod.MOD_ID)
public final class ElytraEventHandler {

    private ElytraEventHandler() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 1 — preventive right-click interception
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

        // End — block equipping the elytra via right-click from the hotbar (no cooldown: explicit player action).
        if (dim == Level.END && isRestrictedElytra(stack, player)) {
            event.setCanceled(true);
            // CRITICAL: when use() is cancelled the server skips its own sendAllDataToRemote()
            // (ServerPlayerGameMode.useItem), so the client is left with an optimistically equipped
            // "phantom" elytra. Force a full inventory resync to roll back the client prediction.
            forceResync(player);
            actionBar(player, "§c[Элитры] В Крае элитры не работают!");
            return;
        }

        // Overworld — forbid fireworks while gliding.
        if (dim == Level.OVERWORLD && player.isFallFlying() && stack.is(Items.FIREWORK_ROCKET)) {
            event.setCanceled(true);
            // The client already predicted the rocket being consumed — restore the correct stack state.
            forceResync(player);
            if (NotifyCooldowns.canNotify(player, "fw", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
                actionBar(player, "§c[Элитры] Использование фейерверков на элитрах запрещено!");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer 2 — equip reaction (drag, shift-click, dispenser, /item, other mods)
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
    // Layer 3 — per-tick safety net
    // ─────────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean flying = player.isFallFlying();
        boolean started = NotifyCooldowns.startedFlying(player, flying);
        ResourceKey<Level> dim = player.level().dimension();

        // ── End ──
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

        // ── Overworld ──
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
            player.hurtMarked = true; // force-sync velocity to the client (the key difference from the KubeJS script)

            if (boosted) {
                if (NotifyCooldowns.canNotify(player, "ow_boost", ElytraConfig.NOTIFY_COOLDOWN_TICKS.get())) {
                    actionBar(player, "§e[Элитры] Ускорение заблокировано!");
                }
            } else if (started) {
                // One-time hint when gliding starts (the action bar fades out on its own after ~2-3 s).
                actionBar(player, "§7[Элитры] Только планирование вниз");
            }
        }

        // ── Nether ── unrestricted.
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        NotifyCooldowns.clear(event.getEntity().getUUID());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Unequip the elytra from the CHEST slot and return it to the inventory (or drop it). */
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
        // Unconditional resync: guarantees the client sees an empty chest slot
        // and can't predict flight based on a phantom elytra.
        forceResync(player);
        return true;
    }

    /** Forces a full inventory resync to the player (overwrites client-side prediction). */
    private static void forceResync(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
    }

    /**
     * Whether an item counts as a restricted elytra:
     * (1) exact id match from the config, or
     * (2) auto-detect by the word 'elytra' in the id + actually functions as an elytra.
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

    /** Whether the item actually grants gliding flight (filters out unrelated items with 'elytra' in the name). */
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
