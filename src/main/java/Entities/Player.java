package Entities;
import java.util.HashMap;

class GameStats {
	int wins;
	int loss;
	int winstreak;
	
	GameStats(){
		this.wins = 0;
		this.loss = 0;
		this.winstreak = 0;
	}
	
	private void win_increment() {
		this.wins++;
		this.winstreak++;
	}
	
	private void lose_increment() {
		this.loss++;
		this.winstreak = 0;
	}
}

public class Player {

	private String username;
	private HashMap<String, GameStats> playerGames;
	
	/*
	 * 	"soccer": GameStats (1, 20, 1)
	 * 
	 * playerGames["soccer"].win_increment();
	 * 
	 * "soccer": GameStats (2, 20, 1)
	 * "pingpong": GameStats (0, 0, 0)
	 * 
	 * */
	
	public Player(String username) {
		this.username = username;
		this.playerGames = new HashMap<String, GameStats>();
	}
	
	/* When a player starts playing a sport they haven't played before,
	 * this adds it to their "playerGames" hash table
	 */
	public void addGame(String sport) {
		if (!playerGames.containsKey(sport)) {
			playerGames.put(sport, new GameStats());
		}
	}
	
	/* This function should be used solely in PlayerTest.java
	 * (the corresponding test suite for this class)
	 */ 
	public HashMap<String, GameStats> getMap() {
		return playerGames;
	}

}