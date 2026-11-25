package dev.buffy.ytlyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lyrics come from LRCLib (lrclib.net), the open synced-lyrics database: no key, no login, and it
 * hands back LRC timestamps, which is what makes the highlight follow the song.
 */
public final class Lyrics {
    public record Line(long timeMs, String text) { }

    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2}(?:[.:]\\d{1,3})?)]");
    private static final Pattern BRACKETS = Pattern.compile("[（(\\[][^)）\\]]*[)）\\]]");
    private static final Set<String> NOISE_WORDS =
            Set.of("official", "video", "audio", "lyric", "lyrics", "visualizer", "remaster", "remastered", "hd", "4k", "mv");

    private static final Map<String, Lyrics> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Lyrics NONE = new Lyrics(List.of(), false);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ytlyrics-lrclib");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Line> lines;
    private final boolean instrumental;

    private Lyrics(List<Line> lines, boolean instrumental) {
        this.lines = lines;
        this.instrumental = instrumental;
    }

    /** Cached lyrics for the track, or null while the first request is still in flight. */
    public static Lyrics get(NowPlaying.Track track) {
        String key = key(track);
        Lyrics cached = CACHE.get(key);
        if (cached != null) return cached == NONE ? null : cached;
        if (!LOADING.add(key)) return null;

        IO.execute(() -> {
            Lyrics fetched;
            try {
                fetched = download(track);
            } catch (Exception e) {
                fetched = null;
            }
            CACHE.put(key, fetched == null ? NONE : fetched);
            if (CACHE.size() > 32) CACHE.entrySet().removeIf(e -> e.getValue() == NONE);
            LOADING.remove(key);
        });

        return null;
    }

    public boolean instrumental() {
        return instrumental;
    }

    public List<Line> lines() {
        return lines;
    }

    /** Index of the line playing at that position, or -1 before the first line. */
    public int indexAt(long positionMs) {
        int low = 0, high = lines.size() - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found;
    }

    private static Lyrics download(NowPlaying.Track track) throws Exception {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/search?track_name=")
                .append(URLEncoder.encode(clean(track.title()), StandardCharsets.UTF_8));

        String artist = clean(track.artist());
        if (!artist.isEmpty()) {
            url.append("&artist_name=").append(URLEncoder.encode(artist, StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .header("User-Agent", "ytlyrics-fabric/1.0 (Minecraft lyrics HUD)")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonArray results = JsonParser.parseString(response.body()).getAsJsonArray();

        JsonObject synced = null;
        JsonObject plain = null;
        boolean instrumental = false;

        for (JsonElement element : results) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("instrumental").getAsBoolean()) instrumental = true;
            if (synced == null && has(entry, "syncedLyrics")) synced = entry;
            if (plain == null && has(entry, "plainLyrics")) plain = entry;
        }

        if (synced != null) return new Lyrics(parseLrc(synced.get("syncedLyrics").getAsString()), false);
        if (plain != null) return new Lyrics(unsynced(plain.get("plainLyrics").getAsString(), track.lengthMs()), false);
        return instrumental ? new Lyrics(List.of(), true) : null;
    }

    private static boolean has(JsonObject entry, String field) {
        return entry.has(field) && !entry.get(field).isJsonNull() && !entry.get(field).getAsString().isBlank();
    }

    private static List<Line> parseLrc(String lrc) {
        List<Line> lines = new ArrayList<>();

        for (String raw : lrc.split("\n")) {
            Matcher matcher = TIMESTAMP.matcher(raw);
            int consumed = 0;
            List<Long> timestamps = new ArrayList<>();

            // one line can carry several timestamps ("[00:10.00][01:20.00] chorus")
            while (matcher.find(consumed) && matcher.start() == consumed) {
                long minutes = Long.parseLong(matcher.group(1));
                double seconds = Double.parseDouble(matcher.group(2).replace(':', '.'));
                timestamps.add(Math.round(minutes * 60_000 + seconds * 1000));
                consumed = matcher.end();
            }

            if (timestamps.isEmpty()) continue;

            String text = raw.substring(consumed).trim();
            if (text.isEmpty()) continue;

            for (long time : timestamps) lines.add(new Line(time, text));
        }

        lines.sort(Comparator.comparingLong(Line::timeMs));
        return lines;
    }

    /** Plain lyrics have no timings; spread them across the track so the overlay still scrolls. */
    private static List<Line> unsynced(String plain, long lengthMs) {
        List<Line> lines = new ArrayList<>();

        for (String raw : plain.split("\n")) {
            String text = raw.trim();
            if (!text.isEmpty()) lines.add(new Line(0, text));
        }

        if (lines.isEmpty()) return lines;

        long perLine = lengthMs > 0 ? lengthMs / lines.size() : 4000L;
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, new Line(i * perLine, lines.get(i).text()));
        }
        return lines;
    }

    private static String key(NowPlaying.Track track) {
        return clean(track.artist()) + "|" + clean(track.title());
    }

    /** Drops "(Official Video)" style noise so the LRCLib search actually hits. */
    private static String clean(String text) {
        Matcher matcher = BRACKETS.matcher(text);
        StringBuilder cleaned = new StringBuilder();

        while (matcher.find()) {
            String inner = matcher.group().toLowerCase();
            boolean noise = false;
            for (String word : NOISE_WORDS) {
                if (inner.contains(word)) noise = true;
            }
            matcher.appendReplacement(cleaned, noise ? " " : Matcher.quoteReplacement(matcher.group()));
        }

        matcher.appendTail(cleaned);
        return cleaned.toString().replaceAll("\\s+", " ").trim();
    }
}
