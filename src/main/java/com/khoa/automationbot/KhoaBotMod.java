package com.khoa.automationbot;

import com.khoa.automationbot.gui.MainScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.client.util.InputUtil;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.khoa.automationbot.Lang.t;

public class KhoaBotMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("khoa-automation-bot");
    private static boolean keyWas;

    @Override
    public void onInitializeClient() {
        Config.load();
        BotManager.init();
        ClientTickEvents.END_CLIENT_TICK.register(KhoaBotMod::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> { BotManager.stopAll(); Config.save(); });

        UseBlockCallback.EVENT.register((p, w, h, hit) -> {
            if (shouldOpen(p, w, h)) { open(); return ActionResult.SUCCESS; }
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((p, w, h) -> {
            if (shouldOpen(p, w, h)) { open(); return TypedActionResult.success(p.getStackInHand(h)); }
            return TypedActionResult.pass(p.getStackInHand(h));
        });

        HudRenderCallback.EVENT.register((ctx, tc) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || BotManager.active == null) return;
            Task tk = BotManager.active.task;
            ctx.drawTextWithShadow(mc.textRenderer, "[KHOA BOT] " + BotManager.active.name + ": " + tk.status, 6, 6, 0x55FF55);
        });
        LOG.info("KHOA AUTOMATION BOT loaded");
    }

    private static boolean shouldOpen(PlayerEntity p, World w, Hand h) {
        return w.isClient && h == Hand.MAIN_HAND && Config.get().shiftClickOpen
                && p.isSneaking() && p.getMainHandStack().isEmpty();
    }

    private static void open() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> { if (!(mc.currentScreen instanceof MainScreen)) mc.setScreen(new MainScreen()); });
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player != null && mc.getWindow() != null) {
            boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), Config.get().openKey);
            if (down && !keyWas && mc.currentScreen == null) mc.setScreen(new MainScreen());
            keyWas = down;
        }
        BotManager.tick(mc);
    }
}
