package com.boyka;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class BoykaMod implements ClientModInitializer {
    public static KeyBinding openKey;
    private static final Map<Integer, Boolean> DOWN = new HashMap<>();

    @Override
    public void onInitializeClient() {
        Config.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.boyka.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.boyka"));
        ClientTickEvents.START_CLIENT_TICK.register(BoykaMod::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            AutoMiner.reset(mc);
            return;
        }
        while (openKey.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new BoykaScreen());
        }
        if (mc.currentScreen == null) hotkeys(mc);
        AutoMiner.tick(mc);
        AutoLeave.tick(mc);
    }

    private static void hotkeys(MinecraftClient mc) {
        long window = mc.getWindow().getHandle();
        edge(window, Config.get().mineKey, () -> AutoMiner.toggle(mc));
    }

    private static void edge(long window, int key, Runnable action) {
        if (key < 0) return;
        boolean down = InputUtil.isKeyPressed(window, key);
        boolean was = DOWN.getOrDefault(key, false);
        DOWN.put(key, down);
        if (down && !was) action.run();
    }

    public static void sendCommand(MinecraftClient mc, String text) {
        if (mc.player == null || text == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        if (t.startsWith("/")) mc.player.networkHandler.sendChatCommand(t.substring(1));
        else mc.player.networkHandler.sendChatMessage(t);
    }

    public static String keyName(int key, String none) {
        if (key < 0) return none;
        return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString();
    }
}
