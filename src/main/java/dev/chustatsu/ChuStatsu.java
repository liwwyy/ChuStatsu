package dev.chustatsu;

import net.ornithemc.osl.entrypoints.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import java.lang.reflect.Method;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;

public final class ChuStatsu implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger("ChuStatsu");
    private static ChuStatsuConfig config;
    private static StatsController controller;

    @Override
    public void init() {
        config = new ChuStatsuConfig();
        HudManager.register(new TableHud.HudTable(), "chustatsu");
        HudManager.register(new TableHud.TabTable(), "chustatsu");
        controller = new StatsController();
        EventManager.register(TickEvent.End.class, controller::tick);
        EventManager.register(ChatEvent.Receive.class, event -> controller.observeChat(event.getFullyUnformattedMessage()));
        EventManager.register(ChatEvent.Send.class, controller::onChatSend);
        LOGGER.info("ChuStatsu initialized");
    }

    public static StatsController controller() {
        return controller;
    }

    public static void saveConfig() {
        if (config != null) config.save();
    }

    public static void openConfig() {
        try {
            Class<?> screens = Class.forName("org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt");
            Method open = screens.getMethod("openUI", org.polyfrost.oneconfig.api.config.v1.Config.class);
            open.invoke(null, config);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Could not open ChuStatsu settings", e);
        }
    }
}
