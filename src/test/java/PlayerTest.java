import Entities.Player;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class PlayerTest {

	Player testPlayer = new Player("janedoe");
	final String sport = "table-tennis";

	@Test
	void testRegisterForGame() {
		testPlayer.registerFor(sport);

		assertNotNull(testPlayer.getPlayerGamesFor(sport), 
			String.format("Error: %s not added.", sport) );
	}

	@Test
	void testGetPlayerGamesFor_unknownGame() {
		assertNull(testPlayer.getPlayerGamesFor("volleyball"));
	} 

	@Test
	void testUpdateGameStatsAfter_Win() {
		testPlayer.registerFor(sport);
		testPlayer.updateGameStatsAfter_WinIn(sport);
		assertEquals(testPlayer.getPlayerGamesFor(sport).getWins(), 1);
		assertEquals(testPlayer.getPlayerGamesFor(sport).getWinstreak(), 1);
	}

	@Test
	void testUpdateGameStatsAfter_Loss() {
		testPlayer.registerFor(sport);
		testPlayer.updateGameStatsAfter_LossIn(sport);
		assertEquals(testPlayer.getPlayerGamesFor(sport).getLoss(), 1);
		assertEquals(testPlayer.getPlayerGamesFor(sport).getWinstreak(), 0);
	}
}
