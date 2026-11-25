package dev.buffy.ytlyrics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Reads the OS media session through playerctl (MPRIS).
 *
 * YouTube Music in Firefox/Chromium and the YouTube Music desktop app both publish an MPRIS
 * session, so the browser tab and the desktop app are picked up the same way.
 */
public final class NowPlaying {
    public record Track(String artist, String title, long lengthMs) { }

    // \u001f is not legal in metadata, so it is a safe field separator
    private static final String FORMAT = "{{playerName}}\u001f{{status}}\u001f{{artist}}\u001f{{title}}"
            + "\u001f{{mpris:length}}\u001f{{position}}\u001f{{xesam:url}}";

    private static final long POLL_MS = 700;

    private static volatile Track track;
    private static volatile boolean playing;
    private static volatile long sampledPositionMs;
    private static volatile long sampledAtNanos;
    private static volatile String warning;

    private record Sample(String artist, String title, long lengthMs, long positionMs, boolean playing) { }

    private NowPlaying() {}

    public static void start() {
        Thread poller = new Thread(NowPlaying::pollLoop, "ytlyrics-playerctl");
        poller.setDaemon(true);
        poller.start();
    }

    /** The track that is playing right now, or null. */
    public static Track current() {
        return playing ? track : null;
    }

    /** Position in ms, extrapolated since the last poll so the highlighted line moves smoothly. */
    public static long positionMs() {
        long elapsedMs = (System.nanoTime() - sampledAtNanos) / 1_000_000L;
        return sampledPositionMs + (playing ? elapsedMs : 0L);
    }

    /** Non-null when the media session cannot be read at all, e.g. playerctl is missing. */
    public static String warning() {
        return warning;
    }

    private static void pollLoop() {
        while (true) {
            try {
                poll();
            } catch (Exception e) {
                warning = "ytlyrics: playerctl missing (sudo apt install playerctl)";
            }

            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void poll() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("playerctl", "-a", "metadata", "--format", FORMAT)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return;
        }

        warning = null;

        // prefer the YouTube Music session when several players are open
        Sample best = null;
        int bestScore = -1;

        for (String line : output.split("\n")) {
            String[] fields = line.split("\u001f", -1);
            if (fields.length < 7) continue; // "No players found" and other playerctl chatter

            Sample sample = toSample(fields);
            if (sample == null || sample.title().isEmpty()) continue;

            int score = 0;
            if (fields[6].contains("music.youtube.com")) score += 3;
            if (fields[0].toLowerCase().contains("youtube")) score += 2;
            if (sample.playing()) score += 1;

            if (score > bestScore) {
                bestScore = score;
                best = sample;
            }
        }

        if (best == null) {
            playing = false;
            track = null;
            return;
        }

        track = new Track(best.artist(), best.title(), best.lengthMs());
        sampledPositionMs = best.positionMs();
        sampledAtNanos = System.nanoTime();
        playing = best.playing();
    }

    private static Sample toSample(String[] fields) {
        String artist = fields[2].trim();
        String title = fields[3].trim();

        // browser tabs often expose only "Song - Artist" as the title
        if (artist.isEmpty()) {
            int split = title.lastIndexOf(" - ");
            if (split > 0) {
                artist = title.substring(split + 3).trim();
                title = title.substring(0, split).trim();
            }
        }

        return new Sample(artist, title, microsToMs(fields[4]), microsToMs(fields[5]),
                fields[1].equalsIgnoreCase("Playing"));
    }

    private static long microsToMs(String value) {
        try {
            return Long.parseLong(value.trim()) / 1000L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
