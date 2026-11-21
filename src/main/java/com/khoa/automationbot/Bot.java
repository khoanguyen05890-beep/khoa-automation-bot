package com.khoa.automationbot;

import static com.khoa.automationbot.Lang.t;

public final class Bot {
    public enum Type { MINE, COLLECT, BUILD }

    public String name;
    public final Type type;
    public final Task task;
    public final boolean builtin;

    public Bot(String name, Type type, boolean builtin) {
        this.name = name;
        this.type = type;
        this.builtin = builtin;
        this.task = switch (type) {
            case MINE -> new AutoMiner();
            case COLLECT -> new ItemCollector();
            case BUILD -> new LitematicaBuilder();
        };
    }

    public void start() {
        if (!Config.get().enabled(type)) {
            task.status = t("Hệ thống đang tắt (bật ở Dashboard)", "System is disabled (enable in Dashboard)");
            return;
        }
        task.start();
    }

    public static String typeName(Type t) {
        return switch (t) {
            case MINE -> Lang.t("Đào block", "Mine blocks");
            case COLLECT -> Lang.t("Nhặt đồ", "Collect items");
            case BUILD -> Lang.t("Hỗ trợ xây dựng", "Build helper");
        };
    }
}
