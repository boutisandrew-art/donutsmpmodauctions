package dev.donutauction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs one auction at a time: optional scheduled delay -> select the item ->
 * send the sell command -> count down -> notify. Everything sent to the server
 * goes through a minimum-delay gate so it can never spam.
 */
public final class AuctionManager {

    public enum State { IDLE, SCHEDULED, STARTING, RUNNING }

    private static State state = State.IDLE;
    private static AuctionConfig.Preset current = new AuctionConfig.Preset();
    private static int preferredSlot = -1;
    private static int prepTicks = 0;
    private static long startAtMs;
    private static long endAtMs;
    private static long durationSec;
    private static String itemName = "";
    private static ItemStack icon = ItemStack.EMPTY;
    private static long lastSentMs = 0;
    private static final Set<Integer> warned = new HashSet<>();
    private static final ArrayDeque<String> chatQueue = new ArrayDeque<>();

    private AuctionManager() {}

    // ------------------------------------------------------------------ public API

    /**
     * Validates and queues an auction. Returns null on success, or a human-readable
     * error. slotHint is the preferred hotbar slot (0-8) or -1 to search by item id.
     */
    public static String start(MinecraftClient client, AuctionConfig.Preset preset, int slotHint) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) return "Join a server first.";
        if (state != State.IDLE) return "An auction is already active. Use /auc cancel first.";

        String price = TimeUtil.normalizePrice(preset.price);
        if (price == null) return "Enter a valid price, e.g. 5000, 25k or 1.5m.";

        String buyNow = "";
        if (preset.buyNow != null && !preset.buyNow.isBlank()) {
            buyNow = TimeUtil.normalizePrice(preset.buyNow);
            if (buyNow == null) return "The buy-now price isn't valid.";
        }

        long dur = TimeUtil.parseDurationSeconds(preset.duration);
        if (dur < 0) return "Invalid duration. Try 15m, 1h30m or 90s (max 30 days).";

        long delay = TimeUtil.parseStartDelayMillis(preset.startIn);
        if (delay < 0) return "Invalid start time. Try now, 10m, or a clock time like 14:30.";

        if ((preset.itemId == null || preset.itemId.isEmpty()) && slotHint < 0) {
            return "No item saved yet. Open /aucmenu and pick one first.";
        }
        PlayerInventory inv = player.getInventory();
        int slot = findSlot(inv, preset.itemId, slotHint);
        if (slot < 0) return "That item isn't in your hotbar.";
        ItemStack stack = inv.getStack(slot);

        AuctionConfig.Preset p = preset.copy();
        p.price = price;
        p.buyNow = buyNow;
        p.itemId = itemId(stack);

        current = p;
        preferredSlot = slot;
        durationSec = dur;
        itemName = stack.getName().getString();
        icon = stack.copy();
        startAtMs = System.currentTimeMillis() + delay;
        warned.clear();
        chatQueue.clear();
        state = State.SCHEDULED;

        AuctionConfig cfg = AuctionConfig.get();
        cfg.last = p.copy();
        cfg.save();

        if (delay > 0) {
            say(client, "Scheduled " + itemName + " at " + price + " for " + TimeUtil.formatShort(dur)
                    + ". Starts in " + TimeUtil.formatShort(delay / 1000) + ".", Formatting.GREEN);
        } else {
            say(client, "Starting " + itemName + " at " + price + " for " + TimeUtil.formatShort(dur) + "...", Formatting.GREEN);
        }
        return null;
    }

    /** Stops the timer. A listing that was already placed on the server is NOT removed. */
    public static boolean cancel(MinecraftClient client) {
        if (state == State.IDLE) {
            say(client, "No active auction.", Formatting.GRAY);
            return false;
        }
        boolean listed = state == State.RUNNING;
        reset();
        say(client, listed
                ? "Timer cancelled. The listing already placed on the server was NOT removed - cancel it there."
                : "Scheduled auction cancelled.", Formatting.YELLOW);
        return true;
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (player == null || net == null) {
            // Left the world / server: drop everything.
            if (state != State.IDLE) reset();
            chatQueue.clear();
            return;
        }
        long now = System.currentTimeMillis();
        flushChat(net, now);

        switch (state) {
            case SCHEDULED -> {
                if (now >= startAtMs) beginSell(client, player);
            }
            case STARTING -> stepStarting(client, player, net, now);
            case RUNNING -> stepRunning(client, now);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ state steps

    private static void beginSell(MinecraftClient client, ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int slot = findSlot(inv, current.itemId, preferredSlot);
        if (slot < 0) {
            say(client, "Auction aborted: " + itemName + " is no longer in your hotbar.", Formatting.RED);
            reset();
            return;
        }
        preferredSlot = slot;
        inv.setSelectedSlot(slot);
        icon = inv.getStack(slot).copy();
        prepTicks = 3; // give the client a few ticks to sync the selected slot
        state = State.STARTING;
    }

    private static void stepStarting(MinecraftClient client, ClientPlayerEntity player,
                                     ClientPlayNetworkHandler net, long now) {
        if (prepTicks > 0) {
            prepTicks--;
            return;
        }
        AuctionConfig cfg = AuctionConfig.get();
        if (now - lastSentMs < cfg.minCommandDelayMs) return; // rate limit: just wait

        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() != preferredSlot) {
            inv.setSelectedSlot(preferredSlot);
            prepTicks = 2;
            return;
        }
        if (!matches(inv.getStack(preferredSlot), current.itemId)) {
            say(client, "Auction aborted: the item in your hotbar changed.", Formatting.RED);
            reset();
            return;
        }

        String cmd = cfg.sellCommand
                .replace("{price}", current.price)
                .replace("{buynow}", current.buyNow == null ? "" : current.buyNow)
                .trim();
        while (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) {
            say(client, "Auction aborted: sellCommand in the config is empty.", Formatting.RED);
            reset();
            return;
        }

        net.sendChatCommand(cmd);
        lastSentMs = now;
        endAtMs = now + durationSec * 1000L;
        state = State.RUNNING;
        say(client, "Sent /" + cmd + ". Timer running for " + TimeUtil.formatShort(durationSec) + ".", Formatting.GREEN);
        if (current.announce) queueAnnouncement(durationSec);
    }

    private static void stepRunning(MinecraftClient client, long now) {
        long remainingMs = endAtMs - now;
        long remainingSec = (remainingMs + 999) / 1000;

        for (int w : AuctionConfig.get().warnAtSeconds) {
            if (w < durationSec && remainingMs > 0 && remainingSec <= w && warned.add(w)) {
                say(client, itemName + " auction ends in " + TimeUtil.formatShort(w) + ".", Formatting.YELLOW);
                if (current.announce) queueAnnouncement(w);
            }
        }

        if (remainingMs <= 0) {
            say(client, "Auction timer finished for " + itemName + ". Check the auction house.", Formatting.GREEN);
            if (client.player != null) client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            reset();
        }
    }

    // ------------------------------------------------------------------ chat announcements

    private static void queueAnnouncement(long secondsLeft) {
        AuctionConfig cfg = AuctionConfig.get();
        String msg = cfg.announceMessage
                .replace("{item}", itemName)
                .replace("{price}", current.price)
                .replace("{buynow}", current.buyNow == null ? "" : current.buyNow)
                .replace("{time}", TimeUtil.formatShort(secondsLeft))
                .replaceFirst("^/+", "") // never let an announcement turn into a command
                .trim();
        if (msg.length() > 250) msg = msg.substring(0, 250);
        if (!msg.isEmpty()) chatQueue.add(msg);
    }

    private static void flushChat(ClientPlayNetworkHandler net, long now) {
        if (chatQueue.isEmpty()) return;
        if (now - lastSentMs < AuctionConfig.get().minCommandDelayMs) return;
        net.sendChatMessage(chatQueue.poll());
        lastSentMs = now;
    }

    // ------------------------------------------------------------------ helpers

    private static void reset() {
        state = State.IDLE;
        prepTicks = 0;
        warned.clear();
        chatQueue.clear();
    }

    private static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static boolean matches(ItemStack stack, String id) {
        return !stack.isEmpty() && id != null && !id.isEmpty() && itemId(stack).equals(id);
    }

    /** Preferred slot first, then the rest of the hotbar. With no item id, only the hint is used. */
    private static int findSlot(PlayerInventory inv, String id, int hint) {
        if (id == null || id.isEmpty()) {
            return (hint >= 0 && hint < 9 && !inv.getStack(hint).isEmpty()) ? hint : -1;
        }
        if (hint >= 0 && hint < 9 && matches(inv.getStack(hint), id)) return hint;
        for (int i = 0; i < 9; i++) {
            if (matches(inv.getStack(i), id)) return i;
        }
        return -1;
    }

    private static void say(MinecraftClient client, String text, Formatting color) {
        if (client.player == null) {
            AuctionMod.LOGGER.info("[Auction] {}", text);
            return;
        }
        Text msg = Text.empty()
                .append(Text.literal("[Auction] ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(text).formatted(color));
        client.player.sendMessage(msg, false);
    }

    // ------------------------------------------------------------------ read-only info (HUD / status)

    public static State state() { return state; }

    public static String itemName() { return itemName; }

    public static ItemStack icon() { return icon; }

    public static long secondsUntilStart() {
        return Math.max(0, (startAtMs - System.currentTimeMillis() + 999) / 1000);
    }

    public static long secondsRemaining() {
        return Math.max(0, (endAtMs - System.currentTimeMillis() + 999) / 1000);
    }

    /** 0..1 fraction of the auction time that has elapsed. */
    public static float progress() {
        if (state != State.RUNNING || durationSec <= 0) return 0f;
        float elapsed = durationSec - (endAtMs - System.currentTimeMillis()) / 1000f;
        return Math.max(0f, Math.min(1f, elapsed / durationSec));
    }

    public static String statusLine() {
        return switch (state) {
            case IDLE -> "No active auction.";
            case SCHEDULED -> itemName + " starts in " + TimeUtil.formatClock(secondsUntilStart());
            case STARTING -> "Listing " + itemName + "...";
            case RUNNING -> itemName + " ends in " + TimeUtil.formatClock(secondsRemaining());
        };
    }
}
