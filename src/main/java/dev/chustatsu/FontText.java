package dev.chustatsu;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Optional formatted TTF text for tables; vanilla text remains the fallback. */
final class FontText {
    private static final float SCALE = .4f;
    private static final float FONT_SIZE = 24f;
    private static final int MAX_ENTRIES = 256;
    private static final long MAX_PIXELS = 4_000_000;
    private static final int[] COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(32, .75f, true);
    private static Font[] fonts;
    private static String selection = "";
    private static boolean clearOnDraw;
    private static long pixels;

    private record Entry(Identifier texture, int width, int height) {}

    private FontText() {}

    static void reload() {
        selection = "";
        clearOnDraw = true;
    }

    static int width(String text) {
        ensure();
        if (fonts == null) return Minecraft.getInstance().textRenderer.getWidth(text);
        if (text == null || text.isEmpty()) return 0;
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        try { return Math.round(measure(g, text) * SCALE); }
        finally { g.dispose(); }
    }

    static void draw(String text, float x, float y) {
        drawColored(text, x, y, 0xFFFFFF);
    }

    static void drawColored(String text, float x, float y, int rgb) {
        ensure();
        if (text == null || text.isEmpty()) return;
        if (fonts == null) {
            if (clearOnDraw) {
                clearCache();
                clearOnDraw = false;
            }
            if (ChuStatsuConfig.textShadows)
                Minecraft.getInstance().textRenderer.drawWithShadow(text, x, y, rgb);
            else Minecraft.getInstance().textRenderer.draw(text, Math.round(x), Math.round(y), rgb);
            return;
        }
        if (clearOnDraw) {
            clearCache();
            clearOnDraw = false;
        }
        Entry entry = CACHE.get(text);
        if (entry == null) {
            BufferedImage image = raster(text);
            Identifier texture = Minecraft.getInstance().getTextureManager()
                .register("chustatsu-font", new DynamicTexture(image));
            entry = new Entry(texture, image.getWidth(), image.getHeight());
            CACHE.put(text, entry);
            pixels += (long) entry.width * entry.height;
            while (CACHE.size() > MAX_ENTRIES || pixels > MAX_PIXELS) {
                String oldest = CACHE.keySet().iterator().next();
                Entry expired = CACHE.remove(oldest);
                pixels -= (long) expired.width * expired.height;
                Minecraft.getInstance().getTextureManager().close(expired.texture);
            }
        }
        int previousProgram = GLContext.getCapabilities().OpenGL20
            ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
        int previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            if (previousProgram != 0) GL20.glUseProgram(0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GL11.glColor4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f,
                (rgb & 255) / 255f, 1f);
            Minecraft.getInstance().getTextureManager().bind(entry.texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            float w = entry.width * SCALE, h = entry.height * SCALE;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + h);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y + h);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(previousTexture);
            GlStateManager.activeTexture(previousTextureUnit);
            if (previousProgram != 0) GL20.glUseProgram(previousProgram);
        }
    }

    private static void ensure() {
        String next = ChuStatsuConfig.FontOptions.fontMode + ":"
            + ChuStatsuConfig.FontOptions.customFont + ":" + ChuStatsuConfig.textShadows;
        if (next.equals(selection)) return;
        selection = next;
        clearOnDraw = true;
        fonts = null;
        if (ChuStatsuConfig.FontOptions.fontMode == 0) return;
        try {
            if (ChuStatsuConfig.FontOptions.fontMode == 1) {
                String[] names = {"Poppins-Regular.ttf", "Poppins-Bold.ttf",
                    "Poppins-Italic.ttf", "Poppins-BoldItalic.ttf"};
                Font[] loaded = new Font[names.length];
                for (int i = 0; i < names.length; i++) {
                    try (InputStream in = FontText.class.getResourceAsStream("/assets/chustatsu/fonts/" + names[i])) {
                        if (in == null) throw new IllegalArgumentException("Missing bundled Poppins font");
                        loaded[i] = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(FONT_SIZE);
                    }
                }
                fonts = loaded;
            } else if (ChuStatsuConfig.FontOptions.fontMode == 2) {
                String name = ChuStatsuConfig.FontOptions.customFont;
                if (name == null || !name.matches("[A-Za-z0-9_.-]+\\.ttf"))
                    throw new IllegalArgumentException("Invalid custom font name");
                Path file = Minecraft.getInstance().gameDir.toPath().resolve("config/chustatsu/fonts").resolve(name);
                if (!Files.isRegularFile(file) || Files.size(file) > 8L * 1024 * 1024)
                    throw new IllegalArgumentException("Custom font missing or too large");
                try (InputStream in = Files.newInputStream(file)) {
                    Font base = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(FONT_SIZE);
                    fonts = new Font[] {base, base.deriveFont(Font.BOLD), base.deriveFont(Font.ITALIC),
                        base.deriveFont(Font.BOLD | Font.ITALIC)};
                }
            }
        } catch (Exception error) {
            ChuStatsu.LOGGER.warn("Custom font unavailable; using Minecraft text", error);
            fonts = null;
        }
    }

    private static void clearCache() {
        for (Entry entry : CACHE.values()) Minecraft.getInstance().getTextureManager().close(entry.texture);
        CACHE.clear();
        pixels = 0;
    }

    private static int measure(Graphics2D g, String text) {
        int width = 0, style = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') style |= 1;
                else if (code == 'o') style |= 2;
                else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) style = 0;
                continue;
            }
            width += Math.max(1, g.getFontMetrics(fonts[style]).charWidth(ch));
        }
        return width;
    }

    private static BufferedImage raster(String text) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probe = scratch.createGraphics();
        int width, height, ascent;
        try {
            FontMetrics metrics = probe.getFontMetrics(fonts[0]);
            width = Math.max(1, measure(probe, text) + 4);
            int capTop = fonts[0].createGlyphVector(probe.getFontRenderContext(), "H")
                .getPixelBounds(null, 0, 0).y;
            ascent = 1 - capTop;
            height = Math.max(1, ascent + metrics.getDescent() + 4);
        } finally { probe.dispose(); }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (ChuStatsuConfig.textShadows) paint(g, text, 2, ascent + 2, true);
            paint(g, text, 1, ascent + 1, false);
        } finally { g.dispose(); }
        return image;
    }

    private static void paint(Graphics2D g, String text, int offset, int baseline, boolean shadow) {
        int cursor = 0, style = 0, color = 0xFFFFFF;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (code == 'l') style |= 1;
                else if (code == 'o') style |= 2;
                else if (code == 'r') { style = 0; color = 0xFFFFFF; }
                else {
                    int index = "0123456789abcdef".indexOf(code);
                    if (index >= 0) { color = COLORS[index]; style = 0; }
                }
                continue;
            }
            Font font = fonts[style];
            g.setFont(font);
            g.setColor(shadow ? new Color(0, 0, 0, 130) : new Color(color));
            g.drawString(String.valueOf(ch), cursor + offset, baseline);
            cursor += Math.max(1, g.getFontMetrics(font).charWidth(ch));
        }
    }
}
