package dev.chustatsu;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;

/** Loads bounded PNGs in a worker and installs textures on the render thread. */
final class BackgroundImages {
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ChuStatsu images");
        thread.setDaemon(true);
        return thread;
    });
    private static final Slot HUD = new Slot("hud");
    private static final Slot TAB = new Slot("tab");

    private BackgroundImages() {}

    static void reload() {
        HUD.invalidate();
        TAB.invalidate();
    }

    static void requestNewCatbox(boolean tab) {
        if (tab) {
            ChuStatsuConfig.tabCatboxImage = true;
            ChuStatsuConfig.tabRandomBackground = false;
            ChuStatsuConfig.tabImageEnabled = true;
            TAB.invalidate();
        } else {
            ChuStatsuConfig.hudCatboxImage = true;
            ChuStatsuConfig.hudRandomBackground = false;
            ChuStatsuConfig.hudImageEnabled = true;
            HUD.invalidate();
        }
        ChuStatsu.saveConfig();
    }

    static void draw(boolean tab, int x, int y, int tableWidth, int tableHeight) {
        boolean enabled = tab ? ChuStatsuConfig.tabImageEnabled : ChuStatsuConfig.hudImageEnabled;
        boolean catbox = tab ? ChuStatsuConfig.tabCatboxImage : ChuStatsuConfig.hudCatboxImage;
        boolean random = tab ? ChuStatsuConfig.tabRandomBackground : ChuStatsuConfig.hudRandomBackground;
        if (!enabled && !catbox && !random) return;
        Slot slot = tab ? TAB : HUD;
        String filename = tab ? ChuStatsuConfig.tabImageFile : ChuStatsuConfig.hudImageFile;
        int opacity = tab ? ChuStatsuConfig.tabImageOpacity : ChuStatsuConfig.hudImageOpacity;
        int size = tab ? ChuStatsuConfig.tabImageSize : ChuStatsuConfig.hudImageSize;
        int position = tab ? ChuStatsuConfig.tabImagePosition : ChuStatsuConfig.hudImagePosition;
        slot.draw(filename, random, catbox && !random, x, y, tableWidth, tableHeight,
            opacity, size, position);
    }

    static boolean safePngName(String name) {
        return name != null && !name.isBlank() && name.equals(Path.of(name).getFileName().toString())
            && name.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static Path folder() throws IOException {
        Path folder = Minecraft.getInstance().gameDir.toPath().resolve("config/chustatsu/backgrounds");
        Files.createDirectories(folder);
        return folder;
    }

    private static BufferedImage readPng(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) return null;
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) return null;
            Iterator<ImageReader> candidates = ImageIO.getImageReaders(input);
            if (!candidates.hasNext()) return null;
            ImageReader reader = candidates.next();
            try {
                if (!"png".equalsIgnoreCase(reader.getFormatName())) return null;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                    || (long) width * height > 8_000_000L) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static final class Slot {
        private final String id;
        private String selection;
        private Identifier texture;
        private int width, height;
        private volatile BufferedImage pending;
        private volatile int pendingGeneration;
        private volatile int generation;
        private int fixedWidth, fixedHeight;
        private CatboxGallery gallery;

        private Slot(String id) { this.id = id; }

        void invalidate() {
            selection = null;
            generation++;
            pending = null;
        }

        void draw(String filename, boolean random, boolean catbox, int x, int y, int tableWidth, int tableHeight,
                  int opacity, int size, int position) {
            String wanted = random ? "random" : catbox ? "catbox" : String.valueOf(filename);
            if (!wanted.equals(selection)) {
                selection = wanted;
                int request = ++generation;
                pending = null;
                WORKER.execute(() -> load(wanted, random, catbox, request));
            }
            BufferedImage ready = pending;
            if (ready != null && pendingGeneration == generation) {
                pending = null;
                Minecraft mc = Minecraft.getInstance();
                if (texture != null) mc.getTextureManager().close(texture);
                width = ready.getWidth();
                height = ready.getHeight();
                texture = mc.getTextureManager().register("chustatsu-" + id, new DynamicTexture(ready));
                fixedWidth = fixedHeight = 0;
            }
            if (texture == null) return;
            int availableWidth = Math.max(1, tableWidth - 12);
            int availableHeight = Math.max(1, tableHeight - 12);
            if (ChuStatsuConfig.staticImageSize) {
                if (fixedWidth == 0) { fixedWidth = availableWidth; fixedHeight = availableHeight; }
                availableWidth = fixedWidth;
                availableHeight = fixedHeight;
            } else fixedWidth = fixedHeight = 0;
            float fit = Math.min(availableWidth / (float) width, availableHeight / (float) height);
            float scale = fit * Math.max(10, Math.min(200, size)) / 100f;
            int drawWidth = Math.max(1, Math.round(width * scale));
            int drawHeight = Math.max(1, Math.round(height * scale));
            int drawX = position == 1 ? x + (tableWidth - drawWidth) / 2
                : position == 2 ? x + tableWidth - drawWidth - 6 : x + 6;
            int drawY = y + (tableHeight - drawHeight) / 2;
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            GL11.glPushMatrix();
            try {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1f, 1f, 1f, Math.max(10, Math.min(100, opacity)) / 100f);
                Minecraft.getInstance().getTextureManager().bind(texture);
                GL11.glTranslatef(drawX, drawY, 0);
                GL11.glScalef(drawWidth / (float) width, drawHeight / (float) height, 1f);
                GuiElement.drawTexture(0, 0, 0f, 0f, width, height, width, height);
            } finally {
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }

        private void load(String wanted, boolean random, boolean catbox, int request) {
            try {
                Path image;
                if (catbox) {
                    Path cache = Minecraft.getInstance().gameDir.toPath().resolve("config/chustatsu/catbox-cache");
                    if (gallery == null) gallery = new CatboxGallery(cache);
                    image = gallery.next();
                } else if (random) {
                    Path folder = folder();
                    List<Path> choices;
                    try (Stream<Path> files = Files.list(folder)) {
                        choices = files.filter(path -> safePngName(path.getFileName().toString()))
                            .filter(Files::isRegularFile).toList();
                    }
                    if (choices.isEmpty()) return;
                    image = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
                } else {
                    if (!safePngName(wanted)) return;
                    image = folder().resolve(wanted);
                }
                BufferedImage pixels = readPng(image);
                if (pixels == null) return;
                if (request == generation) {
                    pendingGeneration = request;
                    pending = pixels;
                }
            } catch (IOException | RuntimeException error) {
                ChuStatsu.LOGGER.warn("Could not load ChuStatsu {} background image", id, error);
            }
        }
    }
}
