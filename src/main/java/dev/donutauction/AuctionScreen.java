package dev.donutauction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.function.Consumer;

/** The auction menu: pick a hotbar item, set price / duration / start time, manage presets. */
public class AuctionScreen extends Screen {
    private static final int W = 344;
    private static final int H = 290;

    private int x0;
    private int y0;

    private int selectedSlot;
    private String priceVal;
    private String buyNowVal;
    private String durationVal;
    private String startVal;
    private String presetVal = "";
    private boolean announce;
    private int presetIndex = -1;

    private String status = "";
    private int statusColor = Gfx.TEXT_DIM;

    private TextFieldWidget priceField;
    private TextFieldWidget buyNowField;
    private TextFieldWidget durationField;
    private TextFieldWidget startField;
    private TextFieldWidget presetField;
    private ButtonWidget announceButton;

    public AuctionScreen() {
        super(Text.literal("Auction Manager"));
        AuctionConfig.Preset last = AuctionConfig.get().last;
        priceVal = nz(last.price);
        buyNowVal = nz(last.buyNow);
        durationVal = nz(last.duration).isEmpty() ? "15m" : last.duration;
        startVal = nz(last.startIn);
        announce = last.announce;

        MinecraftClient mc = MinecraftClient.getInstance();
        selectedSlot = 0;
        if (mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            selectedSlot = inv.getSelectedSlot();
            int match = slotOf(inv, last.itemId);
            if (match >= 0) selectedSlot = match;
        }
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = (height - H) / 2;

        // Hotbar slot buttons (icons are drawn on top in render()).
        int sx = x0 + (W - (9 * 26 - 2)) / 2;
        for (int i = 0; i < 9; i++) {
            final int slot = i;
            addDrawableChild(ButtonWidget.builder(Text.empty(), b -> selectedSlot = slot)
                    .dimensions(sx + i * 26, y0 + 42, 24, 24).build());
        }

        priceField = field(x0 + 16, y0 + 96, 150, priceVal, "e.g. 25k or 1.5m", s -> priceVal = s);
        buyNowField = field(x0 + 180, y0 + 96, 148, buyNowVal, "optional", s -> buyNowVal = s);

        durationField = field(x0 + 16, y0 + 134, 70, durationVal, "15m", s -> durationVal = s);
        String[] presets = {"5m", "15m", "30m", "1h"};
        for (int i = 0; i < presets.length; i++) {
            final String value = presets[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(value), b -> durationField.setText(value))
                    .dimensions(x0 + 92 + i * 36, y0 + 134, 34, 18).build());
        }

        startField = field(x0 + 16, y0 + 172, 150, startVal, "now, 10m or 14:30", s -> startVal = s);
        announceButton = addDrawableChild(ButtonWidget.builder(announceLabel(), b -> {
            announce = !announce;
            b.setMessage(announceLabel());
        }).dimensions(x0 + 180, y0 + 172, 148, 18).build());

        // Presets row
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> cyclePreset(-1))
                .dimensions(x0 + 16, y0 + 208, 20, 18).build());
        presetField = field(x0 + 38, y0 + 208, 126, presetVal, "preset name", s -> presetVal = s);
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> cyclePreset(1))
                .dimensions(x0 + 166, y0 + 208, 20, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> savePreset())
                .dimensions(x0 + 192, y0 + 208, 66, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> deletePreset())
                .dimensions(x0 + 262, y0 + 208, 66, 18).build());

        // Bottom actions
        addDrawableChild(ButtonWidget.builder(Text.literal("Start Auction").formatted(Formatting.GREEN), b -> onStart())
                .dimensions(x0 + 16, y0 + 258, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel Auction").formatted(Formatting.RED), b -> {
            AuctionManager.cancel(MinecraftClient.getInstance());
            setStatus("Cancel requested.", Gfx.TEXT_DIM);
        }).dimensions(x0 + 162, y0 + 258, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x0 + 258, y0 + 258, 70, 20).build());
    }

    private TextFieldWidget field(int x, int y, int w, String value, String hint, Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.empty());
        f.setMaxLength(40);
        f.setPlaceholder(Text.literal(hint).formatted(Formatting.DARK_GRAY));
        f.setText(value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return f;
    }

    private Text announceLabel() {
        return Text.literal("Chat announce: " + (announce ? "ON" : "OFF"))
                .formatted(announce ? Formatting.GREEN : Formatting.GRAY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ actions

    private AuctionConfig.Preset collect(ItemStack stack, String name) {
        AuctionConfig.Preset p = new AuctionConfig.Preset();
        p.name = name;
        p.itemId = Registries.ITEM.getId(stack.getItem()).toString();
        p.price = priceVal;
        p.buyNow = buyNowVal;
        p.duration = durationVal;
        p.startIn = startVal;
        p.announce = announce;
        return p;
    }

    private ItemStack selectedStack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getStack(selectedSlot);
    }

    private void onStart() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack stack = selectedStack();
        if (stack.isEmpty()) {
            setStatus("Pick a hotbar slot that holds the item you want to auction.", Gfx.ERR);
            return;
        }
        String err = AuctionManager.start(mc, collect(stack, ""), selectedSlot);
        if (err != null) {
            setStatus(err, Gfx.ERR);
            return;
        }
        close();
    }

    private void savePreset() {
        String name = presetVal.trim();
        if (name.isEmpty()) {
            setStatus("Type a preset name first.", Gfx.ERR);
            return;
        }
        ItemStack stack = selectedStack();
        if (stack.isEmpty()) {
            setStatus("Pick the item to save in the preset first.", Gfx.ERR);
            return;
        }
        AuctionConfig.get().upsertPreset(collect(stack, name));
        setStatus("Saved preset '" + name + "'.", Gfx.OK);
    }

    private void deletePreset() {
        String name = presetVal.trim();
        if (!name.isEmpty() && AuctionConfig.get().removePreset(name)) {
            presetIndex = -1;
            setStatus("Deleted preset '" + name + "'.", Gfx.OK);
        } else {
            setStatus("No preset with that name.", Gfx.ERR);
        }
    }

    private void cyclePreset(int dir) {
        List<AuctionConfig.Preset> list = AuctionConfig.get().presets;
        if (list.isEmpty()) {
            setStatus("No saved presets yet.", Gfx.TEXT_DIM);
            return;
        }
        presetIndex = Math.floorMod(presetIndex + dir, list.size());
        applyPreset(list.get(presetIndex));
    }

    private void applyPreset(AuctionConfig.Preset p) {
        priceField.setText(nz(p.price));
        buyNowField.setText(nz(p.buyNow));
        durationField.setText(nz(p.duration));
        startField.setText(nz(p.startIn));
        presetField.setText(nz(p.name));
        announce = p.announce;
        announceButton.setMessage(announceLabel());

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int slot = slotOf(mc.player.getInventory(), p.itemId);
            if (slot >= 0) {
                selectedSlot = slot;
                setStatus("Loaded preset '" + p.name + "'.", Gfx.TEXT_DIM);
            } else {
                setStatus("Loaded '" + p.name + "', but its item isn't in your hotbar.", Gfx.ERR);
            }
        }
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);

        Gfx.roundedRect(ctx, x0 - 1, y0 - 1, W + 2, H + 2, Gfx.ACCENT_DIM);
        Gfx.roundedRect(ctx, x0, y0, W, H, Gfx.PANEL);
        Gfx.roundedRect(ctx, x0 + 1, y0 + 1, W - 2, 26, Gfx.PANEL_LIGHT);
        ctx.fill(x0 + 1, y0 + 27, x0 + W - 1, y0 + 28, Gfx.ACCENT);

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Auction Manager").formatted(Formatting.BOLD), x0 + 14, y0 + 10, Gfx.ACCENT);

        label(ctx, "ITEM  (pick a hotbar slot)", x0 + 16, y0 + 31);
        label(ctx, "START PRICE", x0 + 16, y0 + 84);
        label(ctx, "BUY-NOW PRICE", x0 + 180, y0 + 84);
        label(ctx, "DURATION", x0 + 16, y0 + 122);
        label(ctx, "START IN / START AT", x0 + 16, y0 + 160);
        label(ctx, "PRESETS", x0 + 16, y0 + 196);
    }

    private void label(DrawContext ctx, String text, int x, int y) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(text), x, y, Gfx.TEXT_DIM);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            PlayerInventory inv = mc.player.getInventory();
            int sx = x0 + (W - (9 * 26 - 2)) / 2;
            for (int i = 0; i < 9; i++) {
                int bx = sx + i * 26;
                int by = y0 + 42;
                ItemStack st = inv.getStack(i);
                ctx.drawItem(st, bx + 4, by + 4);
                ctx.drawStackOverlay(textRenderer, st, bx + 4, by + 4);
                if (i == selectedSlot) {
                    Gfx.border(ctx, bx - 1, by - 1, 26, 26, Gfx.ACCENT);
                    Gfx.border(ctx, bx, by, 24, 24, Gfx.ACCENT);
                }
            }

            ItemStack sel = inv.getStack(selectedSlot);
            Text line = sel.isEmpty()
                    ? Text.literal("Selected: (empty slot)")
                    : Text.literal("Selected: " + sel.getName().getString() + " x" + sel.getCount());
            int tw = textRenderer.getWidth(line);
            ctx.drawTextWithShadow(textRenderer, line, x0 + (W - tw) / 2, y0 + 70, sel.isEmpty() ? Gfx.ERR : Gfx.TEXT);
        }

        String shown = status.isEmpty() ? AuctionManager.statusLine() : status;
        int color = status.isEmpty() ? Gfx.TEXT_DIM : statusColor;
        ctx.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(shown, W - 32)),
                x0 + 16, y0 + 238, color);
    }

    // ------------------------------------------------------------------ small helpers

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int slotOf(PlayerInventory inv, String itemId) {
        if (itemId == null || itemId.isEmpty()) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && Registries.ITEM.getId(st.getItem()).toString().equals(itemId)) return i;
        }
        return -1;
    }
}
