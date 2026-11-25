package dev.buffy.ytlyrics;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YtLyricsMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ytlyrics");

    @Override
    public void onInitializeClient() {
        NowPlaying.start();
        LOGGER.info("YouTube Music lyrics HUD ready");
    }
}
