package com.khoa.automationbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public int scanRadius = 16;       // Block Scanner
    public int mineRadius = 16;       // Auto Miner
    public int itemRadius = 12;       // Item Collector
    public int actionDelay = 2;       // tick giữa các hành động
    public String mode = "NORMAL";    // SAFE / NORMAL / FAST
    public String lang = "vi";        // vi / en
    public int openKey = 75;          // GLFW_KEY_K
    public boolean shiftClickOpen = true;
    public boolean autoTool = true;
    public boolean onlyMineSelected = true;
    public boolean autoCollect = true;
    public boolean collectOnlyListed = false;
    public boolean moduleMiner = true, moduleCollector = true, moduleBuilder = true, moduleScanner = true;
    public List<String> minerBlocks = new ArrayList<>();
    public List<String> collectItems = new ArrayList<>();
    public List<String> ignoreItems = new ArrayList<>();

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("khoa_automation_bot.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) instance = GSON.fromJson(Files.readString(f), Config.class);
        } catch (Exception e) {
            KhoaBotMod.LOG.error("Không đọc được cấu hình", e);
        }
        if (instance == null) instance = new Config();
        if (instance.minerBlocks == null) instance.minerBlocks = new ArrayList<>();
        if (instance.collectItems == null) instance.collectItems = new ArrayList<>();
        if (instance.ignoreItems == null) instance.ignoreItems = new ArrayList<>();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception e) {
            KhoaBotMod.LOG.error("Không lưu được cấu hình", e);
        }
    }

    public boolean enabled(Bot.Type t) {
        return switch (t) {
            case MINE -> moduleMiner;
            case COLLECT -> moduleCollector;
            case BUILD -> moduleBuilder;
        };
    }

    /** Số tick giữa hai hành động, phụ thuộc chế độ. */
    public int delay() {
        int d = actionDelay;
        if ("SAFE".equals(mode)) d += 3;
        else if ("FAST".equals(mode)) d -= 1;
        return Math.max(1, d);
    }

    /** Độ/tick khi xoay camera. */
    public float turnSpeed() {
        return switch (mode) {
            case "SAFE" -> 15f;
            case "FAST" -> 120f;
            default -> 40f;
        };
    }
}
