package Entities;
import java.util.HashMap;

public class Player {

	public String username;
	private HashMap<String, GameStats> playerGames;
	
	public Player(String username) {
		this.username = username;
		this.playerGames = new HashMap<String, GameStats>();
	}

	public void registerFor(String sport) {
		if (!playerGames.containsKey(sport)) {
			playerGames.put(sport, new GameStats());
		}
	}
	
	public GameStats getPlayerGamesFor(String sport) {
		int wins, loss, winstreak;
		if (playerGames.containsKey(sport)) {
			wins = playerGames.get(sport).getWins();
			loss = playerGames.get(sport).getLoss();
			winstreak = playerGames.get(sport).getWinstreak();
			return new GameStats(wins, loss, winstreak);
		}
		return null;
	}

	public void updateGameStatsAfter_WinIn(String sport) {
		playerGames.get(sport).win_increment();
	}

	public void updateGameStatsAfter_LossIn(String sport) {
		playerGames.get(sport).loss_increment();
	}
}