package com.boyka;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.text.Text;

public final class AutoLeave {
    private AutoLeave() {}

    public static void tick(MinecraftClient mc) {
        Config c = Config.get();
        if (!c.leaveEnabled || mc.player == null || mc.world == null) return;
        if (mc.player.networkHandler == null) return;
        double r2 = (double) c.leaveRadius * c.leaveRadius;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player) continue;
            String name = other.getName().getString();
            if (c.isFriend(name)) continue;
            if (mc.player.squaredDistanceTo(other) <= r2) {
                AutoMiner.reset(mc);
                mc.player.networkHandler.getConnection()
                        .disconnect(Text.literal("[BOYKA] Auto Leave: gracz " + name + " w zasięgu " + c.leaveRadius));
                return;
            }
        }
    }
}
