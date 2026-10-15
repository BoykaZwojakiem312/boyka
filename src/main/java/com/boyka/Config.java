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
    public boolean continuous = false; // continuous playback: po skończeniu linii zawraca i kopie w pętli

    // Auto Repair
    public boolean repairOn = false;      // wysyłaj /repair
    public boolean repairAllOn = false;   // wysyłaj /repair all
    public int repairMinutes = 1;         // co ile minut (1-5)

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
        if (friends == null) friends = new ArrayList<>();
        blocks = Math.max(0, Math.min(150, blocks));
        leaveRadius = Math.max(1, Math.min(50, leaveRadius));
        repairMinutes = Math.max(1, Math.min(5, repairMinutes));
    }

    public boolean isFriend(String name) {
        for (String f : friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }
}
