package dev.chustatsu;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.polyfrost.oneconfig.api.config.v1.annotations.Accordion;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

/** Settings are persisted and displayed by the installed OneConfig v1 runtime. */
public final class ChuStatsuConfig extends Config {
    @Switch(title = "Enabled", description = "Show ChuStatsu on PikaNetwork BedWars")
    public static boolean enabled = true;

    @Keybind(title = "Toggle ChuStatsu", category = "General")
    public static OneConfigKeybind toggleMod = KeybindHelper.builder()
        .name("Toggle ChuStatsu").category("ChuStatsu")
        .action((Runnable) () -> {
            enabled = !enabled;
            ChuStatsu.saveConfig();
        }).register();

    @Switch(title = "TAB statistics", description = "Replace the player list with BedWars statistics", category = "Tab")
    public static boolean tabEnabled = true;

    @Switch(title = "HUD", description = "Show BedWars statistics in the HUD", category = "HUD")
    public static boolean hudEnabled = true;

    @Switch(title = "Nametag statistics", description = "Append BedWars statistics to player nametags", category = "Nametag")
    public static boolean nametagEnabled = true;

    @Switch(title = "Nametags always show", category = "Nametag", subcategory = "Stats")
    public static boolean nametagAlwaysShow = true;

    @Switch(title = "Nametags in waiting room", category = "Nametag", subcategory = "Stats")
    public static boolean nametagShowWaiting = false;

    @Switch(title = "Nametags in game", category = "Nametag", subcategory = "Stats")
    public static boolean nametagShowInGame = false;

    @Dropdown(title = "Nametag statistic", options = {"FKDR", "Level", "WLR", "Winstreak", "Final kills", "Wins", "Beds"}, category = "Nametag", subcategory = "Stats")
    public static int nametagStat = 0;

    @Dropdown(title = "Nametag display mode", options = {"Above username", "With username"},
        category = "Nametag", subcategory = "Stats")
    public static int nametagDisplayMode = 0;

    @Switch(title = "Only on PikaNetwork", category = "General")
    public static boolean onlyOnPika = true;

    @Switch(title = "Only make API requests in game and waiting lobby",
        description = "Show heads and names elsewhere without requesting statistics", category = "General")
    public static boolean apiOnlyInMatchOrWaiting = true;

    @Switch(title = "Show HP only in game", category = "General")
    public static boolean hpOnlyInGame = true;

    @Switch(title = "Text shadows", category = "General", subcategory = "Appearance")
    public static boolean textShadows = true;

    @Switch(title = "Separator shadows", description = "Add a subtle shadow beside table dividers",
        category = "General", subcategory = "Appearance")
    public static boolean separatorShadows = true;

    @Switch(title = "Sort match teams by color",
        description = "Use Red, Blue, Green, Yellow, Aqua, White, Pink, Gray order in HUD and TAB",
        category = "General", subcategory = "Stats")
    public static boolean sortMatchTeams = true;

    @Switch(title = "Keep team color while spectating",
        description = "Keep the team color and italicize names during respawn",
        category = "General", subcategory = "Appearance")
    public static boolean spectatorTeamColor = true;

    @Switch(title = "Low performance mode", description = "Disable animation and glass backgrounds", category = "General")
    public static boolean lowPerformanceMode = false;

    @Slider(title = "Column spacing", min = 0, max = 16, step = 1, category = "General")
    public static int columnSpacing = 6;

    @Switch(title = "Remove ms from ping", category = "General")
    public static boolean removePingMs = true;

    @Accordion(title = "Font", category = "General", subcategory = "Appearance")
    public static final class FontOptions {
        @Dropdown(title = "Typeface", options = {"Minecraft", "Poppins", "Custom TTF"})
        public static int fontMode = 0;

        @Text(title = "Custom TTF filename")
        public static String customFont = "custom.ttf";
    }

    @Switch(title = "HUD always show", category = "HUD", subcategory = "Appearance")
    public static boolean hudAlwaysShow = false;

    @Switch(title = "HUD in waiting room", category = "HUD", subcategory = "Appearance")
    public static boolean hudShowWaiting = true;

    @Switch(title = "HUD in game", category = "HUD", subcategory = "Appearance")
    public static boolean hudShowInGame = false;

    @Switch(title = "Hide HUD while TAB is held", category = "HUD", subcategory = "Appearance")
    public static boolean hideHudWhileTab = true;

    @Slider(title = "Max HUD players", min = 1, max = 40, step = 1, category = "HUD", subcategory = "Appearance")
    public static int hudMaxPlayers = 16;

    @Switch(title = "HUD header", category = "HUD", subcategory = "Appearance")
    public static boolean hudShowHeader = true;

    @Switch(title = "Combine HUD rank with name", category = "HUD", subcategory = "Appearance")
    public static boolean hudCombineRankWithName = true;

    @Switch(title = "HUD loading skeleton", description = "Show placeholders while statistics load",
        category = "HUD", subcategory = "Appearance")
    public static boolean hudLoadingSkeleton = true;

    @Switch(title = "Alternating HUD rows", category = "HUD", subcategory = "Appearance")
    public static boolean hudAlternatingRows = true;

    @Switch(title = "HUD column dividers", category = "HUD", subcategory = "Appearance")
    public static boolean hudColumnDividers = true;

    @Switch(title = "Glass background", category = "HUD", subcategory = "Appearance")
    public static boolean hudGlass = false;

    @Slider(title = "HUD background opacity", min = 0, max = 100, step = 1,
        category = "HUD", subcategory = "Appearance")
    public static int hudBackgroundOpacity = 68;

    @Dropdown(title = "HUD show animation", options = {"None", "Slide", "Zoom", "Bounce", "Pop"},
        category = "HUD", subcategory = "Appearance")
    public static int hudShowAnimation = 1;

    @Slider(title = "HUD show duration (ms)", min = 40, max = 500, step = 10,
        category = "HUD", subcategory = "Appearance")
    public static int hudShowDuration = 120;

    @Dropdown(title = "HUD hide animation", options = {"None", "Slide", "Zoom", "Bounce", "Pop"},
        category = "HUD", subcategory = "Appearance")
    public static int hudHideAnimation = 4;

    @Slider(title = "HUD hide duration (ms)", min = 40, max = 500, step = 10,
        category = "HUD", subcategory = "Appearance")
    public static int hudHideDuration = 120;

    @Slider(title = "HUD resize duration (ms)", min = 40, max = 500, step = 10,
        category = "HUD", subcategory = "Animation")
    public static int hudResizeDuration = 150;

    @Dropdown(title = "HUD new player animation", options = {"None", "Slide left", "Slide right", "Slide up"},
        category = "HUD", subcategory = "Animation")
    public static int hudEntryAnimation = 1;

    @Slider(title = "HUD new player duration (ms)", min = 40, max = 500, step = 10,
        category = "HUD", subcategory = "Animation")
    public static int hudEntryDuration = 160;

    @Dropdown(title = "HUD disconnect animation", options = {"None", "Slide left", "Slide right", "Slide down"},
        category = "HUD", subcategory = "Animation")
    public static int hudExitAnimation = 1;

    @Slider(title = "HUD disconnect duration (ms)", min = 40, max = 500, step = 10,
        category = "HUD", subcategory = "Animation")
    public static int hudExitDuration = 160;

    @Switch(title = "HUD background image", category = "HUD", subcategory = "Image")
    public static boolean hudImageEnabled = false;

    @Text(title = "HUD PNG filename", category = "HUD", subcategory = "Image")
    public static String hudImageFile = "background.png";

    @Switch(title = "Random HUD image from folder", category = "HUD", subcategory = "Image")
    public static boolean hudRandomBackground = false;

    @Switch(title = "Fetch a random waifu (Catbox)", category = "HUD", subcategory = "Image")
    public static boolean hudCatboxImage = false;

    @Button(title = "Request new waifu image", text = "Fetch", category = "HUD", subcategory = "Image")
    private static void requestHudWaifu() { BackgroundImages.requestNewCatbox(false); }

    @Slider(title = "HUD image opacity", min = 10, max = 100, step = 1,
        category = "HUD", subcategory = "Image")
    public static int hudImageOpacity = 60;

    @Slider(title = "HUD image size (%)", min = 10, max = 200, step = 5,
        category = "HUD", subcategory = "Image")
    public static int hudImageSize = 100;

    @Dropdown(title = "HUD image position", options = {"Left", "Center", "Right"},
        category = "HUD", subcategory = "Image")
    public static int hudImagePosition = 2;

    @Switch(title = "Sort waiting-room HUD", category = "HUD", subcategory = "Appearance")
    public static boolean sortHud = true;

    @Dropdown(title = "HUD sort statistic", options = {"FKDR", "WLR", "Winstreak", "Final kills", "Wins", "Beds", "Level"},
        category = "HUD", subcategory = "Appearance")
    public static int hudSortStat = 0;

    @Switch(title = "TAB always show", category = "Tab", subcategory = "Appearance")
    public static boolean tabAlwaysShow = true;

    @Switch(title = "TAB in waiting room", category = "Tab", subcategory = "Appearance")
    public static boolean tabShowWaiting = true;

    @Switch(title = "TAB in game", category = "Tab", subcategory = "Appearance")
    public static boolean tabShowInGame = true;

    @Slider(title = "Max TAB players", min = 1, max = 40, step = 1, category = "Tab", subcategory = "Appearance")
    public static int tabMaxPlayers = 20;

    @Switch(title = "Sort waiting-room TAB", category = "Tab", subcategory = "Appearance")
    public static boolean sortTab = true;

    @Dropdown(title = "TAB sort statistic", options = {"FKDR", "WLR", "Winstreak", "Final kills", "Wins", "Beds", "Level"},
        category = "Tab", subcategory = "Appearance")
    public static int tabSortStat = 0;

    @Switch(title = "TAB header", category = "Tab", subcategory = "Appearance")
    public static boolean tabShowHeader = true;

    @Switch(title = "Combine TAB rank with name", category = "Tab", subcategory = "Appearance")
    public static boolean tabCombineRankWithName = true;

    @Switch(title = "TAB loading skeleton", description = "Show placeholders while statistics load",
        category = "Tab", subcategory = "Appearance")
    public static boolean tabLoadingSkeleton = true;

    @Switch(title = "Match overview", category = "Tab", subcategory = "Appearance")
    public static boolean tabMatchOverview = true;

    @Switch(title = "Show installed client badge", category = "Tab", subcategory = "Appearance")
    public static boolean tabClientIndicator = true;

    @Switch(title = "Alternating TAB rows", category = "Tab", subcategory = "Appearance")
    public static boolean tabAlternatingRows = true;

    @Switch(title = "TAB column dividers", category = "Tab", subcategory = "Appearance")
    public static boolean tabColumnDividers = true;

    @Switch(title = "Glass background", category = "Tab", subcategory = "Appearance")
    public static boolean tabGlass = false;

    @Slider(title = "TAB background opacity", min = 0, max = 100, step = 1,
        category = "Tab", subcategory = "Appearance")
    public static int tabBackgroundOpacity = 68;

    @Dropdown(title = "TAB show animation", options = {"None", "Slide", "Zoom", "Bounce", "Pop"},
        category = "Tab", subcategory = "Appearance")
    public static int tabShowAnimation = 1;

    @Slider(title = "TAB show duration (ms)", min = 40, max = 500, step = 10,
        category = "Tab", subcategory = "Appearance")
    public static int tabShowDuration = 120;

    @Dropdown(title = "TAB hide animation", options = {"None", "Slide", "Zoom", "Bounce", "Pop"},
        category = "Tab", subcategory = "Appearance")
    public static int tabHideAnimation = 4;

    @Slider(title = "TAB hide duration (ms)", min = 40, max = 500, step = 10,
        category = "Tab", subcategory = "Appearance")
    public static int tabHideDuration = 120;

    @Slider(title = "TAB resize duration (ms)", min = 40, max = 500, step = 10,
        category = "Tab", subcategory = "Animation")
    public static int tabResizeDuration = 150;

    @Dropdown(title = "TAB new player animation", options = {"None", "Slide left", "Slide right", "Slide up"},
        category = "Tab", subcategory = "Animation")
    public static int tabEntryAnimation = 1;

    @Slider(title = "TAB new player duration (ms)", min = 40, max = 500, step = 10,
        category = "Tab", subcategory = "Animation")
    public static int tabEntryDuration = 160;

    @Dropdown(title = "TAB disconnect animation", options = {"None", "Slide left", "Slide right", "Slide down"},
        category = "Tab", subcategory = "Animation")
    public static int tabExitAnimation = 1;

    @Slider(title = "TAB disconnect duration (ms)", min = 40, max = 500, step = 10,
        category = "Tab", subcategory = "Animation")
    public static int tabExitDuration = 160;

    @Switch(title = "TAB background image", category = "Tab", subcategory = "Image")
    public static boolean tabImageEnabled = false;

    @Text(title = "TAB PNG filename", category = "Tab", subcategory = "Image")
    public static String tabImageFile = "background.png";

    @Switch(title = "Random TAB image from folder", category = "Tab", subcategory = "Image")
    public static boolean tabRandomBackground = false;

    @Switch(title = "Fetch a random waifu (Catbox)", category = "Tab", subcategory = "Image")
    public static boolean tabCatboxImage = false;

    @Button(title = "Request new waifu image", text = "Fetch", category = "Tab", subcategory = "Image")
    private static void requestTabWaifu() { BackgroundImages.requestNewCatbox(true); }

    @Slider(title = "TAB image opacity", min = 10, max = 100, step = 1,
        category = "Tab", subcategory = "Image")
    public static int tabImageOpacity = 60;

    @Slider(title = "TAB image size (%)", min = 10, max = 200, step = 5,
        category = "Tab", subcategory = "Image")
    public static int tabImageSize = 100;

    @Dropdown(title = "TAB image position", options = {"Left", "Center", "Right"},
        category = "Tab", subcategory = "Image")
    public static int tabImagePosition = 2;

    @Switch(title = "Keep image size when table changes", category = "General", subcategory = "Assets")
    public static boolean staticImageSize = false;

    @Button(title = "Reload images and fonts", text = "Reload", category = "General", subcategory = "Assets")
    private static void reloadBackgroundImages() {
        BackgroundImages.reload();
        FontText.reload();
    }

    @Switch(title = "Show denicked name", category = "Denick")
    public static boolean showDenickedName = false;

    @Switch(title = "Highlight party", description = "Mark party members in TAB and the HUD", category = "Party")
    public static boolean highlightParty = true;

    @Switch(title = "Party members first", category = "Party")
    public static boolean partyFirst = true;

    @Switch(title = "Highlight friends", description = "Mark profile friends in TAB and the HUD", category = "Friends")
    public static boolean highlightFriends = true;

    @Switch(title = "Friends after party", category = "Friends")
    public static boolean friendsAfterParty = true;

    @Switch(title = "Group other arrivals", description = "Experimental: simultaneous waiting-room arrivals may share a color", category = "Party")
    public static boolean detectOtherParties = false;

    @Slider(title = "Arrival window (ms)", min = 50, max = 2000, step = 50, category = "Party")
    public static int otherPartyWindowMs = 350;

    @Switch(title = "Denick", description = "Track unambiguous waiting-room team name replacements", category = "Denick")
    public static boolean denickEnabled = false;

    @Switch(title = "BedWars only", description = "Require a BedWars sidebar", category = "General")
    public static boolean bedWarsOnly = true;

    @Dropdown(title = "Mode", options = {"Overall", "Solo", "Doubles", "Quads"}, category = "General", subcategory = "Stats")
    public static int mode = 0;

    @Dropdown(title = "Period", options = {"Lifetime", "Weekly", "Monthly", "Yearly"}, category = "General", subcategory = "Stats")
    public static int period = 0;

    @Slider(title = "Cache duration (seconds)", min = 300, max = 600, step = 30, category = "Debug")
    public static int cacheSeconds = 300;

    @Slider(title = "Max API players", min = 1, max = 40, step = 1, category = "Debug")
    public static int maxApiPlayers = 16;

    @Switch(title = "Debug mode", category = "Debug")
    public static boolean debugMode = false;

    @Button(title = "Save raw TAB data", text = "Write file", category = "Debug")
    private static void saveRawTabData() {
        if (debugMode && ChuStatsu.controller() != null) ChuStatsu.controller().dumpTabData();
    }

    @DraggableList(title = "TAB columns", description = "Drag to reorder; uncheck to hide", category = "Tab",
        subcategory = "Columns", checkable = true,
        options = {"Head", "Dynamic name box", "Static name box", "Level", "FKDR", "WLR", "Winstreak", "Final kills", "Wins", "Beds", "Guild", "Rank", "Ping", "HP"})
    public static String[] tabColumns = {"Head", "Dynamic name box", "Level", "FKDR", "WLR", "Winstreak", "HP"};

    @DraggableList(title = "HUD columns", description = "Drag to reorder; uncheck to hide", category = "HUD",
        subcategory = "Columns", checkable = true,
        options = {"Head", "Dynamic name box", "Static name box", "Level", "FKDR", "WLR", "Winstreak", "Final kills", "Wins", "Beds", "Guild", "Rank", "Ping"})
    public static String[] hudColumns = {"Head", "Dynamic name box", "Level", "FKDR", "WLR", "Winstreak"};

    @Button(title = "Move HUD and TAB tables", text = "Open HUD editor", category = "General")
    private static void openHudEditor() {
        HudManager.INSTANCE.openEditor();
    }

    public ChuStatsuConfig() {
        super("chustatsu.json", "/assets/chustatsu/icon.png", "ChuStatsu", Category.HUD);
    }

    static boolean migrateNameColumns() {
        boolean old = java.util.Arrays.asList(tabColumns).contains("Name")
            || java.util.Arrays.asList(hudColumns).contains("Name");
        if (!old) return false;
        tabColumns = java.util.Arrays.stream(tabColumns)
            .map(name -> name.equals("Name") ? "Dynamic name box" : name).toArray(String[]::new);
        hudColumns = java.util.Arrays.stream(hudColumns)
            .map(name -> name.equals("Name") ? "Dynamic name box" : name).toArray(String[]::new);
        if (columnSpacing == 0) columnSpacing = 6;
        return true;
    }
}
