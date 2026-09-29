package dev.donutauction;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Small countdown panel drawn while an auction is scheduled or running. */
public final class AuctionHud {
    private AuctionHud() {}

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.of(AuctionMod.MOD_ID, "countdown"),
                AuctionHud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        AuctionConfig cfg = AuctionConfig.get();
        if (!cfg.hudEnabled) return;

        AuctionManager.State st = AuctionManager.state();
        if (st == AuctionManager.State.IDLE) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = cfg.hudX;
        int y = cfg.hudY;
        int w = 156;
        int h = 40;

        Gfx.roundedRect(ctx, x - 1, y - 1, w + 2, h + 2, Gfx.ACCENT_DIM);
        Gfx.roundedRect(ctx, x, y, w, h, Gfx.PANEL);

        ctx.drawItem(AuctionManager.icon(), x + 6, y + 6);

        String name = tr.trimToWidth(AuctionManager.itemName(), w - 36);
        ctx.drawTextWithShadow(tr, Text.literal(name), x + 28, y + 7, Gfx.TEXT);

        String line;
        int color = Gfx.ACCENT;
        switch (st) {
            case SCHEDULED -> line = "Starts in " + TimeUtil.formatClock(AuctionManager.secondsUntilStart());
            case STARTING -> line = "Listing item...";
            default -> {
                long left = AuctionManager.secondsRemaining();
                line = "Ends in " + TimeUtil.formatClock(left);
                if (left <= 10) color = Gfx.ERR;
            }
        }
        ctx.drawTextWithShadow(tr, Text.literal(line), x + 28, y + 19, color);

        if (st == AuctionManager.State.RUNNING) {
            int bx = x + 6;
            int bw = w - 12;
            ctx.fill(bx, y + 32, bx + bw, y + 35, Gfx.TRACK);
            int filled = Math.round(bw * AuctionManager.progress());
            if (filled > 0) ctx.fill(bx, y + 32, bx + filled, y + 35, color);
        }
    }
}
