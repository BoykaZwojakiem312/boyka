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
    private static final int[] lastSent = new int[3];

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
        Direction facing = p.getHorizontalFacing();
        dir = c.right ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        start = p.getBlockPos();
        ticks = 0;
        lastSent[0] = lastSent[1] = lastSent[2] = -1000;
        prevPause = mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus = false; // pozwala na alt+tab bez pauzy
        active = true;
        msg(mc, "Kopanie START (" + (c.right ? "line.R" : "line.L") + ", " + c.blocks + " kratek).");
    }

    public static void stop(MinecraftClient mc, String message) {
        if (!active) return;
        active = false;
        mc.options.pauseOnLostFocus = prevPause;
        mc.options.forwardKey.setPressed(false);
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
        mc.options.forwardKey.setPressed(false);
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

        if (toolHotbar >= 0) {
            p.getInventory().selectedSlot = toolHotbar;
            ItemStack tool = p.getInventory().getStack(toolHotbar);
            if (tool.isEmpty()) {
                stop(mc, "Narzędzie zniknęło - stop.");
                return;
            }
            if (tool.isDamageable()) {
                int left = tool.getMaxDamage() - tool.getDamage();
                for (int i = 0; i < 3; i++) {
                    if (c.cmdAuto[i] && !c.cmdText[i].isBlank()
                            && left < tool.getMaxDamage() * 0.10 && ticks - lastSent[i] > 100) {
                        BoykaMod.sendCommand(mc, c.cmdText[i]);
                        lastSent[i] = ticks;
                    }
                }
                if (left <= 1) {
                    stop(mc, "Narzędzie prawie zepsute - stop.");
                    return;
                }
            }
        }

        // patrz w kierunku kopania
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getOffsetX(), dir.getOffsetZ()));
        p.setYaw(yaw);
        p.setPitch(0f);

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
            mc.options.forwardKey.setPressed(false);
            mc.interactionManager.updateBlockBreakingProgress(target, dir.getOpposite());
            p.swingHand(Hand.MAIN_HAND);
        } else {
            mc.options.forwardKey.setPressed(true);
        }
    }

    private static void swap(MinecraftClient mc, int slotId, int hotbar) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slotId, hotbar,
                SlotActionType.SWAP, mc.player);
    }

    private static void msg(MinecraftClient mc, String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§9[BOYKA] §f" + text), false);
    }
}
