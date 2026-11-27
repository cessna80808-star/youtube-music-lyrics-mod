package dev.buffy.ytlyrics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Drag the lyric card anywhere, scroll to resize, R to reset, Esc to save. */
public final class HudEditScreen extends Screen {
    private boolean dragging;

    public HudEditScreen() {
        super(Component.literal("Lyrics HUD layout"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        LyricsHud.renderEditor(context);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double offsetX, double offsetY) {
        if (dragging) {
            HudConfig.moveTo(HudConfig.x() + (float) (offsetX / width),
                    HudConfig.y() + (float) (offsetY / height));
            return true;
        }
        return super.mouseDragged(event, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudConfig.resize((float) vertical * 0.05f);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R) {
            HudConfig.reset();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        HudConfig.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
