package dev.chustatsu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class RankLabelTest {
    @Test
    public void unknownBoldServerRankIsShownBesideTheName() {
        assertEquals(" §7§6MYTHIC+", RankLabel.besideName("§6§l[MYTHIC+] §7liywy", "liywy", "VIP"));
    }

    @Test
    public void apiRankDoesNotOverrideAnUnlabeledTabName() {
        assertEquals("", RankLabel.besideName("§7movi6287", "movi6287", "[VIP]"));
    }

    @Test
    public void scoreboardTeamPrefixCanSupplyAnUndocumentedRank() {
        String formatted = "§d§l[BUILDER] §fSomeone";
        assertTrue(RankLabel.hasBoldPrefix(formatted, "Someone"));
        assertEquals(" §7§dBUILDER", RankLabel.besideName(formatted, "Someone", null));
    }
}
