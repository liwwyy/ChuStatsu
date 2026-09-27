package dev.chustatsu;

import kotlin.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.TextHud;
import dev.chustatsu.stats.StatsView;
import java.util.ArrayList;
import java.util.List;

/** Native OneConfig editor handles for the two independently positioned tables. */
public abstract class TableHud extends TextHud {
    private TableHud(String id, String title) {
        super(id, title, Hud.Category.getINFO(), "", "");
        setPadLeft(0);
        setPadRight(0);
        setPadTop(0);
        setPadBottom(0);
        setStaticWidth(true);
        setStaticW(260);
        setStaticH(64);
        setShowBackground(false);
        setShowShadow(false);
    }

    @Override
    protected String getText() {
        if (!HudManager.INSTANCE.isEditorOpen()) return "";
        List<String> columns = new ArrayList<>(ColumnLayout.selected(
            this instanceof TabTable ? ChuStatsuConfig.tabColumns : ChuStatsuConfig.hudColumns,
            this instanceof TabTable));
        if (columns.contains("Dynamic name box")) columns.remove("Rank");
        StatsView first = new StatsView("liywy", StatsView.Status.READY, 42, 120, 80,
            234, 100, 5, 87, "Pika", "VIP", "");
        StatsView second = new StatsView("movi6287", StatsView.Status.READY, 67, 250, 100,
            314, 100, 10, 120, "Pika", "MVP", "");
        List<StatsView> examples = List.of(first, second);
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            int width = plain(ColumnLayout.header(column)).length();
            for (StatsView example : examples)
                width = Math.max(width, plain(sample(column, example)).length());
            widths[i] = width + 2;
        }
        StringBuilder result = new StringBuilder();
        boolean header = (!ChuStatsuConfig.apiOnlyInMatchOrWaiting
            || PikaContext.inWaitingRoom(Minecraft.getInstance())
            || PikaContext.inGame(Minecraft.getInstance()))
            && (this instanceof TabTable ? ChuStatsuConfig.tabShowHeader : ChuStatsuConfig.hudShowHeader);
        if (header) result.append("§bChuStatsu §7• §fOverall §7/ §fLifetime\n");
        if (header) {
            for (int i = 0; i < columns.size(); i++) appendCell(result,
                columns.get(i).equals("Head") ? "" : "§e" + ColumnLayout.header(columns.get(i)), widths[i]);
            result.append('\n');
        }
        for (int row = 0; row < examples.size(); row++) {
            for (int i = 0; i < columns.size(); i++)
                appendCell(result, sample(columns.get(i), examples.get(row)), widths[i]);
            if (row + 1 < examples.size()) result.append('\n');
        }
        return result.toString();
    }

    private static String sample(String column, StatsView example) {
        return column.equals("Head") ? "§f■"
            : ColumnLayout.cell(column, "§f" + example.username(), example, 42, "§c20");
    }

    private static void appendCell(StringBuilder result, String value, int width) {
        result.append(value);
        result.append(" ".repeat(Math.max(0, width - plain(value).length())));
    }

    private static String plain(String value) {
        return value.replaceAll("(?i)§[0-9a-fk-or]", "");
    }

    @Override
    public boolean hasBackground() {
        return false;
    }

    @Override
    public boolean showByDefault() {
        return true;
    }

    @Override
    public boolean deletable() {
        // Existing profiles can already contain duplicates. The editor may remove extras,
        // while keeping the final handle so the table remains movable.
        return isReal() && HudManager.INSTANCE.getHudsOfType(getClass()).size() > 1;
    }

    @Override
    public boolean multipleInstancesAllowed() {
        return false;
    }

    public void size(float width, float height) {
        setStaticW(Math.max(12, width));
        setStaticH(Math.max(12, height));
    }

    public static <T extends TableHud> T active(Class<T> type) {
        var matches = HudManager.INSTANCE.getHudsOfType(type);
        for (int i = matches.size() - 1; i >= 0; i--) {
            if (!matches.get(i).getHidden()) return matches.get(i);
        }
        return matches.isEmpty() ? null : matches.get(matches.size() - 1);
    }

    public static final class HudTable extends TableHud {
        public HudTable() { super("chustatsu-hud-table", "ChuStatsu HUD"); }

        @Override
        public Pair<Float, Float> defaultPosition() {
            Minecraft mc = Minecraft.getInstance();
            return new Pair<>(8f, 8f);
        }

        @Override
        public boolean shouldShow() {
            return ChuStatsuConfig.enabled && ChuStatsuConfig.hudEnabled;
        }
    }

    public static final class TabTable extends TableHud {
        public TabTable() { super("chustatsu-tab-table", "ChuStatsu TAB"); }

        @Override
        public Pair<Float, Float> defaultPosition() {
            Minecraft mc = Minecraft.getInstance();
            return new Pair<>((float) Math.max(8, (new Window(mc).getScaledWidth() - getStaticW()) / 2f), 26f);
        }

        @Override
        public boolean shouldShow() {
            return ChuStatsuConfig.enabled && ChuStatsuConfig.tabEnabled;
        }
    }
}
