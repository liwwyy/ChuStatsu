package dev.chustatsu;

import dev.chustatsu.stats.PikaStatsClient;
import dev.chustatsu.stats.StatsView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.Window;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.TabListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.WorldSettings;
import java.util.Locale;
import net.minecraft.text.LiteralText;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.hud.v1.Section;
import org.lwjgl.opengl.GL11;

/** Samples game state on the client thread; API work remains on the service thread. */
public final class StatsController {
    private static final String[] NAMETAG_STATS = {"FKDR", "Level", "WLR", "Winstreak", "Final kills", "Wins", "Beds"};
    private static final int DIVIDER_COLOR = 0x80B6C4D0;
    private static final int ROW_STRIPE = 0x11FFFFFF;
    private static final int NAME_INSET = 3;
    private final PikaStatsClient api = new PikaStatsClient();
    private final SocialTracker social = new SocialTracker();
    private final DenickTracker denick = new DenickTracker(this::denickDiagnostic);
    private final OtherPartyTracker otherParties = new OtherPartyTracker();
    private final TableMotion hudMotion = new TableMotion();
    private final TableMotion tabMotion = new TableMotion();
    private final TableLayoutMotion<Row> hudLayout = new TableLayoutMotion<>();
    private final TableLayoutMotion<PlayerInfo> tabLayout = new TableLayoutMotion<>();
    private volatile List<Row> rows = List.of();
    private volatile Map<String, Integer> otherPartyGroups = Map.of();
    private int ticks;
    private String pendingLookup;
    private String matchOverview;
    private volatile String rawTabHeader = "";
    private volatile String rawTabFooter = "";
    private boolean tabCenterMigrated;
    private final Map<String, TeamIdentity> trackedTeams = new HashMap<>();
    private Object teamWorld;

    private record Row(String name, StatsView stats, int ping) {}
    private record TeamIdentity(String name, String color) {}

    public void tick() {
        if (ChuStatsuConfig.migrateNameColumns()) ChuStatsu.saveConfig();
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        PikaContext.SidebarState sidebar = PikaContext.sidebarState(mc);
        boolean apiAllowed = apiAllowed(mc, sidebar);
        if (pendingLookup != null && apiAllowed) {
            StatsView result = api.get(pendingLookup, ChuStatsuConfig.mode, ChuStatsuConfig.period,
                ChuStatsuConfig.cacheSeconds);
            if (result.status() != StatsView.Status.LOADING) {
                if (mc.player != null) mc.player.addMessage(new LiteralText(chatSummary(result)));
                pendingLookup = null;
            }
        }
        if (!ChuStatsuConfig.enabled || !allowed(mc, sidebar)) {
            prepareTeams(mc, false);
            rows = List.of();
            hudLayout.clear();
            tabLayout.clear();
            otherPartyGroups = Map.of();
            otherParties.setWaiting(false);
            if (!PikaContext.active(mc, false)) {
                social.reset();
                denick.reset();
                matchOverview = null;
            }
            return;
        }
        social.updateLocalName(mc.player.getName());
        if (apiAllowed) api.get(mc.player.getName(), ChuStatsuConfig.mode, ChuStatsuConfig.period,
            ChuStatsuConfig.cacheSeconds);
        social.updateFriends(api.friendsFor(mc.player.getName()));
        ArrayList<String> names = new ArrayList<>();
        if (mc.getNetworkHandler() != null) {
            for (PlayerInfo info : mc.getNetworkHandler().getOnlinePlayers()) {
                String name = info.getProfile().getName();
                if (name != null && name.matches("[A-Za-z0-9_]{3,16}")) names.add(name);
            }
        }
        boolean waiting = sidebar.waiting();
        boolean inGame = sidebar.inGame();
        prepareTeams(mc, inGame);
        otherParties.setWaiting(waiting && ChuStatsuConfig.detectOtherParties);
        if (waiting && ChuStatsuConfig.detectOtherParties) {
            Map<String, Integer> groups = new HashMap<>();
            for (String name : names) {
                int group = otherParties.group(name, ChuStatsuConfig.otherPartyWindowMs, social::isParty);
                if (group >= 0) groups.put(name.toLowerCase(Locale.ROOT), group);
            }
            otherPartyGroups = Map.copyOf(groups);
        } else otherPartyGroups = Map.of();
        if (waiting) names.sort(Comparator.comparingInt(this::socialRank));
        else if (inGame) names.sort((a, b) -> compareTeams(mc, a, b, true));
        ArrayList<Row> next = new ArrayList<>();
        for (int i = 0; i < Math.min(ChuStatsuConfig.maxApiPlayers, names.size()); i++) {
            String name = names.get(i);
            PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(name);
            next.add(new Row(name, apiAllowed
                ? api.get(effectiveName(name), ChuStatsuConfig.mode, ChuStatsuConfig.period,
                    ChuStatsuConfig.cacheSeconds) : StatsView.loading(name),
                info == null ? -1 : info.getPing()));
        }
        if (waiting && ChuStatsuConfig.sortHud) {
            next.sort(Comparator.comparingInt((Row row) -> socialRank(row.name))
                .thenComparing(row -> row.stats.sortValue(ChuStatsuConfig.hudSortStat),
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
        rows = List.copyOf(next);
    }

    public void renderHud() {
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.hudEnabled) {
            hudMotion.snapHidden();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PikaContext.SidebarState sidebar = PikaContext.sidebarState(mc);
        if (!allowed(mc, sidebar) || rows.isEmpty()) {
            hudMotion.snapHidden();
            hudLayout.clear();
            return;
        }
        boolean show = visible(mc, sidebar, ChuStatsuConfig.hudAlwaysShow,
            ChuStatsuConfig.hudShowWaiting, ChuStatsuConfig.hudShowInGame)
            && !(ChuStatsuConfig.hideHudWhileTab && mc.options.playerListKey.isPressed());
        int motionMode = show ? ChuStatsuConfig.hudShowAnimation : ChuStatsuConfig.hudHideAnimation;
        float progress = visibilityProgress(hudMotion, show, motionMode,
            ChuStatsuConfig.hudShowDuration, ChuStatsuConfig.hudHideDuration);
        if (progress <= 0f) return;
        Window window = new Window(mc);
        boolean waiting = sidebar.waiting();
        boolean inGame = sidebar.inGame();
        boolean showHeader = ChuStatsuConfig.hudShowHeader && apiAllowed(mc, sidebar);
        List<String> columns = visibleColumns(ChuStatsuConfig.hudColumns, false, sidebar);
        Map<String, String> displayNames = new HashMap<>();
        for (Row row : rows)
            displayNames.put(row.name, displayName(mc, row.name, waiting, inGame));
        int padding = columnPadding();
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            if (columns.get(c).equals("Head")) {
                widths[c] = 18;
                continue;
            }
            widths[c] = FontText.width(ColumnLayout.header(columns.get(c))) + padding;
            for (Row row : rows) {
                String column = columns.get(c);
                String name = displayNames.get(row.name);
                if (ColumnLayout.status(row.stats) == null || ColumnLayout.isName(column)
                    || column.equals("Level") && row.stats.level() != null) {
                    int content = FontText.width(ColumnLayout.cell(column, name, row.stats, row.ping, null));
                    if (column.equals("Dynamic name box")) {
                        String secondary = nameSecondary(mc, row.name);
                        if (!secondary.isEmpty()) content += FontText.width(secondary) + 5;
                    }
                    widths[c] = Math.max(widths[c], content + padding + (ColumnLayout.isName(column) ? NAME_INSET : 0));
                }
            }
        }
        for (Row row : rows) ensureStatusWidth(columns, widths, row.stats);
        int totalWidth = 0;
        for (int width : widths) totalWidth += width;
        if (showHeader) totalWidth = Math.max(totalWidth, FontText.width(tableTitle()) + 12);
        int shown = Math.min(ChuStatsuConfig.hudMaxPlayers, rows.size());
        int topRows = showHeader ? 2 : 0;
        long now = System.nanoTime() / 1_000_000L;
        List<TableLayoutMotion.Display<Row>> displayRows = hudLayout.update(rows.subList(0, shown),
            row -> row.name, now, ChuStatsuConfig.hudExitDuration, ChuStatsuConfig.hudResizeDuration,
            !ChuStatsuConfig.lowPerformanceMode, ChuStatsuConfig.hudExitAnimation != 0);
        hudLayout.size(totalWidth, 11 * (shown + topRows), now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.hudResizeDuration);
        int panelWidth = Math.max(1, Math.round(hudLayout.width(now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.hudResizeDuration)));
        int panelHeight = Math.max(1, Math.round(hudLayout.height(now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.hudResizeDuration)));
        TableHud.HudTable handle = TableHud.active(TableHud.HudTable.class);
        if (handle != null && handle.getHidden()) {
            hudMotion.snapHidden();
            return;
        }
        if (handle != null) handle.size(panelWidth + 12, panelHeight + 6);
        int x = handle == null ? 8
            : Math.round(handle.getX()) + 6;
        int y = handle == null ? 8 : Math.round(handle.getY()) + 3;
        float scale = handle == null ? 1f : handle.getEffectiveScale();
        GL11.glPushMatrix();
        GL11.glTranslatef(x - 6, y - 3, 0);
        GL11.glScalef(scale, scale, 1f);
        GL11.glTranslatef(6 - x, 3 - y, 0);
        applyMotion(TableMotion.pose(motionMode, progress), x, y, totalWidth, 11 * (shown + topRows));
        int opacity = Math.max(0, Math.min(100, ChuStatsuConfig.hudBackgroundOpacity));
        PanelStyle.panel(x - 6, y - 3, x + panelWidth + 6,
            y + panelHeight + 3, opacity, ChuStatsuConfig.hudGlass);
        BackgroundImages.draw(false, x - 6, y - 3, panelWidth + 12, panelHeight + 6);
        boolean clipped = beginClip(mc, window, x - 6, y - 3,
            panelWidth + 12, panelHeight + 6, scale, progress);
        if (showHeader) drawTableTitle(x, panelWidth, y);
        int headerX = x;
        if (showHeader) {
            for (int c = 0; c < columns.size(); c++) {
                String header = "§e" + ColumnLayout.header(columns.get(c));
                FontText.draw(header, cellTextX(columns.get(c), header, headerX, widths[c]), y + 12);
                headerX += widths[c];
            }
        }
        for (int i = 0; i < displayRows.size(); i++) {
            var animated = displayRows.get(i);
            Row row = animated.value();
            int rowX = x + rowOffsetX(hudLayout, animated, now,
                ChuStatsuConfig.hudEntryAnimation, ChuStatsuConfig.hudExitAnimation,
                ChuStatsuConfig.hudEntryDuration, ChuStatsuConfig.hudExitDuration);
            int cellX = rowX;
            int rowY = y + 11 * (i + topRows) + rowOffsetY(hudLayout, animated, now,
                ChuStatsuConfig.hudEntryAnimation, ChuStatsuConfig.hudExitAnimation,
                ChuStatsuConfig.hudEntryDuration, ChuStatsuConfig.hudExitDuration,
                ChuStatsuConfig.hudResizeDuration);
            String status = ColumnLayout.status(row.stats);
            int[] span = status == null ? null : statusSpan(columns, row.stats);
            if (ChuStatsuConfig.hudAlternatingRows && (i & 1) == 0) {
                PanelStyle.rounded(rowX, rowY, rowX + totalWidth, rowY + 11, 0, ROW_STRIPE);
            }
            for (int c = 0; c < columns.size(); c++) {
                String column = columns.get(c);
                if (span != null && c >= span[0] && c <= span[1]) {
                    if (c == span[0]) {
                        int spanWidth = 0;
                        for (int k = span[0]; k <= span[1]; k++) spanWidth += widths[k];
                        FontText.draw(status, cellX + Math.max(0, (spanWidth - FontText.width(status)) / 2), rowY + 2);
                    }
                    cellX += widths[c];
                    continue;
                }
                if (span != null && row.stats.status() == StatsView.Status.ERROR && statisticColumn(column)) {
                    cellX += widths[c];
                    continue;
                }
                if (column.equals("Head")) {
                    PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(row.name);
                    if (info != null) drawHead(mc, info, cellX + 5, rowY + 2);
                } else if (ChuStatsuConfig.hudLoadingSkeleton && row.stats.status() == StatsView.Status.LOADING
                    && statisticColumn(column)) {
                    drawLoadingCell(cellX, rowY, widths[c], c);
                } else if (ColumnLayout.isName(column)) {
                    String displayed = displayNames.get(row.name);
                    if (displayed == null) displayed = displayName(mc, row.name, waiting, inGame);
                    drawNameCell(column, displayed, nameSecondary(mc, row.name),
                        cellX, rowY + 2, widths[c], false);
                } else {
                    String value = ColumnLayout.cell(column, row.name, row.stats, row.ping, null);
                    FontText.draw(value, cellTextX(column, value, cellX, widths[c]), rowY + 2);
                }
                cellX += widths[c];
            }
        }
        if (ChuStatsuConfig.hudColumnDividers)
            drawDividers(x, y + (showHeader ? 11 : 0),
                y + 11 * (shown + topRows), y + 11 * topRows, widths,
                statusSpans(columns, rows, shown));
        if (clipped) GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    public boolean shouldRenderTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.tabEnabled || mc.options == null
            || !mc.options.playerListKey.isPressed()) return false;
        return shouldRenderTab(mc, PikaContext.sidebarState(mc));
    }

    private boolean shouldRenderTab(Minecraft mc, PikaContext.SidebarState sidebar) {
        return ChuStatsuConfig.enabled && ChuStatsuConfig.tabEnabled && mc.options != null
            && mc.options.playerListKey.isPressed() && allowed(mc, sidebar)
            && visible(mc, sidebar, ChuStatsuConfig.tabAlwaysShow, ChuStatsuConfig.tabShowWaiting,
                ChuStatsuConfig.tabShowInGame);
    }

    public void renderTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.tabEnabled || mc.getNetworkHandler() == null) {
            tabMotion.snapHidden();
            return;
        }
        if (mc.options != null && !mc.options.playerListKey.isPressed() && tabMotion.isHidden()) return;
        PikaContext.SidebarState sidebar = PikaContext.sidebarState(mc);
        if (!allowed(mc, sidebar)) {
            tabMotion.snapHidden();
            return;
        }
        boolean show = shouldRenderTab(mc, sidebar);
        int motionMode = show ? ChuStatsuConfig.tabShowAnimation : ChuStatsuConfig.tabHideAnimation;
        float progress = visibilityProgress(tabMotion, show, motionMode,
            ChuStatsuConfig.tabShowDuration, ChuStatsuConfig.tabHideDuration);
        if (progress <= 0f) return;
        boolean waiting = sidebar.waiting();
        boolean inGame = sidebar.inGame();
        prepareTeams(mc, inGame);
        boolean showHeader = ChuStatsuConfig.tabShowHeader && apiAllowed(mc, sidebar);
        List<String> columns = visibleColumns(ChuStatsuConfig.tabColumns, true, sidebar);
        int padding = columnPadding();
        ArrayList<PlayerInfo> players = new ArrayList<>();
        for (PlayerInfo info : mc.getNetworkHandler().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (name != null && name.matches("[A-Za-z0-9_]{3,16}")) players.add(info);
        }
        players.sort((a, b) -> compareTeams(mc, a.getProfile().getName(), b.getProfile().getName(), inGame));
        if (waiting && ChuStatsuConfig.sortTab) {
            players.sort(Comparator.comparingInt((PlayerInfo info) -> socialRank(info.getProfile().getName()))
                .thenComparing(info -> {
                    StatsView view = viewFor(info.getProfile().getName());
                    return view == null ? null : view.sortValue(ChuStatsuConfig.tabSortStat);
                }, Comparator.nullsLast(Comparator.reverseOrder())));
        }
        if (players.isEmpty()) {
            tabMotion.snapHidden();
            tabLayout.clear();
            return;
        }
        int shown = Math.min(ChuStatsuConfig.tabMaxPlayers, players.size());
        Map<String, String> displayNames = new HashMap<>();
        for (int i = 0; i < shown; i++) {
            PlayerInfo info = players.get(i);
            String name = info.getProfile().getName();
            displayNames.put(name, displayName(mc, name, waiting, inGame));
        }
        int[] widths = new int[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            String column = columns.get(c);
            widths[c] = column.equals("Head") ? 18 : FontText.width(ColumnLayout.header(column)) + padding;
            for (int i = 0; i < shown; i++) {
                PlayerInfo info = players.get(i);
                String name = info.getProfile().getName();
                String value = ColumnLayout.cell(column, displayNames.get(name), viewFor(name), info.getPing(),
                    column.equals("HP") ? tabHealth(mc, name, inGame) : null);
                StatsView state = viewFor(name);
                if (ColumnLayout.status(state) == null || ColumnLayout.isName(column)
                    || column.equals("Level") && state != null && state.level() != null) {
                    int content = FontText.width(value);
                    if (ColumnLayout.isName(column) && ClientBadge.visible(info)) content += 12;
                    if (column.equals("Dynamic name box")) {
                        String secondary = nameSecondary(mc, name);
                        if (!secondary.isEmpty()) content += FontText.width(secondary) + 5;
                    }
                    widths[c] = Math.max(widths[c], content + padding + (ColumnLayout.isName(column) ? NAME_INSET : 0));
                }
            }
        }
        for (int i = 0; i < shown; i++)
            ensureStatusWidth(columns, widths, viewFor(players.get(i).getProfile().getName()));
        int tableWidth = 0;
        for (int width : widths) tableWidth += width;
        if (showHeader) tableWidth = Math.max(tableWidth, FontText.width(tableTitle()) + 14);
        Window window = new Window(mc);
        int topRows = showHeader ? 2 : 0;
        long now = System.nanoTime() / 1_000_000L;
        List<TableLayoutMotion.Display<PlayerInfo>> displayRows = tabLayout.update(players.subList(0, shown),
            info -> info.getProfile().getName(), now, ChuStatsuConfig.tabExitDuration,
            ChuStatsuConfig.tabResizeDuration,
            !ChuStatsuConfig.lowPerformanceMode, ChuStatsuConfig.tabExitAnimation != 0);
        tabLayout.size(tableWidth, 11 * (shown + topRows), now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.tabResizeDuration);
        int panelWidth = Math.max(1, Math.round(tabLayout.width(now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.tabResizeDuration)));
        int panelHeight = Math.max(1, Math.round(tabLayout.height(now,
            ChuStatsuConfig.lowPerformanceMode ? 0 : ChuStatsuConfig.tabResizeDuration)));
        TableHud.TabTable handle = TableHud.active(TableHud.TabTable.class);
        if (handle != null && handle.getHidden()) {
            tabMotion.snapHidden();
            return;
        }
        int overviewHeight = ChuStatsuConfig.tabMatchOverview && inGame && matchOverview != null ? 15 : 0;
        if (handle != null) handle.size(panelWidth + 14, panelHeight + 8 + overviewHeight);
        if (handle != null && !tabCenterMigrated) {
            tabCenterMigrated = true;
            if (shouldCenterNewTab(handle.getSection(), handle.getLayoutRefW())
                || handle.getSection() == Section.TopCenter && Math.abs(handle.getRelativeX() - 85f) < 1f) {
                handle.setSection(Section.TopCenter);
                handle.setRelativeX(0f);
            }
        }
        boolean centered = handle != null && handle.getSection() == Section.TopCenter
            && Math.abs(handle.getRelativeX()) < .5f;
        int x = handle == null ? Math.max(4, ((int) window.getScaledWidth() - tableWidth) / 2)
            : centered ? centeredTabX((int) window.getScaledWidth(), panelWidth, handle.getEffectiveScale())
                : Math.round(handle.getX()) + 7;
        int y = handle == null ? 26 : Math.round(handle.getY()) + 4;
        float scale = handle == null ? 1f : handle.getEffectiveScale();
        GL11.glPushMatrix();
        GL11.glTranslatef(x - 7, y - 4, 0);
        GL11.glScalef(scale, scale, 1f);
        GL11.glTranslatef(7 - x, 4 - y, 0);
        applyMotion(TableMotion.pose(motionMode, progress), x, y, tableWidth, 11 * (shown + topRows));
        int opacity = Math.max(0, Math.min(100, ChuStatsuConfig.tabBackgroundOpacity));
        PanelStyle.panel(x - 7, y - 4, x + panelWidth + 7,
            y + panelHeight + 4, opacity, ChuStatsuConfig.tabGlass);
        BackgroundImages.draw(true, x - 7, y - 4, panelWidth + 14, panelHeight + 8);
        boolean clipped = beginClip(mc, window, x - 7, y - 4,
            panelWidth + 14, panelHeight + 8, scale, progress);
        if (showHeader) {
            drawTableTitle(x, panelWidth, y);
            int cellX = x;
            for (int c = 0; c < columns.size(); c++) {
                if (!columns.get(c).equals("Head"))
                    FontText.draw("§e" + ColumnLayout.header(columns.get(c)),
                        cellTextX(columns.get(c), "§e" + ColumnLayout.header(columns.get(c)),
                            cellX, widths[c]), y + 12);
                cellX += widths[c];
            }
        }
        for (int i = 0; i < displayRows.size(); i++) {
            var animated = displayRows.get(i);
            PlayerInfo info = animated.value();
            String name = info.getProfile().getName();
            String displayed = displayNames.get(name);
            if (displayed == null) displayed = displayName(mc, name, waiting, inGame);
            int rowX = x + rowOffsetX(tabLayout, animated, now,
                ChuStatsuConfig.tabEntryAnimation, ChuStatsuConfig.tabExitAnimation,
                ChuStatsuConfig.tabEntryDuration, ChuStatsuConfig.tabExitDuration);
            int rowY = y + 11 * (i + topRows) + rowOffsetY(tabLayout, animated, now,
                ChuStatsuConfig.tabEntryAnimation, ChuStatsuConfig.tabExitAnimation,
                ChuStatsuConfig.tabEntryDuration, ChuStatsuConfig.tabExitDuration,
                ChuStatsuConfig.tabResizeDuration);
            StatsView view = viewFor(name);
            String status = ColumnLayout.status(view);
            int[] span = status == null ? null : statusSpan(columns, view);
            if (ChuStatsuConfig.tabAlternatingRows && (i & 1) == 0)
                PanelStyle.rounded(rowX, rowY, rowX + tableWidth, rowY + 11, 0, ROW_STRIPE);
            int cellX = rowX;
            for (int c = 0; c < columns.size(); c++) {
                String column = columns.get(c);
                if (span != null && c >= span[0] && c <= span[1]) {
                    if (c == span[0]) {
                        int spanWidth = 0;
                        for (int k = span[0]; k <= span[1]; k++) spanWidth += widths[k];
                        FontText.draw(status, cellX + Math.max(0, (spanWidth - FontText.width(status)) / 2), rowY + 2);
                    }
                    cellX += widths[c];
                    continue;
                }
                if (span != null && view.status() == StatsView.Status.ERROR && statisticColumn(column)) {
                    cellX += widths[c];
                    continue;
                }
                if (column.equals("Head")) drawHead(mc, info, cellX + 5, rowY + 2);
                else if (ChuStatsuConfig.tabLoadingSkeleton && loading(view)
                    && statisticColumn(column)) drawLoadingCell(cellX, rowY, widths[c], c);
                else if (ColumnLayout.isName(column)) {
                    String value = displayed;
                    drawNameCell(column, value, nameSecondary(mc, name), cellX, rowY + 2, widths[c],
                        ClientBadge.visible(info));
                    if (ClientBadge.visible(info)) ClientBadge.draw(cellX + NAME_INSET + FontText.width(value) + 2, rowY + 3);
                } else {
                    String value = ColumnLayout.cell(column, displayed, view, info.getPing(),
                        column.equals("HP") ? tabHealth(mc, name, inGame) : null);
                    FontText.draw(value, cellTextX(column, value, cellX, widths[c]), rowY + 2);
                }
                cellX += widths[c];
            }
        }
        if (ChuStatsuConfig.tabColumnDividers)
            drawDividers(x, y + (showHeader ? 11 : 0),
                y + 11 * (shown + topRows), y + 11 * topRows, widths,
                statusSpans(columns, players, shown));
        if (clipped) GL11.glPopAttrib();
        if (ChuStatsuConfig.tabMatchOverview && inGame && matchOverview != null) {
            int overviewWidth = FontText.width(matchOverview);
            int overviewX = x + (tableWidth - overviewWidth) / 2;
            int overviewY = y + panelHeight + 11;
            PanelStyle.rounded(overviewX - 5, overviewY - 3, overviewX + overviewWidth + 5,
                overviewY + 10, 6, 0xA0101820);
            FontText.draw(matchOverview, overviewX, overviewY);
        }
        GL11.glPopMatrix();
    }

    static boolean shouldCenterNewTab(Section section, float layoutRefWidth) {
        return section == Section.TopLeft && layoutRefWidth <= 0f;
    }

    static int centeredTabX(int screenWidth, int panelWidth, float scale) {
        return Math.round((screenWidth - (panelWidth + 14) * scale) / 2f) + 7;
    }

    private String displayName(Minecraft mc, String name, boolean waiting, boolean inGame) {
        PlayerInfo info = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getOnlinePlayer(name);
        if (info != null && info.getGameMode() == WorldSettings.GameMode.SPECTATOR && inGame) {
            String color = ChuStatsuConfig.spectatorTeamColor
                ? teamIdentity(mc, name).color() : "§7";
            return color + "§o" + name;
        }
        String nameColor = inGame ? teamIdentity(mc, name).color() : "§f";
        int otherGroup = waiting && ChuStatsuConfig.detectOtherParties
            ? otherPartyGroups.getOrDefault(name.toLowerCase(Locale.ROOT), -1) : -1;
        if (otherGroup >= 0 && !social.isParty(name) && !social.isFriend(name))
            nameColor = otherPartyColor(otherGroup);
        if (!inGame && ChuStatsuConfig.highlightFriends && social.isFriend(name)) nameColor = "§6";
        if (!inGame && ChuStatsuConfig.highlightParty && social.isParty(name)) nameColor = "§d";
        return nameColor + name;
    }

    private void prepareTeams(Minecraft mc, boolean inGame) {
        if (teamWorld != mc.world || !inGame) {
            trackedTeams.clear();
            teamWorld = mc.world;
        }
        if (!inGame || mc.getNetworkHandler() == null) return;
        Set<String> online = new HashSet<>();
        for (PlayerInfo info : mc.getNetworkHandler().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (name == null) continue;
            online.add(name.toLowerCase(Locale.ROOT));
            if (info.getGameMode() != WorldSettings.GameMode.SPECTATOR) teamIdentity(mc, name);
        }
        trackedTeams.keySet().retainAll(online);
    }

    private TeamIdentity teamIdentity(Minecraft mc, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        PlayerInfo info = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getOnlinePlayer(name);
        TeamIdentity previous = trackedTeams.get(key);
        if (info != null && info.getGameMode() == WorldSettings.GameMode.SPECTATOR && previous != null)
            return previous;
        Team team = mc.world == null ? null : mc.world.getScoreboard().getTeamOfMember(name);
        String formatted = formattedTabName(mc, name, info);
        int at = formatted.toLowerCase(Locale.ROOT).lastIndexOf(key);
        TeamIdentity current = new TeamIdentity(team == null ? "" : team.getName(),
            inGameNameColor(mc, name, formatted, at));
        if (info != null && info.getGameMode() != WorldSettings.GameMode.SPECTATOR)
            trackedTeams.put(key, current);
        return current;
    }

    private int compareTeams(Minecraft mc, String a, String b, boolean inGame) {
        TeamIdentity left = teamIdentity(mc, a);
        TeamIdentity right = teamIdentity(mc, b);
        if (inGame && ChuStatsuConfig.sortMatchTeams) {
            int color = Integer.compare(TeamOrder.rank(left.color(), left.name()),
                TeamOrder.rank(right.color(), right.name()));
            if (color != 0) return color;
        }
        int team = left.name().compareToIgnoreCase(right.name());
        return team != 0 ? team : a.compareToIgnoreCase(b);
    }

    private String nameSecondary(Minecraft mc, String name) {
        String real = effectiveName(name);
        if (ChuStatsuConfig.showDenickedName && real != null && !real.equalsIgnoreCase(name))
            return "§7§o" + real;
        PlayerInfo info = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getOnlinePlayer(name);
        String formatted = formattedTabName(mc, name, info);
        return RankLabel.besideName(formatted, name, null).trim();
    }

    private static String formattedTabName(Minecraft mc, String name, PlayerInfo info) {
        String display = info == null || info.getDisplayName() == null
            ? null : info.getDisplayName().getFormattedString();
        Team team = mc.world == null ? null : mc.world.getScoreboard().getTeamOfMember(name);
        String teamName = team == null ? null : team.getMemberDisplayName(name);
        if (teamName != null && team.getColor() != null && team.getColor().isColor()
            && "§f".equals(colorAt(teamName, teamName.toLowerCase(Locale.ROOT)
                .lastIndexOf(name.toLowerCase(Locale.ROOT)))))
            teamName = team.getColor() + teamName;
        if (display != null && RankLabel.hasBoldPrefix(display, name)) return display;
        if (teamName != null && RankLabel.hasBoldPrefix(teamName, name)) return teamName;
        if (display != null && !"§f".equals(colorAt(display, display.length()))) return display;
        return teamName != null ? teamName : display != null ? display : "§f" + name;
    }

    private static String inGameNameColor(Minecraft mc, String name, String formatted, int nameAt) {
        Team team = mc.world == null ? null : mc.world.getScoreboard().getTeamOfMember(name);
        if (team != null) {
            String teamName = team.getMemberDisplayName(name);
            int teamAt = teamName.toLowerCase(Locale.ROOT).lastIndexOf(name.toLowerCase(Locale.ROOT));
            String teamTextColor = colorAt(teamName, teamAt < 0 ? teamName.length() : teamAt);
            if (team.getColor() != null && team.getColor().isColor()
                && !"§f".equals(team.getColor().toString())) return team.getColor().toString();
            if (!"§f".equals(teamTextColor)) return teamTextColor;
            if (team.getColor() != null && team.getColor().isColor()) return team.getColor().toString();
        }
        return colorAt(formatted, nameAt < 0 ? formatted.length() : nameAt);
    }

    private static void drawNameCell(String column, String name, String secondary,
                                     int x, int y, int width, boolean badge) {
        FontText.draw(name, x + NAME_INSET, y);
        if (!column.equals("Dynamic name box") || secondary.isEmpty()) return;
        int right = x + width - Math.max(4, columnPadding() / 2);
        int leftEnd = x + NAME_INSET + FontText.width(name) + (badge ? 12 : 0) + 5;
        int secondaryWidth = FontText.width(secondary);
        if (right - secondaryWidth >= leftEnd) FontText.draw(secondary, right - secondaryWidth, y);
    }

    private static int cellTextX(String column, String value, int x, int width) {
        if (ColumnLayout.isName(column)) return x + NAME_INSET;
        if (column.equals("Head")) return x;
        return x + Math.max(0, (width - FontText.width(value)) / 2);
    }

    private static int columnPadding() {
        return 5 + Math.max(0, Math.min(16, ChuStatsuConfig.columnSpacing));
    }

    private static boolean beginClip(Minecraft mc, Window window, int left, int top,
                                     int width, int height, float scale, float progress) {
        if (progress < .999f) return false;
        float px = mc.width / (float) window.getScaledWidth();
        float py = mc.height / (float) window.getScaledHeight();
        int sx = Math.round(left * px);
        int sy = Math.round(mc.height - (top + height * scale) * py);
        int sw = Math.max(1, Math.round(width * scale * px));
        int sh = Math.max(1, Math.round(height * scale * py));
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT | GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
        return true;
    }

    private static <T> int rowOffsetX(TableLayoutMotion<T> motion, TableLayoutMotion.Display<T> row,
                                      long now, int entryMode, int exitMode,
                                      int entryDuration, int exitDuration) {
        if (ChuStatsuConfig.lowPerformanceMode) return 0;
        if (row.leaving()) {
            float amount = 18f * motion.exit(row.key(), now, exitDuration);
            return Math.round(exitMode == 1 ? -amount : exitMode == 2 ? amount : 0);
        }
        float amount = 18f * (1f - motion.entry(row.key(), now, entryDuration));
        return Math.round(entryMode == 1 ? -amount : entryMode == 2 ? amount : 0);
    }

    private static <T> int rowOffsetY(TableLayoutMotion<T> motion, TableLayoutMotion.Display<T> row,
                                      long now, int entryMode, int exitMode,
                                      int entryDuration, int exitDuration, int moveDuration) {
        if (ChuStatsuConfig.lowPerformanceMode) return 0;
        float offset = row.leaving()
            ? (exitMode == 3 ? 18f * motion.exit(row.key(), now, exitDuration) : 0f)
            : (entryMode == 3 ? 18f * (1f - motion.entry(row.key(), now, entryDuration)) : 0f);
        return Math.round(offset + (row.leaving() ? 0f : 11f * motion.move(row.key(), now, moveDuration)));
    }

    private static void drawDividers(int x, int top, int bottom, int rowTop, int[] widths,
                                     int[][] statusSpans) {
        int at = x;
        for (int i = 0; i + 1 < widths.length; i++) {
            at += widths[i];
            int cursor = top;
            for (int row = 0; row < statusSpans.length; row++) {
                int[] span = statusSpans[row];
                if (span == null || i < span[0] || i >= span[1]) continue;
                int cut = rowTop + row * 11;
                if (cut > cursor) drawDividerSegment(at, cursor, cut);
                cursor = cut + 11;
            }
            if (cursor < bottom) drawDividerSegment(at, cursor, bottom);
        }
    }

    private static void drawDividerSegment(int at, int top, int bottom) {
        if (ChuStatsuConfig.separatorShadows)
            GuiElement.fill(at - 1, top + 1, at, bottom, 0x50000000);
        GuiElement.fill(at - 2, top, at - 1, bottom, DIVIDER_COLOR);
    }

    static int[] statusSpan(List<String> columns, StatsView view) {
        boolean includeLevel = view == null || view.level() == null;
        int name = -1;
        for (int i = 0; i < columns.size(); i++)
            if (ColumnLayout.isName(columns.get(i))) { name = i; break; }
        if (view != null && view.status() == StatsView.Status.ERROR) {
            int first = -1, last = -1;
            for (int i = name + 1; i < columns.size(); i++) {
                if (!statisticColumn(columns.get(i))) continue;
                if (first < 0) first = i;
                last = i;
            }
            if (first >= 0) return new int[] {first, last};
        }
        int[] preferred = findStatusRun(columns, name + 1, includeLevel);
        return preferred == null ? findStatusRun(columns, 0, includeLevel) : preferred;
    }

    private static void ensureStatusWidth(List<String> columns, int[] widths, StatsView view) {
        String status = ColumnLayout.status(view);
        if (status == null) return;
        int[] span = statusSpan(columns, view);
        if (span == null) return;
        int available = 0;
        for (int i = span[0]; i <= span[1]; i++) available += widths[i];
        widths[span[1]] += Math.max(0, FontText.width(status) + 8 - available);
    }

    private static int[] findStatusRun(List<String> columns, int from, boolean includeLevel) {
        int start = -1;
        for (int i = from; i < columns.size(); i++) {
            if (statisticColumn(columns.get(i)) && (includeLevel || !columns.get(i).equals("Level"))) {
                if (start < 0) start = i;
            } else if (start >= 0) return new int[] {start, i - 1};
        }
        return start < 0 ? null : new int[] {start, columns.size() - 1};
    }

    private static int[][] statusSpans(List<String> columns, List<?> entries, int shown) {
        int[][] result = new int[shown][];
        for (int i = 0; i < shown; i++) {
            Object entry = entries.get(i);
            StatsView view = entry instanceof Row row ? row.stats
                : entry instanceof PlayerInfo info ? ChuStatsu.controller().viewFor(info.getProfile().getName()) : null;
            result[i] = ColumnLayout.status(view) == null ? null : statusSpan(columns, view);
        }
        return result;
    }

    private static String colorAt(String formatted, int end) {
        String color = "§f";
        for (int i = 0; i + 1 < end; i++) {
            if (formatted.charAt(i) == '§') {
                char code = Character.toLowerCase(formatted.charAt(++i));
                if ("0123456789abcdef".indexOf(code) >= 0) color = "§" + code;
            }
        }
        return color;
    }

    private static String tabHealth(Minecraft mc, String name, boolean inGame) {
        String value = health(mc, name);
        return value == null ? "§7?" : "§7" + value;
    }

    private static List<String> visibleColumns(String[] configured, boolean tab) {
        return visibleColumns(configured, tab, PikaContext.sidebarState(Minecraft.getInstance()));
    }

    private static List<String> visibleColumns(String[] configured, boolean tab,
                                               PikaContext.SidebarState sidebar) {
        List<String> selected = ColumnLayout.selected(configured, tab);
        if (!apiAllowed(Minecraft.getInstance(), sidebar)) {
            List<String> names = selected.stream().filter(column -> column.equals("Head")
                || ColumnLayout.isName(column)).toList();
            return names.stream().anyMatch(ColumnLayout::isName) ? names
                : names.isEmpty() ? List.of("Dynamic name box")
                    : List.of("Head", "Dynamic name box");
        }
        boolean dynamicName = selected.contains("Dynamic name box");
        List<String> visible = selected.stream()
            .filter(column -> !dynamicName || !column.equals("Rank"))
            .filter(column -> !tab || !column.equals("HP") || !ChuStatsuConfig.hpOnlyInGame
                || sidebar.inGame())
            .toList();
        return visible.isEmpty() ? List.of("Dynamic name box") : visible;
    }

    private static boolean loading(StatsView view) {
        return view == null || view.status() == StatsView.Status.LOADING;
    }

    private static boolean apiAllowed(Minecraft mc, PikaContext.SidebarState sidebar) {
        return !ChuStatsuConfig.apiOnlyInMatchOrWaiting
            || mc != null && mc.player != null && !mc.isSingleplayer()
                && (sidebar.waiting() || sidebar.inGame());
    }

    private static boolean statisticColumn(String column) {
        return !column.equals("Head") && !ColumnLayout.isName(column)
            && !column.equals("Ping") && !column.equals("HP");
    }

    private static String tableTitle() {
        String[] modes = {"Overall", "Solo", "Doubles", "Quads"};
        String[] periods = {"Lifetime", "Weekly", "Monthly", "Yearly"};
        String mode = modes[Math.max(0, Math.min(modes.length - 1, ChuStatsuConfig.mode))];
        String period = periods[Math.max(0, Math.min(periods.length - 1, ChuStatsuConfig.period))];
        return "Chustatsu §7• §f" + mode + " §7/ §f" + period;
    }

    private static void drawTableTitle(int x, int width, int y) {
        float at = x + (width - FontText.width(tableTitle())) / 2f;
        FontText.drawColored("Chu", at, y + 1, 0xF5B6D8);
        at += FontText.width("Chu");
        FontText.drawColored("statsu", at, y + 1, 0xF9E6A6);
        at += FontText.width("statsu");
        FontText.draw(tableTitle().substring("Chustatsu".length()), at, y + 1);
    }

    private static void drawLoadingCell(int x, int y, int width, int index) {
        int usable = Math.max(3, width - 12);
        float fraction = switch (index % 3) { case 0 -> .62f; case 1 -> .78f; default -> .48f; };
        int fill = Math.max(3, Math.round(usable * fraction));
        double wave = ChuStatsuConfig.lowPerformanceMode ? .5
            : .5 + .5 * Math.sin(System.nanoTime() / 180_000_000.0);
        int alpha = 30 + (int) (wave * 28);
        float left = x + (width - fill) / 2f;
        PanelStyle.rounded(left, y + 3.5f, left + fill, y + 7.5f, 2,
            (alpha << 24) | 0xAEB5BF);
    }

    private static float visibilityProgress(TableMotion motion, boolean show, int mode,
                                            int showMs, int hideMs) {
        if (ChuStatsuConfig.lowPerformanceMode || mode == 0) {
            motion.snap(show);
            return show ? 1f : 0f;
        }
        return motion.progress(show, System.nanoTime() / 1_000_000L, showMs, hideMs);
    }

    private static void applyMotion(TableMotion.Pose pose, int x, int y, int width, int height) {
        GL11.glTranslatef(0, pose.y(), 0);
        if (pose.scale() == 1f) return;
        float cx = x + width / 2f;
        float cy = y + height / 2f;
        GL11.glTranslatef(cx, cy, 0);
        GL11.glScalef(pose.scale(), pose.scale(), 1f);
        GL11.glTranslatef(-cx, -cy, 0);
    }

    private static void drawHead(Minecraft mc, PlayerInfo info, int x, int y) {
        try {
            mc.getTextureManager().bind(info.getSkinTexture());
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GuiElement.drawTexture(x, y, 8f, 8f, 8, 8, 64f, 64f);
            GuiElement.drawTexture(x, y, 40f, 8f, 8, 8, 64f, 64f);
        } catch (RuntimeException ignored) {
            // A missing skin should not hide the rest of the table.
        }
    }

    public String decorateTabName(String name, String original) {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.tabEnabled || !allowed(mc)
            || !visible(mc, ChuStatsuConfig.tabAlwaysShow, ChuStatsuConfig.tabShowWaiting,
                ChuStatsuConfig.tabShowInGame)) return original;
        StatsView view = viewFor(name);
        String marker = "";
        String realName = effectiveName(name);
        String alias = ChuStatsuConfig.showDenickedName && realName != null && !realName.equalsIgnoreCase(name)
            ? " §8→ " + realName : "";
        String status = ColumnLayout.status(view);
        if (status != null) return original + marker + alias + " §8│ " + status;
        StringBuilder display = new StringBuilder();
        List<String> columns = visibleColumns(ChuStatsuConfig.tabColumns, true);
        int ping = -1;
        if (mc.getNetworkHandler() != null) {
            PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(name);
            if (info != null) ping = info.getPing();
        }
        for (String column : columns) {
            if (!display.isEmpty()) display.append(" §8│ ");
            if (ColumnLayout.isName(column)) display.append(original).append(marker).append(alias);
            else display.append("§7").append(column).append(": §b")
                .append(ColumnLayout.cell(column, name, view, ping, column.equals("HP") ? health(mc, name) : null));
        }
        return display.toString();
    }

    public String decorateNametag(String name, String original) {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.nametagEnabled || !allowed(mc)
            || !visible(mc, ChuStatsuConfig.nametagAlwaysShow, ChuStatsuConfig.nametagShowWaiting,
                ChuStatsuConfig.nametagShowInGame)) return original;
        String display = original;
        String value = nametagValue(name);
        return ChuStatsuConfig.nametagDisplayMode == 0 || value == null
            ? display : display + " §7[" + value + "§7]";
    }

    public String nametagValue(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.enabled || !ChuStatsuConfig.nametagEnabled || !allowed(mc)
            || !visible(mc, ChuStatsuConfig.nametagAlwaysShow, ChuStatsuConfig.nametagShowWaiting,
                ChuStatsuConfig.nametagShowInGame)) return null;
        StatsView view = viewFor(name);
        String stat = NAMETAG_STATS[Math.max(0, Math.min(ChuStatsuConfig.nametagStat, NAMETAG_STATS.length - 1))];
        return view == null || view.status() != StatsView.Status.READY ? null : view.nametagValue(stat);
    }

    private StatsView viewFor(String name) {
        for (Row row : rows) {
            if (row.name.equalsIgnoreCase(name)) return row.stats;
        }
        return null;
    }

    public void observeChat(String message) {
        if (PikaContext.active(Minecraft.getInstance(), false)) social.observeChat(message);
    }

    public void onChatSend(ChatEvent.Send event) {
        String message = event.message == null ? "" : event.message.trim();
        if (message.equalsIgnoreCase("/chustatsu") || message.equalsIgnoreCase("/chuoverlay")) {
            event.cancelled = true;
            ChuStatsu.openConfig();
            return;
        }
        if (message.equalsIgnoreCase("/stats") || message.toLowerCase(Locale.ROOT).startsWith("/stats ")) {
            event.cancelled = true;
            String[] parts = message.split("\\s+", 2);
            Minecraft mc = Minecraft.getInstance();
            if (parts.length != 2 || !parts[1].matches("[A-Za-z0-9_]{3,16}")) {
                if (mc.player != null) mc.player.addMessage(new LiteralText("§bChuStatsu §7Usage: /stats <player>"));
            } else {
                pendingLookup = parts[1];
                if (mc.player != null) mc.player.addMessage(new LiteralText("§bChuStatsu §7Loading " + parts[1] + "..."));
            }
        }
    }

    private static String chatSummary(StatsView stats) {
        if (stats.status() != StatsView.Status.READY) {
            return "§bChuStatsu §f" + stats.username() + " §7" + stats.message();
        }
        return "§bChuStatsu §f" + stats.username() + " §7FKDR §b" + stats.fkdr()
            + " §7WLR §b" + stats.wlr() + " §7Level §b" + stats.value("Level")
            + " §7Wins §b" + stats.value("Wins") + " §7Beds §b" + stats.value("Beds");
    }

    public void observeTeam(TeamS2CPacket packet) {
        if (!ChuStatsuConfig.denickEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!PikaContext.active(mc, false)) {
            denickDiagnostic("skipped reason=outside_pika_or_no_player team=" + packet.getName()
                + " action=" + packet.getAction() + " members=" + packet.getMembers());
            return;
        }
        boolean waiting = PikaContext.inWaitingRoom(mc);
        if (packet.getAction() == 3 || packet.getAction() == 4)
            denickDiagnostic("packet team=" + packet.getName() + " action=" + packet.getAction()
                + " members=" + packet.getMembers() + " waiting=" + waiting
                + " waitingRoomOnly=" + ChuStatsuConfig.denickWaitingOnly);
        try {
            denick.onTeam(packet.getName(), packet.getAction(), packet.getMembers(),
                System.currentTimeMillis(), !ChuStatsuConfig.denickWaitingOnly || waiting);
        } catch (RuntimeException error) {
            java.io.StringWriter trace = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(trace));
            denickDiagnostic("failed reason=exception team=" + packet.getName()
                + " action=" + packet.getAction() + " members=" + packet.getMembers()
                + " waiting=" + waiting + " error=" + trace);
        }
    }

    private void denickDiagnostic(String detail) {
        if (!ChuStatsuConfig.debugMode) return;
        String line = java.time.Instant.now() + " " + detail;
        ChuStatsu.LOGGER.info("Denick {}", line);
        try {
            Minecraft mc = Minecraft.getInstance();
            java.nio.file.Path folder = mc.gameDir.toPath().resolve("config/chustatsu/debug");
            java.nio.file.Files.createDirectories(folder);
            java.nio.file.Files.writeString(folder.resolve("denick.log"), line + "\n",
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) {
            ChuStatsu.LOGGER.error("Could not write denick debug log", error);
        }
    }

    public void observeTabList(TabListS2CPacket packet) {
        if (packet == null) return;
        String header = packet.getHeader() == null ? "" : packet.getHeader().getFormattedString();
        String footer = packet.getFooter() == null ? "" : packet.getFooter().getFormattedString();
        rawTabHeader = header;
        rawTabFooter = footer;
        matchOverview = MatchOverview.parse(header + "\n" + footer);
    }

    public void dumpTabData() {
        Minecraft mc = Minecraft.getInstance();
        if (!ChuStatsuConfig.debugMode || mc.getNetworkHandler() == null) return;
        StringBuilder data = new StringBuilder("ChuStatsu raw TAB snapshot\n")
            .append("Captured: ").append(java.time.Instant.now()).append('\n')
            .append("Header: ").append(rawTabHeader).append("\nFooter: ").append(rawTabFooter)
            .append("\nPlayers:\n");
        for (PlayerInfo info : mc.getNetworkHandler().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            Team team = mc.world == null ? null : mc.world.getScoreboard().getTeamOfMember(name);
            String formatted = formattedTabName(mc, name, info);
            data.append("name=").append(name)
                .append(" | display=").append(info.getDisplayName() == null ? "<none>"
                    : info.getDisplayName().getFormattedString())
                .append(" | team=").append(team == null ? "<none>" : team.getName())
                .append(" | teamFormatted=").append(team == null ? "<none>" : team.getMemberDisplayName(name))
                .append(" | teamColor=").append(team == null ? "<none>" : team.getColor())
                .append(" | chosenFormatted=").append(formatted)
                .append(" | rank=").append(RankLabel.besideName(formatted, name, null).trim())
                .append(" | ping=").append(info.getPing())
                .append(" | mode=").append(info.getGameMode()).append('\n');
        }
        try {
            java.nio.file.Path folder = mc.gameDir.toPath().resolve("config/chustatsu/debug");
            java.nio.file.Files.createDirectories(folder);
            String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now());
            java.nio.file.Path file = folder.resolve("tab-" + stamp + ".txt");
            java.nio.file.Files.writeString(file, data, java.nio.charset.StandardCharsets.UTF_8);
            if (mc.player != null) mc.player.addMessage(new LiteralText("§bChuStatsu §7Saved raw TAB data: " + file.getFileName()));
            ChuStatsu.LOGGER.info("Saved raw TAB snapshot to {}", file);
        } catch (java.io.IOException error) {
            ChuStatsu.LOGGER.error("Could not save raw TAB snapshot", error);
            if (mc.player != null) mc.player.addMessage(new LiteralText("§cChuStatsu could not save raw TAB data"));
        }
    }

    public void observePlayerInfo(PlayerInfoS2CPacket packet) {
        if (!ChuStatsuConfig.detectOtherParties || !PikaContext.active(Minecraft.getInstance(), false)) return;
        boolean add = packet.getAction() == PlayerInfoS2CPacket.Action.ADD_PLAYER;
        boolean remove = packet.getAction() == PlayerInfoS2CPacket.Action.REMOVE_PLAYER;
        if (!add && !remove) return;
        long now = System.currentTimeMillis();
        for (PlayerInfoS2CPacket.Entry entry : packet.getEntries()) {
            if (entry.getProfile() == null) continue;
            String name = entry.getProfile().getName();
            if (add) otherParties.arrived(name, now);
            else otherParties.left(name);
        }
    }

    private String effectiveName(String visibleName) {
        return ChuStatsuConfig.denickEnabled ? denick.resolve(visibleName) : visibleName;
    }

    private static boolean allowed(Minecraft mc) {
        return PikaContext.active(mc, ChuStatsuConfig.bedWarsOnly, ChuStatsuConfig.onlyOnPika);
    }

    private static boolean allowed(Minecraft mc, PikaContext.SidebarState sidebar) {
        return PikaContext.active(mc, ChuStatsuConfig.bedWarsOnly, ChuStatsuConfig.onlyOnPika, sidebar);
    }

    private static boolean visible(Minecraft mc, boolean always, boolean waiting, boolean inGame) {
        return always || (waiting && PikaContext.inWaitingRoom(mc)) || (inGame && PikaContext.inGame(mc));
    }

    private static boolean visible(Minecraft mc, PikaContext.SidebarState sidebar,
                                   boolean always, boolean waiting, boolean inGame) {
        return always || mc != null && mc.player != null && !mc.isSingleplayer()
            && ((waiting && sidebar.waiting()) || (inGame && sidebar.inGame()));
    }

    private static String health(Minecraft mc, String name) {
        if (mc.world == null) return null;
        Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective objective = board.getDisplayObjective(2);
        if (objective != null) {
            String label = objective.getDisplayName().toLowerCase(Locale.ROOT);
            if ((objective.getRenderType().toString().equalsIgnoreCase("HEARTS")
                || label.contains("health") || label.contains("hp") || label.contains("heart"))
                && board.hasScore(name, objective)) return Integer.toString(board.getScore(name, objective).get());
        }
        PlayerEntity player = mc.world.getPlayer(name);
        return player == null ? null : Integer.toString(Math.round(player.getHealth()));
    }

    private int socialRank(String name) {
        if (ChuStatsuConfig.partyFirst && social.isParty(name)) return 0;
        if (ChuStatsuConfig.friendsAfterParty && social.isFriend(name)) return 1;
        return 2;
    }

    private static String otherPartyColor(int group) {
        return new String[] {"§c", "§9", "§a", "§e", "§b", "§f", "§d", "§7"}[group % 8];
    }

    public void close() {
        api.close();
    }
}
