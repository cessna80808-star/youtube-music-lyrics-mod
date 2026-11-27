package dev.buffy.ytlyrics;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the lyric card sits and how large it is, stored as fractions of the screen so the same
 * layout survives resolution and GUI-scale changes. Saved to {@code config/ytlyrics.json}.
 */
public final class HudConfig {
    public static final float MIN_SCALE = 0.6f;
    public static final float MAX_SCALE = 1.8f;

    /** Card centre, as a fraction of the screen. The default is the original spot above the hotbar. */
    private static final float DEFAULT_X = 0.5f;
    private static final float DEFAULT_Y = 0.733f;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ytlyrics.json");

    private static float x = DEFAULT_X;
    private static float y = DEFAULT_Y;
    private static float scale = 1f;

    private HudConfig() {}

    public static float x() {
        return x;
    }

    public static float y() {
        return y;
    }

    public static float scale() {
        return scale;
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) return;

        try {
            JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            x = clamp(read(json, "x", DEFAULT_X), 0f, 1f);
            y = clamp(read(json, "y", DEFAULT_Y), 0f, 1f);
            scale = clamp(read(json, "scale", 1f), MIN_SCALE, MAX_SCALE);
        } catch (Exception e) {
            YtLyricsMod.LOGGER.warn("ytlyrics: could not read {}: {}", FILE, e.toString());
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject json = new JsonObject();
            json.addProperty("x", round(x));
            json.addProperty("y", round(y));
            json.addProperty("scale", round(scale));

            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(json),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            YtLyricsMod.LOGGER.warn("ytlyrics: could not save {}: {}", FILE, e.toString());
        }
    }

    public static void moveTo(float newX, float newY) {
        x = clamp(newX, 0f, 1f);
        y = clamp(newY, 0f, 1f);
    }

    public static void resize(float step) {
        scale = clamp(scale + step, MIN_SCALE, MAX_SCALE);
    }

    public static void reset() {
        x = DEFAULT_X;
        y = DEFAULT_Y;
        scale = 1f;
    }

    private static float read(JsonObject json, String field, float fallback) {
        try {
            return json.has(field) ? json.get(field).getAsFloat() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float round(float value) {
        return Math.round(value * 1000f) / 1000f;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
