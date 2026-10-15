package com.boyka;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("boyka.json");
    private static Config instance = new Config();

    // Auto MineTask
    public int toolSlot = -1;          // indeks w ekwipunku gracza (0-8 hotbar, 9-35 reszta), -1 = brak
    public boolean right = true;       // true = line.R, false = line.L
    public int blocks = 50;            // 0-150
    public int mineKey = -1;           // kod GLFW, -1 = brak

    // Komendy na klawisz
    public String[] cmdText = {"/repair", "", ""};
    public int[] cmdKey = {-1, -1, -1};
    public boolean[] cmdAuto = {true, false, false};

    // Leave
    public boolean leaveEnabled = false;
    public int leaveRadius = 40;       // 1-50

    // Friends
    public List<String> friends = new ArrayList<>();

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        instance.fix();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fix() {
        if (cmdText == null || cmdText.length != 3) cmdText = new String[]{"/repair", "", ""};
        for (int i = 0; i < 3; i++) if (cmdText[i] == null) cmdText[i] = "";
        if (cmdKey == null || cmdKey.length != 3) cmdKey = new int[]{-1, -1, -1};
        if (cmdAuto == null || cmdAuto.length != 3) cmdAuto = new boolean[]{true, false, false};
        if (friends == null) friends = new ArrayList<>();
        blocks = Math.max(0, Math.min(150, blocks));
        leaveRadius = Math.max(1, Math.min(50, leaveRadius));
    }

    public boolean isFriend(String name) {
        for (String f : friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
