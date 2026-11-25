package dev.buffy.ytlyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Animated lyric card above the hotbar: rounded glass panel, karaoke highlight, soft fades.
 *
 * <p>The card is anchored {@link #BOTTOM_MARGIN} pixels above the bottom of the screen, clear of the
 * hotbar and the health/hunger rows, and every line is trimmed to the card's inner width, so nothing
 * is clipped or spills over the HUD at any GUI scale.
 */
public final class LyricsHud {
    private static final int ACCENT_A = 0xFF8B7CFF;
    private static final int ACCENT_B = 0xFF52E0D0;

    private static final int CARD_TOP = 0xE81A1C26;
    private static final int CARD_BOTTOM = 0xE611121A;
    private static final int BORDER = 0x28FFFFFF;
    private static final int SHADOW = 0x50000000;

    private static final int HEADER = 0xFFC7CBF0;
    private static final int CONTEXT = 0xFF9AA3C7;
    private static final int DIM = 0xFF7C84A6;
    private static final int BRIGHT = 0xFFFFFFFF;
    private static final int WARNING = 0xFFFF9AA0;

    private static final int PAD_X = 14;
    private static final int CARD_H = 68;
    private static final int CARD_MIN_W = 150;
    private static final int RADIUS = 6;

    /** Card bottom sits this far above the bottom edge: hotbar (22) plus health/hunger rows. */
    private static final int BOTTOM_MARGIN = 62;

    private static final int HEADER_Y = 7;
    private static final int PREV_Y = 22;
    private static final int CURRENT_Y = 35;
    private static final int NEXT_Y = 50;

    private static final float HEADER_SCALE = 0.80f;
    private static final float CONTEXT_SCALE = 0.82f;

    // ---- animation state ----
    private static float visibility;
    private static float cardWidth;
    private static float pillWidth;
    private static long lastFrame;
    private static String shownKey = "";
    private static int shownIndex = -1;
    private static long lineChangedAt;

    // ---- last content, kept so the card can fade out ----
    private static String header = "";
    private static Lyrics lyrics;
    private static int index;
    private static long positionMs;
    private static long lengthMs;

    private LyricsHud() {}

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // not in a world, menu open, F1, or a window too short for the card to clear the hotbar
        if (client.player == null || client.currentScreen != null || client.options.hudHidden
                || screenW < CARD_MIN_W + 8 || screenH < CARD_H + 80) {
            visibility = 0f;
            return;
        }

        String warning = NowPlaying.warning();
        if (warning != null) {
            visibility = approach(visibility, 1f, dt, 12f);
            drawWarning(context, client, warning, smooth(visibility), screenW, screenH);
            return;
        }

        NowPlaying.Track track = NowPlaying.current();
        Lyrics loaded = track == null ? null : Lyrics.get(track);
        int playing = loaded == null || loaded.instrumental() || loaded.lines().isEmpty()
                ? -1
                : loaded.indexAt(NowPlaying.positionMs());
        boolean live = playing >= 0;

        if (live) {
            String key = track.artist() + "|" + track.title();
            if (!key.equals(shownKey)) {
                shownKey = key;
                cardWidth = 0f;
                pillWidth = 0f;
                shownIndex = -1;
                lineChangedAt = now;
            }
            if (playing != shownIndex) {
                shownIndex = playing;
                lineChangedAt = now;
            }
            header = track.artist().isEmpty() ? track.title() : track.title() + "  \u00b7  " + track.artist();
            lyrics = loaded;
            index = playing;
            positionMs = NowPlaying.positionMs();
            lengthMs = track.lengthMs();
        }

        visibility = approach(visibility, live ? 1f : 0f, dt, 11f);
        float alpha = smooth(visibility);
        if (alpha <= 0.02f || lyrics == null || index < 0 || index >= lyrics.lines().size()) return;

        TextRenderer font = client.textRenderer;
        int cx = screenW / 2;
        Lyrics.Line current = lyrics.lines().get(index);

        int widest = Math.max(font.getWidth(current.text()), Math.round(font.getWidth(header) * HEADER_SCALE));
        int targetWidth = Math.min(screenW - 24, Math.max(CARD_MIN_W, widest + PAD_X * 2 + 26));
        cardWidth = approach(cardWidth, targetWidth, dt, 13f);

        int w = Math.max(2, Math.round(cardWidth));
        int x = cx - w / 2;
        int y = Math.round(screenH - BOTTOM_MARGIN - CARD_H + (1f - alpha) * 8f);
        int inner = Math.max(20, w - PAD_X * 2);

        // shadow, outline, glass body
        roundedFill(context, x - 2, y + 1, w + 4, CARD_H + 4, RADIUS + 2, withAlpha(SHADOW, alpha), withAlpha(SHADOW, alpha));
        roundedFill(context, x - 1, y - 1, w + 2, CARD_H + 2, RADIUS + 1, withAlpha(BORDER, alpha), withAlpha(BORDER, alpha));
        roundedFill(context, x, y, w, CARD_H, RADIUS, withAlpha(CARD_TOP, alpha), withAlpha(CARD_BOTTOM, alpha));
        context.fill(x + RADIUS, y + 1, x + w - RADIUS, y + 2, withAlpha(ACCENT_A, alpha * 0.75f));

        scaledCentered(context, font, fit(font, header, inner, HEADER_SCALE), cx, y + HEADER_Y, HEADER_SCALE,
                withAlpha(HEADER, alpha * 0.95f));

        if (index > 0) {
            scaledCentered(context, font, fit(font, lyrics.lines().get(index - 1).text(), inner, CONTEXT_SCALE),
                    cx, y + PREV_Y, CONTEXT_SCALE, withAlpha(CONTEXT, alpha * 0.55f));
        }
        if (index + 1 < lyrics.lines().size()) {
            scaledCentered(context, font, fit(font, lyrics.lines().get(index + 1).text(), inner, CONTEXT_SCALE),
                    cx, y + NEXT_Y, CONTEXT_SCALE, withAlpha(CONTEXT, alpha * 0.45f));
        }

        // current line: pill grows to fit, words light up as they are sung
        String shown = fit(font, current.text(), inner, 1f);
        float lineIn = smooth(clamp((now - lineChangedAt) / 220_000_000f, 0f, 1f));
        int pillTarget = Math.min(w - 4, font.getWidth(shown) + 24);
        pillWidth = approach(pillWidth, pillTarget, dt, 15f);
        int pillW = Math.round(pillWidth);
        roundedFill(context, cx - pillW / 2, y + CURRENT_Y - 2, pillW, 14, 7,
                withAlpha(0x3A8B7CFF, alpha * lineIn), withAlpha(0x2252E0D0, alpha * lineIn));

        long start = current.timeMs();
        long end = index + 1 < lyrics.lines().size() ? lyrics.lines().get(index + 1).timeMs() : lengthMs;
        float sung = end > start ? clamp((positionMs - start) / (float) (end - start), 0f, 1f) : 1f;
        drawKaraoke(context, font, shown, cx, y + CURRENT_Y + (1f - lineIn) * 4f, sung, alpha * lineIn);

        // song progress
        int barW = w - 24;
        if (barW > 4) {
            int barX = x + 12;
            int barY = y + CARD_H - 8;
            roundedFill(context, barX, barY, barW, 2, 1, withAlpha(0x26FFFFFF, alpha), withAlpha(0x26FFFFFF, alpha));
            if (lengthMs > 0) {
                int filled = Math.round(barW * clamp((float) positionMs / lengthMs, 0f, 1f));
                if (filled > 0) {
                    roundedFill(context, barX, barY, filled, 2, 1,
                            withAlpha(ACCENT_A, alpha * 0.95f), withAlpha(ACCENT_B, alpha * 0.95f));
                }
            }
        }
    }

    private static void drawWarning(DrawContext context, MinecraftClient client, String text,
                                    float alpha, int screenW, int screenH) {
        if (alpha <= 0.02f) return;

        TextRenderer font = client.textRenderer;
        String shown = fit(font, text, screenW - 36, 1f);
        int w = font.getWidth(shown) + 24;
        int x = (screenW - w) / 2;
        int y = Math.round(screenH - 78 + (1f - alpha) * 6f);

        roundedFill(context, x - 1, y - 1, w + 2, 20, 7, withAlpha(BORDER, alpha), withAlpha(BORDER, alpha));
        roundedFill(context, x, y, w, 18, 6, withAlpha(CARD_TOP, alpha), withAlpha(CARD_BOTTOM, alpha));
        context.drawCenteredTextWithShadow(font, shown, screenW / 2, y + 5, withAlpha(WARNING, alpha));
    }

    /** Draws the current line word by word, blending each word from dim to bright as it is sung. */
    private static void drawKaraoke(DrawContext context, TextRenderer font, String text, int cx, float y,
                                    float sung, float alpha) {
        int total = Math.max(1, text.length());
        int space = font.getWidth(" ");
        int x = -font.getWidth(text) / 2;
        int chars = 0;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, y);

        for (String word : text.split(" ", -1)) {
            if (!word.isEmpty()) {
                float from = chars / (float) total;
                float to = (chars + word.length()) / (float) total;
                int color = sung >= to ? BRIGHT
                        : sung <= from ? DIM
                        : lerpColor(DIM, BRIGHT, (sung - from) / (to - from));
                context.drawTextWithShadow(font, word, x, 0, withAlpha(color, alpha));
                x += font.getWidth(word);
            }
            chars += word.length() + 1;
            x += space;
        }

        context.getMatrices().popMatrix();
    }

    private static void scaledCentered(DrawContext context, TextRenderer font, String text, int cx, int y,
                                       float scale, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, y);
        context.getMatrices().scale(scale, scale);
        context.drawCenteredTextWithShadow(font, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    /** Trims text so it never draws wider than the card's inner width at that scale. */
    private static String fit(TextRenderer font, String text, int innerWidth, float scale) {
        int max = Math.max(12, (int) (innerWidth / scale));
        return font.getWidth(text) > max ? font.trimToWidth(text, max) : text;
    }

    /** Rounded rectangle drawn row by row with a vertical gradient, so corners stay soft. */
    private static void roundedFill(DrawContext context, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) return;

        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h, r);
            context.fill(x + inset, y + row, x + w - inset, y + row + 1, lerpColor(top, bottom, row / (float) h));
        }
    }

    private static int cornerInset(int row, int height, int radius) {
        int d = Math.min(row, height - 1 - row);
        if (d >= radius) return 0;
        int dy = radius - 1 - d;
        return radius - (int) Math.sqrt(radius * (double) radius - dy * (double) dy);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ca = Math.round(aa + (ba - aa) * t);
        int cr = Math.round(ar + (br - ar) * t);
        int cg = Math.round(ag + (bg - ag) * t);
        int cb = Math.round(ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * clamp(alpha, 0f, 1f));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float approach(float current, float target, float dt, float speed) {
        return target + (current - target) * (float) Math.exp(-speed * dt);
    }

    private static float smooth(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
