package com.boyka;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class AutoMiner {
    public static boolean active = false;

    private static BlockPos start;
    private static Direction dir;
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
        // kierunek liczony raz, względem tego gdzie patrzysz w chwili startu
        Direction facing = p.getHorizontalFacing();
        dir = c.right ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        start = p.getBlockPos();
        ticks = 0;
        repairTimer = 0;
        repairAllAt = -1;
        prevPause = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false; // pozwala na alt+tab bez pauzy
        active = true;
        msg(mc, "Kopanie START (" + (c.right ? "line.R" : "line.L") + ", " + c.blocks + " kratek).");
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

        // kamera jest wolna - nie ruszamy yaw/pitch

        BlockPos feet = p.getBlockPos();
        int traveled = (feet.getX() - start.getX()) * dir.getOffsetX()
                + (feet.getZ() - start.getZ()) * dir.getOffsetZ();
        if (traveled >= c.blocks) {
            stop(mc, "Gotowe! Przekopano " + traveled + " kratek.");
            return;
        }

        BlockPos front = feet.offset(dir);
        BlockPos upper = front.up();
        if (!w.getBlockState(front.down()).getFluidState().isEmpty()) {
            stop(mc, "Płyn pod drogą - stop.");
            return;
        }

        BlockPos target = null;
        for (BlockPos bp : new BlockPos[]{front, upper}) {
            BlockState st = w.getBlockState(bp);
            if (!st.getFluidState().isEmpty()) {
                stop(mc, "Płyn na drodze - stop.");
                return;
            }
            if (!st.isAir() && target == null) {
                if (st.getHardness(w, bp) < 0) {
                    stop(mc, "Nie do zniszczenia (bedrock?) - stop.");
                    return;
                }
                target = bp;
            }
        }

        if (target != null) {
            steer(p, false);
            mc.interactionManager.updateBlockBreakingProgress(target, dir.getOpposite());
            p.swingHand(Hand.MAIN_HAND);
        } else {
            steer(p, true);
        }
    }

    /** Wciska klawisze ruchu tak, żeby iść w kierunku dir niezależnie od tego, gdzie patrzy kamera. */
    private static void steer(ClientPlayerEntity p, boolean move) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean f = false, b = false, l = false, r = false;
        if (move) {
            double dirYaw = Math.atan2(-dir.getOffsetX(), dir.getOffsetZ());
            double rel = dirYaw - Math.toRadians(p.getYaw());
            double fwd = Math.cos(rel);
            double side = Math.sin(rel);
            f = fwd > 0.38;
            b = fwd < -0.38;
            r = side > 0.38;
            l = side < -0.38;
        }
        mc.options.forwardKey.setPressed(f);
        mc.options.backKey.setPressed(b);
        mc.options.leftKey.setPressed(l);
        mc.options.rightKey.setPressed(r);
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
