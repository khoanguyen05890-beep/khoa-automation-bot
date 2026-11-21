package com.khoa.automationbot.gui;

import com.khoa.automationbot.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import static com.khoa.automationbot.Lang.t;

public class MainScreen extends Screen {
    private static final int ACC = 0xFF3B82F6, GRN = 0xFF1F9D63, RED = 0xFFC94444, YEL = 0xFFB98512,
            BTN = 0xFF2B3140, PAN = 0xF0141821, SIDE = 0xFF191E2B, TXT = 0xFFE6E9F0, DIM = 0xFF8A92A6;
    private static final int ROW = 11;

    private static int tab = 0;
    private static Bot.Type newType = Bot.Type.MINE;
    private static boolean collectTargetIsList = true; // Item Collector: thêm vào "nhặt" hay "bỏ qua"

    private record Click(int x, int y, int w, int h, Runnable r) {}
    private static final class Field { String text = ""; }

    private final List<Click> clicks = new ArrayList<>();
    private final List<int[]> listRects = new ArrayList<>();
    private final List<String> listKeys = new ArrayList<>();
    private final Map<String, Integer> scroll = new HashMap<>();
    private final Field fFeature = new Field(), fSearch = new Field(), fName = new Field();
    private Field active;
    private boolean capturing;
    private int px, py, pw, ph, cx, cy, cw, ch;

    // ----- cache danh sách block/item -----
    private static List<Block> allBlocks;
    private static List<String> blockKeys;
    private static List<Item> allItems;
    private static List<String> itemKeys;
    private String lastBlockQ = null, lastItemQ = null;
    private List<Block> fBlocks = List.of();
    private List<Item> fItems = List.of();

    public MainScreen() {
        super(Text.literal("KHOA AUTOMATION BOT"));
        fName.text = "Bot " + (BotManager.bots.size() + 1);
        if (allBlocks == null) {
            allBlocks = new ArrayList<>();
            blockKeys = new ArrayList<>();
            for (Block b : Registries.BLOCK) {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR || b == Blocks.BEDROCK
                        || b instanceof FluidBlock) continue;
                allBlocks.add(b);
                blockKeys.add((Registries.BLOCK.getId(b).getPath() + " " + b.getName().getString()).toLowerCase());
            }
            allItems = new ArrayList<>();
            itemKeys = new ArrayList<>();
            for (Item i : Registries.ITEM) {
                if (i == Items.AIR) continue;
                allItems.add(i);
                itemKeys.add((Registries.ITEM.getId(i).getPath() + " " + i.getName().getString()).toLowerCase());
            }
        }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(); }

    @Override
    public void tick() {
        if (tab == 2 && Config.get().moduleScanner) {
            BotManager.SCANNER.radius = Config.get().scanRadius;
            BotManager.SCANNER.tick(MinecraftClient.getInstance());
        }
    }

    // ===================== helpers vẽ =====================
    private void rect(DrawContext g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }
    private void txt(DrawContext g, String s, int x, int y, int c) { g.drawText(textRenderer, s, x, y, c, false); }
    private boolean in(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28), gr = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private void btn(DrawContext g, int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r) {
        rect(g, x, y, w, h, in(mx, my, x, y, w, h) ? lighten(color) : color);
        String s = textRenderer.trimToWidth(label, w - 4);
        txt(g, s, x + (w - textRenderer.getWidth(s)) / 2, y + (h - 8) / 2, TXT);
        clicks.add(new Click(x, y, w, h, r));
    }

    private void tog(DrawContext g, int mx, int my, int x, int y, int w, String label, boolean v, Runnable flip) {
        btn(g, mx, my, x, y, w, 14, (v ? "[ON]  " : "[OFF] ") + label, v ? GRN : BTN, () -> { flip.run(); Config.save(); });
    }

    private void stepper(DrawContext g, int mx, int my, int x, int y, int w, String label, int val, int step, int min, int max, IntConsumer set) {
        txt(g, label, x, y + 3, TXT);
        btn(g, mx, my, x + w - 70, y, 14, 14, "-", BTN, () -> { set.accept(Math.max(min, val - step)); Config.save(); });
        String v = String.valueOf(val);
        txt(g, v, x + w - 52 + (36 - textRenderer.getWidth(v)) / 2, y + 3, TXT);
        btn(g, mx, my, x + w - 14, y, 14, 14, "+", BTN, () -> { set.accept(Math.min(max, val + step)); Config.save(); });
    }

    private void bar(DrawContext g, int x, int y, int w, int h, double f) {
        rect(g, x, y, w, h, 0xFF0D1017);
        if (f >= 0) rect(g, x, y, (int) (w * Math.min(1, f)), h, ACC);
        String s = f < 0 ? "--" : (int) (f * 100) + "%";
        txt(g, s, x + (w - textRenderer.getWidth(s)) / 2, y + (h - 8) / 2, TXT);
    }

    private void field(DrawContext g, int mx, int my, Field f, int x, int y, int w, String hint) {
        rect(g, x, y, w, 14, active == f ? 0xFF2D3750 : 0xFF0F121A);
        g.drawBorder(x, y, w, 14, active == f ? ACC : 0xFF39415A);
        String s = f.text.isEmpty() && active != f ? hint : f.text + (active == f && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        txt(g, textRenderer.trimToWidth(s, w - 6), x + 3, y + 3, f.text.isEmpty() && active != f ? DIM : TXT);
        clicks.add(new Click(x, y, w, 14, () -> active = f));
    }

    private <T> void list(DrawContext g, int mx, int my, String key, int x, int y, int w, int h, List<T> items,
                          Function<T, String> label, Predicate<T> sel, Consumer<T> click) {
        rect(g, x, y, w, h, 0xFF10131B);
        int rows = Math.max(1, h / ROW);
        int max = Math.max(0, items.size() - rows);
        int sc = Math.min(max, Math.max(0, scroll.getOrDefault(key, 0)));
        scroll.put(key, sc);
        listRects.add(new int[]{x, y, w, h, max});
        listKeys.add(key);
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < rows && sc + i < items.size(); i++) {
            T it = items.get(sc + i);
            int ry = y + i * ROW;
            boolean s = sel.test(it);
            boolean hov = in(mx, my, x, ry, w, ROW) && in(mx, my, x, y, w, h);
            rect(g, x, ry, w, ROW, s ? 0xFF1E4D3A : hov ? 0xFF273042 : ((i & 1) == 0 ? 0xFF151923 : 0xFF121620));
            txt(g, textRenderer.trimToWidth(label.apply(it), w - 8), x + 3, ry + 2, s ? 0xFF7CF0B0 : TXT);
            clicks.add(new Click(x, ry, w, ROW, () -> click.accept(it)));
        }
        g.disableScissor();
    }

    private List<Block> blocks(String q) {
        q = q.trim().toLowerCase();
        if (!q.equals(lastBlockQ)) {
            lastBlockQ = q;
            List<Block> r = new ArrayList<>();
            for (int i = 0; i < allBlocks.size(); i++) if (q.isEmpty() || blockKeys.get(i).contains(q)) r.add(allBlocks.get(i));
            fBlocks = r;
        }
        return fBlocks;
    }

    private List<Item> items(String q) {
        q = q.trim().toLowerCase();
        if (!q.equals(lastItemQ)) {
            lastItemQ = q;
            List<Item> r = new ArrayList<>();
            for (int i = 0; i < allItems.size(); i++) if (q.isEmpty() || itemKeys.get(i).contains(q)) r.add(allItems.get(i));
            fItems = r;
        }
        return fItems;
    }

    private String stateText(Task.State s) {
        return switch (s) {
            case IDLE -> t("Dừng", "Stopped");
            case RUNNING -> t("Đang chạy", "Running");
            case PAUSED -> t("Tạm dừng", "Paused");
            case DONE -> t("Hoàn thành", "Done");
            case ERROR -> t("Lỗi", "Error");
        };
    }

    private int stateColor(Task.State s) {
        return switch (s) { case RUNNING -> 0xFF7CF0B0; case PAUSED -> 0xFFF0C060; case ERROR -> 0xFFFF7070; case DONE -> 0xFF80B0FF; default -> DIM; };
    }

    private String tabName(int i) {
        return switch (i) {
            case 0 -> "Dashboard";
            case 1 -> "Auto Miner";
            case 2 -> "Block Scanner";
            case 3 -> "Item Collector";
            case 4 -> "Litematica Builder";
            case 5 -> "Bot Manager";
            default -> t("Cài đặt", "Settings");
        };
    }

    private static final String[] TAB_KEYS = {
            "dashboard tong quan trang chu status", "auto miner dao mine block", "block scanner quet scan",
            "item collector nhat do vat pham collect", "litematica builder xay schematic build",
            "bot manager quan ly bot nhiem vu", "settings cai dat tuy chinh config"};

    private List<Integer> visibleTabs() {
        String q = fFeature.text.trim().toLowerCase();
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < 7; i++) if (q.isEmpty() || TAB_KEYS[i].contains(q) || tabName(i).toLowerCase().contains(q)) r.add(i);
        return r;
    }

    // ===================== render =====================
    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        clicks.clear();
        listRects.clear();
        listKeys.clear();
        g.fill(0, 0, width, height, 0xA0000000);
        pw = Math.min(width - 12, 580);
        ph = Math.min(height - 12, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rect(g, px, py, pw, ph, PAN);
        g.drawBorder(px, py, pw, ph, ACC);
        int sw = 120;
        rect(g, px + 1, py + 1, sw, ph - 2, SIDE);
        txt(g, "KHOA AUTOMATION BOT", px + 6, py + 6, 0xFF7FB2FF);
        field(g, mx, my, fFeature, px + 6, py + 20, sw - 12, t("Tìm chức năng...", "Search feature..."));
        int ty = py + 40;
        for (int i : visibleTabs()) {
            final int idx = i;
            btn(g, mx, my, px + 6, ty, sw - 12, 18, tabName(i), tab == i ? ACC : BTN, () -> { tab = idx; active = null; });
            ty += 21;
        }
        cx = px + sw + 8;
        cy = py + 8;
        cw = pw - sw - 16;
        ch = ph - 16;
        try {
            switch (tab) {
                case 0 -> dashboard(g, mx, my);
                case 1 -> miner(g, mx, my);
                case 2 -> scanner(g, mx, my);
                case 3 -> collector(g, mx, my);
                case 4 -> builder(g, mx, my);
                case 5 -> botManager(g, mx, my);
                default -> settings(g, mx, my);
            }
        } catch (Exception e) {
            txt(g, "UI error: " + e, cx, cy, 0xFFFF7070);
        }
    }

    private void controls(DrawContext g, int mx, int my, int x, int y, int w, Bot b) {
        int bw = (w - 8) / 3;
        btn(g, mx, my, x, y, bw, 16, b.task.state == Task.State.PAUSED ? "Resume" : "Start", GRN,
                () -> { if (b.task.state == Task.State.PAUSED) b.task.resume(); else b.start(); });
        btn(g, mx, my, x + bw + 4, y, bw, 16, "Pause", YEL, b.task::pause);
        btn(g, mx, my, x + 2 * (bw + 4), y, bw, 16, "Stop", RED, b.task::stop);
    }

    // ----------------------- Dashboard -----------------------
    private void dashboard(DrawContext g, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config c = Config.get();
        int y = cy;
        txt(g, t("Trạng thái mod: ", "Mod status: ") + t("Hoạt động", "Active") + "   FPS: " + mc.getCurrentFps()
                + "   Litematica: " + (LitematicaBridge.loaded() ? "OK" : t("chưa cài", "not installed")), cx, y, TXT);
        y += 12;
        txt(g, t("Bot đang chạy: ", "Active bot: ") + (BotManager.active == null ? t("không", "none") : BotManager.active.name), cx, y, TXT);
        y += 12;
        LitematicaBuilder lb = BotManager.builderTask();
        txt(g, t("Đã đào: ", "Mined: ") + Stats.mined + "   " + t("Đã nhặt: ", "Collected: ") + Stats.collected + "   "
                + t("Đã xây: ", "Built: ") + Stats.built + "   Layer: " + (lb.layers > 0 ? (lb.layer + 1) + "/" + lb.layers : "-"), cx, y, TXT);
        y += 14;
        btn(g, mx, my, cx, y, 90, 14, t("Reset thống kê", "Reset stats"), BTN, Stats::reset);
        btn(g, mx, my, cx + 94, y, 90, 14, t("Dừng tất cả", "Stop all"), RED, BotManager::stopAll);
        y += 22;
        Bot[] arr = {BotManager.miner, BotManager.collector, BotManager.builder};
        int cwid = (cw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            Bot b = arr[i];
            int x = cx + i * (cwid + 4);
            rect(g, x, y, cwid, 120, 0xFF1B2030);
            txt(g, b.name, x + 4, y + 4, 0xFF7FB2FF);
            txt(g, stateText(b.task.state), x + 4, y + 16, stateColor(b.task.state));
            String st = textRenderer.trimToWidth(b.task.status, cwid - 8);
            txt(g, st, x + 4, y + 28, DIM);
            bar(g, x + 4, y + 42, cwid - 8, 10, b.task.progress());
            controls(g, mx, my, x + 4, y + 58, cwid - 8, b);
            final Bot fb = b;
            tog(g, mx, my, x + 4, y + 80, cwid - 8, t("Bật hệ thống", "Enabled"), c.enabled(b.type), () -> {
                switch (fb.type) {
                    case MINE -> c.moduleMiner = !c.moduleMiner;
                    case COLLECT -> c.moduleCollector = !c.moduleCollector;
                    case BUILD -> c.moduleBuilder = !c.moduleBuilder;
                }
                if (!c.enabled(fb.type)) fb.task.stop();
            });
        }
        y += 126;
        tog(g, mx, my, cx, y, 180, "Block Scanner", c.moduleScanner, () -> c.moduleScanner = !c.moduleScanner);
    }

    // ----------------------- Auto Miner -----------------------
    private void miner(DrawContext g, int mx, int my) {
        Config c = Config.get();
        int lw = (cw - 8) / 2, rx = cx + lw + 8;
        field(g, mx, my, fSearch, cx, cy, lw, t("Tìm block (vd: diamond)...", "Search block (e.g. diamond)..."));
        List<Block> fl = blocks(fSearch.text);
        txt(g, t("Danh sách block (", "Block list (") + fl.size() + ")", cx, cy + 18, DIM);
        list(g, mx, my, "mblocks", cx, cy + 30, lw, ch - 30, fl,
                b -> b.getName().getString() + " (" + Registries.BLOCK.getId(b).getPath() + ")",
                b -> c.minerBlocks.contains(Tools.blockId(b)),
                b -> { String id = Tools.blockId(b); if (!c.minerBlocks.remove(id)) c.minerBlocks.add(id); Config.save(); });
        Bot b = BotManager.miner;
        controls(g, mx, my, rx, cy, lw, b);
        txt(g, stateText(b.task.state) + ": " + textRenderer.trimToWidth(b.task.status, lw - 60), rx, cy + 21, stateColor(b.task.state));
        txt(g, t("Đã đào: ", "Mined: ") + ((AutoMiner) b.task).minedCount, rx, cy + 32, TXT);
        tog(g, mx, my, rx, cy + 44, lw, t("Tự chuyển công cụ", "Auto switch tool"), c.autoTool, () -> c.autoTool = !c.autoTool);
        tog(g, mx, my, rx, cy + 60, lw, t("Chỉ đào block đã chọn", "Mine selected only"), c.onlyMineSelected, () -> c.onlyMineSelected = !c.onlyMineSelected);
        tog(g, mx, my, rx, cy + 76, lw, t("Tự nhặt sau khi đào", "Auto collect drops"), c.autoCollect, () -> c.autoCollect = !c.autoCollect);
        stepper(g, mx, my, rx, cy + 94, lw, t("Khoảng cách tìm", "Search distance"), c.mineRadius, 2, 4, 48, v -> c.mineRadius = v);
        txt(g, t("Block đã chọn (click để bỏ):", "Selected (click to remove):"), rx, cy + 114, DIM);
        List<String> sel = new ArrayList<>(c.minerBlocks);
        list(g, mx, my, "msel", rx, cy + 126, lw, ch - 126, sel, s -> s.replace("minecraft:", ""), s -> false,
                s -> { c.minerBlocks.remove(s); Config.save(); });
    }

    // ----------------------- Block Scanner -----------------------
    private void scanner(DrawContext g, int mx, int my) {
        Config c = Config.get();
        BlockScanner sc = BotManager.SCANNER;
        field(g, mx, my, fSearch, cx, cy, cw - 190, t("Tìm block theo tên...", "Search block by name..."));
        btn(g, mx, my, cx + cw - 184, cy, 90, 14, t("Quét lại", "Rescan"), ACC, sc::restart);
        stepper(g, mx, my, cx + cw - 90, cy, 90, "R", c.scanRadius, 2, 4, 48, v -> c.scanRadius = v);
        if (!c.moduleScanner) { txt(g, t("Block Scanner đang tắt (bật ở Dashboard)", "Block Scanner is off (enable in Dashboard)"), cx, cy + 22, 0xFFFF7070); return; }
        String q = fSearch.text.trim().toLowerCase();
        List<Map.Entry<Block, Integer>> es = new ArrayList<>();
        int types = 0, sum = 0;
        for (var e : sc.counts.entrySet()) {
            types++;
            sum += e.getValue();
            String key = (Registries.BLOCK.getId(e.getKey()).getPath() + " " + e.getKey().getName().getString()).toLowerCase();
            if (q.isEmpty() || key.contains(q)) es.add(e);
        }
        es.sort((a, b2) -> b2.getValue() - a.getValue());
        txt(g, t("Chu kỳ: ", "Cycle: ") + sc.cycles + "  " + t("Loại: ", "Types: ") + types + "  " + t("Tổng block: ", "Blocks: ") + sum
                + "  " + t("Tâm: ", "Center: ") + sc.center.toShortString(), cx, cy + 20, DIM);
        bar(g, cx, cy + 32, cw, 8, sc.cycleProgress());
        txt(g, t("Click một dòng để thêm/bỏ khỏi Auto Miner", "Click a row to add/remove it in Auto Miner"), cx, cy + 44, DIM);
        list(g, mx, my, "scan", cx, cy + 56, cw, ch - 56, es,
                e -> e.getValue() + " x  " + e.getKey().getName().getString() + " (" + Registries.BLOCK.getId(e.getKey()).getPath() + ")",
                e -> c.minerBlocks.contains(Tools.blockId(e.getKey())),
                e -> { String id = Tools.blockId(e.getKey()); if (!c.minerBlocks.remove(id)) c.minerBlocks.add(id); Config.save(); });
    }

    // ----------------------- Item Collector -----------------------
    private void collector(DrawContext g, int mx, int my) {
        Config c = Config.get();
        int lw = (cw - 8) / 2, rx = cx + lw + 8;
        field(g, mx, my, fSearch, cx, cy, lw, t("Tìm vật phẩm...", "Search item..."));
        btn(g, mx, my, cx, cy + 18, lw / 2 - 2, 14, t("Thêm → Nhặt", "Add → Collect"), collectTargetIsList ? GRN : BTN, () -> collectTargetIsList = true);
        btn(g, mx, my, cx + lw / 2 + 2, cy + 18, lw / 2 - 2, 14, t("Thêm → Bỏ qua", "Add → Ignore"), !collectTargetIsList ? RED : BTN, () -> collectTargetIsList = false);
        List<Item> fl = items(fSearch.text);
        list(g, mx, my, "items", cx, cy + 36, lw, ch - 36, fl,
                i -> i.getName().getString() + " (" + Registries.ITEM.getId(i).getPath() + ")",
                i -> c.collectItems.contains(Tools.itemId(i)) || c.ignoreItems.contains(Tools.itemId(i)),
                i -> {
                    String id = Tools.itemId(i);
                    List<String> target = collectTargetIsList ? c.collectItems : c.ignoreItems;
                    List<String> other = collectTargetIsList ? c.ignoreItems : c.collectItems;
                    other.remove(id);
                    if (!target.remove(id)) target.add(id);
                    Config.save();
                });
        Bot b = BotManager.collector;
        controls(g, mx, my, rx, cy, lw, b);
        txt(g, stateText(b.task.state) + ": " + textRenderer.trimToWidth(b.task.status, lw - 60), rx, cy + 21, stateColor(b.task.state));
        txt(g, t("Đã nhặt: ", "Collected: ") + Stats.collected, rx, cy + 32, TXT);
        tog(g, mx, my, rx, cy + 44, lw, t("Chỉ nhặt vật phẩm trong danh sách", "Only listed items"), c.collectOnlyListed, () -> c.collectOnlyListed = !c.collectOnlyListed);
        stepper(g, mx, my, rx, cy + 62, lw, t("Phạm vi tìm vật phẩm", "Item range"), c.itemRadius, 1, 3, 32, v -> c.itemRadius = v);
        int half = (ch - 82) / 2;
        txt(g, t("Danh sách NHẶT (click để bỏ):", "COLLECT list (click to remove):"), rx, cy + 82, 0xFF7CF0B0);
        list(g, mx, my, "clist", rx, cy + 93, lw, half - 14, new ArrayList<>(c.collectItems), s -> s.replace("minecraft:", ""), s -> false,
                s -> { c.collectItems.remove(s); Config.save(); });
        txt(g, t("Danh sách BỎ QUA:", "IGNORE list:"), rx, cy + 93 + half - 10, 0xFFFF9090);
        list(g, mx, my, "ilist", rx, cy + 104 + half - 10, lw, ch - (104 + half - 10), new ArrayList<>(c.ignoreItems), s -> s.replace("minecraft:", ""), s -> false,
                s -> { c.ignoreItems.remove(s); Config.save(); });
    }

    // ----------------------- Litematica Builder -----------------------
    private void builder(DrawContext g, int mx, int my) {
        Bot b = BotManager.builder;
        LitematicaBuilder lb = BotManager.builderTask();
        MinecraftClient mc = MinecraftClient.getInstance();
        int y = cy;
        txt(g, "Litematica: " + (LitematicaBridge.loaded() ? t("đã cài", "installed") : t("CHƯA CÀI - tính năng này cần Litematica", "NOT INSTALLED - required for this feature")),
                cx, y, LitematicaBridge.loaded() ? 0xFF7CF0B0 : 0xFFFF7070);
        y += 12;
        controls(g, mx, my, cx, y, 200, b);
        btn(g, mx, my, cx + 206, y, 90, 16, t("Phân tích lại", "Re-analyze"), ACC, lb::reanalyze);
        y += 22;
        btn(g, mx, my, cx, y, 70, 14, "< Prev Layer", BTN, lb::prevLayer);
        btn(g, mx, my, cx + 74, y, 70, 14, "Next Layer >", BTN, lb::nextLayer);
        btn(g, mx, my, cx + 150, y, 150, 14, lb.mode == LitematicaBuilder.Mode.ALL ? t("Chế độ: Tự xây toàn bộ", "Mode: Build all") : t("Chế độ: Từng layer", "Mode: Layer by layer"), YEL,
                () -> lb.mode = lb.mode == LitematicaBuilder.Mode.ALL ? LitematicaBuilder.Mode.LAYER : LitematicaBuilder.Mode.ALL);
        y += 20;
        txt(g, stateText(b.task.state) + ": " + textRenderer.trimToWidth(b.task.status, cw - 70), cx, y, stateColor(b.task.state));
        y += 12;
        if (!lb.error.isEmpty() && b.task.state != Task.State.RUNNING) {
            txt(g, textRenderer.trimToWidth(lb.error, cw), cx, y, 0xFFFF9090);
        }
        y += 12;
        txt(g, "Layer: " + (lb.layers > 0 ? (lb.layer + 1) + "/" + lb.layers : "-") + "   "
                + t("Block xây xong: ", "Built: ") + lb.builtBlocks() + "/" + lb.totalBlocks()
                + (lb.analyzing ? "   (" + t("đang phân tích ", "analyzing ") + (int) (lb.analysisProgress() * 100) + "%)" : ""), cx, y, TXT);
        y += 12;
        bar(g, cx, y, cw, 10, lb.progress());
        y += 16;
        txt(g, t("Vật liệu còn thiếu  (thiếu / tổng cần / đang có)", "Missing materials  (missing / total / in inventory)"), cx, y, DIM);
        y += 12;
        List<Map.Entry<Block, Integer>> es = new ArrayList<>(lb.missing.entrySet());
        es.sort((a, c2) -> c2.getValue() - a.getValue());
        list(g, mx, my, "mats", cx, y, cw, ch - (y - cy), es, e -> {
            int have = mc.player == null ? 0 : Tools.count(mc.player, e.getKey().asItem());
            return e.getValue() + " / " + lb.required.getOrDefault(e.getKey(), 0) + " / " + have + "   " + e.getKey().getName().getString();
        }, e -> false, e -> {});
    }

    // ----------------------- Bot Manager -----------------------
    private void botManager(DrawContext g, int mx, int my) {
        field(g, mx, my, fName, cx, cy, 140, t("Tên bot", "Bot name"));
        btn(g, mx, my, cx + 146, cy, 120, 14, Bot.typeName(newType), BTN, () -> newType = Bot.Type.values()[(newType.ordinal() + 1) % Bot.Type.values().length]);
        btn(g, mx, my, cx + 272, cy, 90, 14, t("Tạo bot", "Create bot"), GRN, () -> {
            String n = fName.text.trim().isEmpty() ? "Bot " + (BotManager.bots.size() + 1) : fName.text.trim();
            BotManager.add(new Bot(n, newType, false));
            fName.text = "Bot " + (BotManager.bots.size() + 1);
        });
        txt(g, t("Chỉ có 1 người chơi thật nên các bot đang chạy sẽ thay phiên nhau theo thứ tự danh sách.", "Only one real player exists, so running bots take turns in list order."), cx, cy + 18, DIM);
        int top = cy + 30, rowH = 30;
        int rows = Math.max(1, (ch - 30) / rowH);
        int max = Math.max(0, BotManager.bots.size() - rows);
        int sc = Math.min(max, Math.max(0, scroll.getOrDefault("bots", 0)));
        scroll.put("bots", sc);
        listRects.add(new int[]{cx, top, cw, ch - 30, max});
        listKeys.add("bots");
        List<Bot> snapshot = new ArrayList<>(BotManager.bots);
        for (int i = 0; i < rows && sc + i < snapshot.size(); i++) {
            Bot b = snapshot.get(sc + i);
            int y = top + i * rowH;
            rect(g, cx, y, cw, rowH - 2, 0xFF1B2030);
            boolean act = BotManager.active == b;
            txt(g, b.name + "  [" + Bot.typeName(b.type) + "]" + (act ? "  ◀" : ""), cx + 4, y + 3, act ? 0xFF7CF0B0 : 0xFF7FB2FF);
            txt(g, stateText(b.task.state) + " - " + textRenderer.trimToWidth(b.task.status, cw - 330), cx + 4, y + 15, stateColor(b.task.state));
            bar(g, cx + cw - 320, y + 3, 80, 10, b.task.progress());
            int bx = cx + cw - 234;
            btn(g, mx, my, bx, y + 2, 48, 12, b.task.state == Task.State.PAUSED ? "Resume" : "Start", GRN,
                    () -> { if (b.task.state == Task.State.PAUSED) b.task.resume(); else b.start(); });
            btn(g, mx, my, bx + 50, y + 2, 40, 12, "Pause", YEL, b.task::pause);
            btn(g, mx, my, bx + 92, y + 2, 40, 12, "Stop", RED, b.task::stop);
            if (!b.builtin) btn(g, mx, my, bx + 134, y + 2, 14, 12, "X", RED, () -> BotManager.remove(b));
        }
    }

    // ----------------------- Settings -----------------------
    private void settings(DrawContext g, int mx, int my) {
        Config c = Config.get();
        int w = Math.min(cw, 330), y = cy;
        stepper(g, mx, my, cx, y, w, t("Phạm vi quét block", "Block scan range"), c.scanRadius, 2, 4, 48, v -> c.scanRadius = v); y += 20;
        stepper(g, mx, my, cx, y, w, t("Phạm vi đào (Auto Miner)", "Mining range"), c.mineRadius, 2, 4, 48, v -> c.mineRadius = v); y += 20;
        stepper(g, mx, my, cx, y, w, t("Phạm vi tìm vật phẩm", "Item search range"), c.itemRadius, 1, 3, 32, v -> c.itemRadius = v); y += 20;
        stepper(g, mx, my, cx, y, w, t("Độ trễ hành động (tick)", "Action delay (ticks)"), c.actionDelay, 1, 1, 20, v -> c.actionDelay = v); y += 22;
        String modeLabel = switch (c.mode) { case "SAFE" -> t("An toàn (chậm)", "Safe (slow)"); case "FAST" -> t("Nhanh", "Fast"); default -> t("Bình thường", "Normal"); };
        txt(g, t("Chế độ hoạt động", "Operation mode"), cx, y + 3, TXT);
        btn(g, mx, my, cx + w - 120, y, 120, 14, modeLabel, BTN, () -> {
            c.mode = switch (c.mode) { case "SAFE" -> "NORMAL"; case "NORMAL" -> "FAST"; default -> "SAFE"; };
            Config.save();
        });
        y += 22;
        txt(g, t("Phím mở menu", "Menu key"), cx, y + 3, TXT);
        String key = capturing ? t("Nhấn phím...", "Press a key...") : net.minecraft.client.util.InputUtil.fromKeyCode(c.openKey, 0).getLocalizedText().getString();
        btn(g, mx, my, cx + w - 120, y, 120, 14, key, capturing ? YEL : BTN, () -> capturing = true);
        y += 22;
        tog(g, mx, my, cx, y, w, t("Mở menu bằng SHIFT + chuột phải (tay trống)", "Open menu with SHIFT + right click (empty hand)"), c.shiftClickOpen, () -> c.shiftClickOpen = !c.shiftClickOpen);
        y += 22;
        txt(g, t("Ngôn ngữ", "Language"), cx, y + 3, TXT);
        btn(g, mx, my, cx + w - 120, y, 58, 14, "Tiếng Việt", "vi".equals(c.lang) ? ACC : BTN, () -> { c.lang = "vi"; Config.save(); });
        btn(g, mx, my, cx + w - 58, y, 58, 14, "English", "en".equals(c.lang) ? ACC : BTN, () -> { c.lang = "en"; Config.save(); });
        y += 26;
        btn(g, mx, my, cx, y, 120, 16, t("Lưu cấu hình", "Save config"), GRN, Config::save);
        txt(g, t("Cấu hình tự lưu khi đổi và khi thoát game.", "Config auto-saves on change and on exit."), cx + 126, y + 4, DIM);
    }

    // ===================== input =====================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            active = null;
            for (int i = clicks.size() - 1; i >= 0; i--) {
                Click c = clicks.get(i);
                if (in((int) mx, (int) my, c.x, c.y, c.w, c.h)) { c.r.run(); return true; }
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        for (int i = 0; i < listRects.size(); i++) {
            int[] r = listRects.get(i);
            if (in((int) mx, (int) my, r[0], r[1], r[2], r[3])) {
                String k = listKeys.get(i);
                int s = scroll.getOrDefault(k, 0) + (v > 0 ? -3 : 3);
                scroll.put(k, Math.max(0, Math.min(r[4], s)));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (capturing) {
            if (key != 256) { Config.get().openKey = key; Config.save(); }
            capturing = false;
            return true;
        }
        if (active != null) {
            if (key == 259 && !active.text.isEmpty()) active.text = active.text.substring(0, active.text.length() - 1);
            else if (key == 257 || key == 335) {
                if (active == fFeature) { List<Integer> v = visibleTabs(); if (!v.isEmpty()) tab = v.get(0); }
                active = null;
            } else if (key == 256) active = null;
            return true;
        }
        if (key == Config.get().openKey || key == 256) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (active != null && chr >= 32 && chr != 127 && active.text.length() < 40) { active.text += chr; return true; }
        return false;
    }
}
