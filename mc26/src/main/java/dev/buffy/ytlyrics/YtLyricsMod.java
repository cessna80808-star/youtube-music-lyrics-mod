package dev.buffy.ytlyrics;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YtLyricsMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ytlyrics");

    @Override
    public void onInitializeClient() {
        NowPlaying.start();

        KeyMapping visualizer = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ytlyrics.visualizer", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ytlyrics", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (visualizer.consumeClick()) LyricsHud.toggleVisualizer();
        });

        // 26.x draws the HUD into a render state, so this is a registered element rather than a mixin
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("ytlyrics", "lyrics"),
                (context, tickCounter) -> LyricsHud.render(context));

        LOGGER.info("YouTube Music lyrics HUD ready");
    }
}
