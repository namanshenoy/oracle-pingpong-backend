import Entities.Player;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class PlayerTest {

	Player testPlayer = new Player("jane_doe");
	
	@Test
	void testAddGame() {

		// Should be empty
		assertTrue(testPlayer.getMap().isEmpty(), "Error: HashMap is not empty");

		testPlayer.addGame("basketball");
		
		// Should print "basketball"
		assertFalse(testPlayer.getMap().isEmpty(), "Error: HashMap is empty");
	
	}
}
