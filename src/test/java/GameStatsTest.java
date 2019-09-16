import Entities.GameStats;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class GameStatsTest {
    GameStats gameStat = new GameStats(2, 2, 2);

    @Test
    void testGameStats_resetWinstreakAfterLoss() {
        gameStat.loss_increment();
        assertEquals(gameStat.getWinstreak(), 0);
    }

    @Test
    void testGameStats_incrementLossesAfterLoss() {
        gameStat.loss_increment();
        assertEquals(gameStat.getLoss(), 3);
    }

    @Test
    void testGameStats_incrementWinAfterWin() {
        gameStat.win_increment();
        assertEquals(gameStat.getWins(), 3);
    }

    @Test
    void testGameStats_incrementWinstreakAfterWin() {
        gameStat.win_increment();
        assertEquals(gameStat.getWinstreak(), 3);
    }

    @Test
    void testGameStats_getWins() {
        assertEquals(gameStat.getWins(), 2);
    }

    @Test
    void testGameStats_getLoss() {
        assertEquals(gameStat.getLoss(), 2);
    }

    @Test
    void testGameStats_getWinstreak() {
        assertEquals(gameStat.getWinstreak(), 2);
    }

}