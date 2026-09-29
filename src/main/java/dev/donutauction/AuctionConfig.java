package dev.donutauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** JSON config stored at .minecraft/config/donutauction.json (Lunar uses its own game dir). */
public class AuctionConfig {

    /** One auction definition. Also used for named presets and the "last used" settings. */
    public static class Preset {
        public String name = "";
        public String itemId = "";
        public String price = "";
        public String buyNow = "";
        public String duration = "15m";
        public String startIn = "";
        public boolean announce = false;

        public Preset copy() {
            Preset c = new Preset();
            c.name = name;
            c.itemId = itemId;
            c.price = price;
            c.buyNow = buyNow;
            c.duration = duration;
            c.startIn = startIn;
            c.announce = announce;
            return c;
        }
    }

    /** Command sent to the server, without the leading slash. CHANGE THIS to match DonutSMP's syntax. */
    public String sellCommand = "ah sell {price}";
    public boolean hudEnabled = true;
    public int hudX = 6;
    public int hudY = 6;
    /** Minimum gap between anything this mod sends to the server. Never below 1000. */
    public long minCommandDelayMs = 1500;
    /** Chat warnings when this many seconds remain. */
    public int[] warnAtSeconds = {60, 10};
    /** Only used when "Chat announce" is on. Placeholders: {item} {price} {buynow} {time} */
    public String announceMessage = "Auctioning {item} - starting at {price}! Ends in {time}.";
    public Preset last = new Preset();
    public List<Preset> presets = new ArrayList<>();

    // ------------------------------------------------------------------ storage

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AuctionConfig instance;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutauction.json");
    }

    public static synchronized AuctionConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        AuctionConfig loaded = null;
        Path p = file();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                loaded = GSON.fromJson(r, AuctionConfig.class);
            } catch (Exception e) {
                AuctionMod.LOGGER.warn("Could not read donutauction.json, using defaults", e);
            }
        }
        instance = loaded != null ? loaded : new AuctionConfig();
        instance.sanitize();
        instance.save();
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            AuctionMod.LOGGER.warn("Could not save donutauction.json", e);
        }
    }

    private void sanitize() {
        if (last == null) last = new Preset();
        if (presets == null) presets = new ArrayList<>();
        presets.removeIf(Objects::isNull);
        if (sellCommand == null || sellCommand.isBlank()) sellCommand = "ah sell {price}";
        if (announceMessage == null) announceMessage = "Auctioning {item} - starting at {price}! Ends in {time}.";
        if (warnAtSeconds == null) warnAtSeconds = new int[0];
        if (minCommandDelayMs < 1000) minCommandDelayMs = 1000;
    }

    // ------------------------------------------------------------------ presets

    public Preset findPreset(String name) {
        if (name == null) return null;
        for (Preset p : presets) {
            if (p.name != null && p.name.equalsIgnoreCase(name.trim())) return p;
        }
        return null;
    }

    public void upsertPreset(Preset preset) {
        presets.removeIf(p -> p.name != null && p.name.equalsIgnoreCase(preset.name));
        presets.add(preset);
        save();
    }

    public boolean removePreset(String name) {
        boolean removed = presets.removeIf(p -> p.name != null && p.name.equalsIgnoreCase(name.trim()));
        if (removed) save();
        return removed;
    }

    public List<String> presetNames() {
        List<String> names = new ArrayList<>();
        for (Preset p : presets) names.add(p.name);
        return names;
    }
}
