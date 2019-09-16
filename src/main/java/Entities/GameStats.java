package Entities;

public class GameStats {
	private int wins;
	private int loss;
	private int winstreak;
	
	public GameStats(){
		this.wins = 0;
		this.loss = 0;
		this.winstreak = 0;
	}

	public GameStats(int wins, int loss, int winstreak) {
		this.wins = wins;
		this.loss = loss;
		this.winstreak = winstreak;
	}

	private void reset_streak() {
		this.winstreak = 0;
	}
	
	public void win_increment() {
		this.wins++;
		this.winstreak++;
	}
	
	public void loss_increment() {
		this.loss++;
		this.reset_streak();
	}

	public int getWins() {
		return this.wins;
	}

	public int getLoss(){
		return this.loss;
	}

	public int getWinstreak() {
		return this.winstreak;
	}
}