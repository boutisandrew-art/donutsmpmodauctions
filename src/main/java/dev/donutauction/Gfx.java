package dev.donutauction;

import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers and the colour palette used by the menu and HUD. */
public final class Gfx {
    public static final int PANEL = 0xFF151922;
    public static final int PANEL_LIGHT = 0xFF1E2430;
    public static final int ACCENT = 0xFFFF7AB8;
    public static final int ACCENT_DIM = 0xFF8A3F63;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF9AA4B5;
    public static final int OK = 0xFF6EE7A0;
    public static final int ERR = 0xFFFF6B6B;
    public static final int TRACK = 0xFF2A3140;

    private Gfx() {}

    /** Filled rectangle with 2px "rounded" corners. */
    public static void roundedRect(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 2, y, x + w - 2, y + h, color);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        c.fill(x, y + 2, x + w, y + h - 2, color);
    }

    /** 1px rectangle outline. */
    public static void border(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x, y, x + w, y + 1, color);
        c.fill(x, y + h - 1, x + w, y + h, color);
        c.fill(x, y + 1, x + 1, y + h - 1, color);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
