package dev.chustatsu;

import java.lang.reflect.Method;
import net.minecraft.client.network.PlayerInfo;
import org.lwjgl.opengl.GL11;

/** Uses the beta's installed PolyPlus player-list badge when that optional mod is present. */
final class ClientBadge {
    private static Method shouldShow;
    private static Method draw;
    private static boolean checked;

    private ClientBadge() {}

    static boolean visible(PlayerInfo info) {
        if (info == null || !ChuStatsuConfig.tabClientIndicator) return false;
        resolve();
        if (shouldShow == null) return false;
        try { return Boolean.TRUE.equals(shouldShow.invoke(null, info)); }
        catch (ReflectiveOperationException | LinkageError error) { return false; }
    }

    static void draw(int x, int y) {
        resolve();
        if (draw == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try { draw.invoke(null, x, y); }
        catch (ReflectiveOperationException | LinkageError error) {
            ChuStatsu.LOGGER.debug("Could not draw installed client badge", error);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void resolve() {
        if (checked) return;
        checked = true;
        try {
            Class<?> badge = Class.forName("org.polyfrost.polyplus.client.PolyPlusBadge");
            shouldShow = badge.getMethod("shouldBadgeTab", PlayerInfo.class);
            draw = badge.getMethod("drawBadge", int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The badge is optional in other modlists.
        }
    }
}
