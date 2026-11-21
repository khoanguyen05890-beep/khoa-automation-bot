package com.khoa.automationbot;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

import static com.khoa.automationbot.Lang.t;

/**
 * Chỉ có MỘT người chơi thật được điều khiển, nên tại một thời điểm chỉ một bot được chạy.
 * Các bot khác đang RUNNING sẽ xếp hàng chờ theo thứ tự trong danh sách.
 */
public final class BotManager {
    private BotManager() {}

    public static final List<Bot> bots = new ArrayList<>();
    public static final BlockScanner SCANNER = new BlockScanner();
    public static Bot miner, collector, builder;
    public static Bot active;
    private static int lastInv = -1;

    public static void init() {
        miner = add(new Bot("Auto Miner", Bot.Type.MINE, true));
        collector = add(new Bot("Item Collector", Bot.Type.COLLECT, true));
        builder = add(new Bot("Litematica Builder", Bot.Type.BUILD, true));
    }

    public static Bot add(Bot b) { bots.add(b); return b; }

    public static void remove(Bot b) {
        if (b.builtin) return;
        b.task.stop();
        bots.remove(b);
    }

    public static LitematicaBuilder builderTask() { return (LitematicaBuilder) builder.task; }

    public static void stopAll() {
        for (Bot b : bots) if (b.task.state != Task.State.IDLE) b.task.stop();
        active = null;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { active = null; lastInv = -1; return; }
        active = null;
        for (Bot b : bots) {
            if (b.task.state == Task.State.RUNNING) { active = b; break; }
        }
        if (active == null) { lastInv = -1; return; }
        Task task = active.task;
        // An toàn: dừng khi sắp chết
        if (!mc.player.isAlive() || mc.player.getHealth() <= 4f) {
            task.pause();
            task.status = t("Tạm dừng: máu thấp", "Paused: low health");
            return;
        }
        try {
            task.tick(mc);
        } catch (Throwable e) {
            KhoaBotMod.LOG.error("Lỗi trong task " + task.name(), e);
            task.state = Task.State.ERROR;
            task.status = e.getClass().getSimpleName() + ": " + e.getMessage();
            Motion.release();
        }
        int inv = Tools.totalItems(mc.player);
        if (lastInv >= 0 && inv > lastInv) Stats.collected += inv - lastInv;
        lastInv = inv;
    }
}
