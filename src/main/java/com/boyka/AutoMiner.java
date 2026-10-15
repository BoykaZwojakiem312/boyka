package com.boyka;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * AFK kopanie jak na 1.8: kopiesz blok na który patrzysz (celownik), a postać
 * cały czas idzie bokiem (strafe) w lewo albo w prawo. Kamera jest wolna.
 */
public final class AutoMiner {
    public static boolean active = false;

    private static boolean strafeRight;
    private static double startX, startZ, lastX, lastZ;
    private static int stuck;
    private static int toolHotbar = -1;
    private static int swappedFrom = -1;
    private static boolean prevPause;
    private static int ticks;
    private static int repairTimer;
    private static int repairAllAt = -1;

    private AutoMiner() {}

    public static void toggle(MinecraftClient mc) {
        if (active) stop(mc, "Zatrzymano kopanie.");
        else start(mc);
    }

    public static void start(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        Config c = Config.get();
        if (p == null || mc.interactionManager == null) return;
        if (c.blocks <= 0) {
            msg(mc, "Ustaw liczbę kratek większą niż 0.");
            return;
        }
        PlayerInventory inv = p.getInventory();
        toolHotbar = -1;
        swappedFrom = -1;
        if (c.toolSlot >= 0) {
            if (inv.getStack(c.toolSlot).isEmpty()) {
                msg(mc, "Zaznaczony slot jest pusty.");
                return;
            }
            if (c.toolSlot < 9) {
                toolHotbar = c.toolSlot;
            } else {
                toolHotbar = inv.selectedSlot;
                swappedFrom = c.toolSlot;
                swap(mc, c.toolSlot, toolHotbar);
            }
        }
        strafeRight = c.right;
        startX = lastX = p.getX();
        startZ = lastZ = p.getZ();
        stuck = 0;
        ticks = 0;
        repairTimer = 0;
        repairAllAt = -1;
        prevPause = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false; // pozwala na alt+tab bez pauzy
        active = true;
        msg(mc, "Kopanie START (" + (c.right ? "line.R - w prawo" : "line.L - w lewo") + ", "
                + c.blocks + " kratek" + (c.continuous ? ", w pętli" : "") + ").");
    }

    public static void stop(MinecraftClient mc, String message) {
        if (!active) return;
        active = false;
        mc.options.pauseOnLostFocus = prevPause;
        releaseKeys(mc);
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (mc.player != null && swappedFrom >= 0) swap(mc, swappedFrom, toolHotbar);
        swappedFrom = -1;
        if (message != null) msg(mc, message);
    }

    /** Ciche zatrzymanie (np. po rozłączeniu). */
    public static void reset(MinecraftClient mc) {
        if (!active) return;
        active = false;
        mc.options.pauseOnLostFocus = prevPause;
        releaseKeys(mc);
        swappedFrom = -1;
    }

    /** Koniec linii: w trybie pętli zawraca, inaczej zatrzymuje. Zwraca true gdy kopanie trwa dalej. */
    private static boolean endOfLine(MinecraftClient mc, ClientPlayerEntity p, String message) {
        if (Config.get().continuous) {
            strafeRight = !strafeRight;
            startX = lastX = p.getX();
            startZ = lastZ = p.getZ();
            stuck = 0;
            return true;
        }
        stop(mc, message);
        return false;
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null) {
            reset(mc);
            return;
        }
        if (!p.isAlive()) {
            stop(mc, "Nie żyjesz - stop.");
            return;
        }
        Config c = Config.get();
        ticks++;

        // ---- Auto Repair ----
        if (c.repairOn || c.repairAllOn) {
            repairTimer++;
            if (repairTimer >= c.repairMinutes * 60 * 20) {
                repairTimer = 0;
                if (c.repairOn) {
                    BoykaMod.sendCommand(mc, "/repair");
                    if (c.repairAllOn) repairAllAt = ticks + 40; // /repair all 2 sekundy później
                } else {
                    BoykaMod.sendCommand(mc, "/repair all");
                }
            }
        }
        if (repairAllAt >= 0 && ticks >= repairAllAt) {
            repairAllAt = -1;
            BoykaMod.sendCommand(mc, "/repair all");
        }

        // ---- Narzędzie ----
        if (toolHotbar >= 0) {
            p.getInventory().selectedSlot = toolHotbar;
            ItemStack tool = p.getInventory().getStack(toolHotbar);
            if (tool.isEmpty()) {
                stop(mc, "Narzędzie zniknęło - stop.");
                return;
            }
            if (tool.isDamageable() && tool.getMaxDamage() - tool.getDamage() <= 1) {
                stop(mc, "Narzędzie prawie zepsute - stop.");
                return;
            }
        }

        // ---- Ile kratek już przeszliśmy ----
        double dx = p.getX() - startX, dz = p.getZ() - startZ;
        if (Math.sqrt(dx * dx + dz * dz) >= c.blocks) {
            if (!endOfLine(mc, p, "Gotowe! Przekopano " + c.blocks + " kratek.")) return;
        }

        // ---- Zablokowany bok (ściana) = koniec linii ----
        double mx = p.getX() - lastX, mz = p.getZ() - lastZ;
        lastX = p.getX();
        lastZ = p.getZ();
        if (mx * mx + mz * mz < 1.0E-4) stuck++;
        else stuck = 0;
        if (stuck >= 60) {
            stuck = 0;
            if (!endOfLine(mc, p, "Zablokowano z boku - koniec linii.")) return;
        }

        // ---- Bezpieczeństwo: płyn z boku ----
        double yaw = Math.toRadians(p.getYaw());
        double sx = strafeRight ? -Math.cos(yaw) : Math.cos(yaw);
        double sz = strafeRight ? -Math.sin(yaw) : Math.sin(yaw);
        BlockPos side = BlockPos.ofFloored(p.getX() + sx, p.getY(), p.getZ() + sz);
        if (!w.getBlockState(side).getFluidState().isEmpty()
                || !w.getBlockState(side.down()).getFluidState().isEmpty()) {
            stop(mc, "Płyn z boku - stop.");
            return;
        }

        // ---- Idź bokiem ----
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.rightKey.setPressed(strafeRight);
        mc.options.leftKey.setPressed(!strafeRight);

        // ---- Kop to, na co patrzysz ----
        HitResult hit = p.raycast(4.5, 1.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            mc.interactionManager.updateBlockBreakingProgress(bhr.getBlockPos(), bhr.getSide());
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    private static void releaseKeys(MinecraftClient mc) {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private static void swap(MinecraftClient mc, int slotId, int hotbar) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, hotbar,
                SlotActionType.SWAP, mc.player);
    }

    private static void msg(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§9[BOYKA] §f" + text), false);
    }
}
