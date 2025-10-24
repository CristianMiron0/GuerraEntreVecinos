package com.example.guerraentrevecinos;

public class GameHistoryItem {
    private int gameId;
    private boolean isWin;
    private float accuracy;
    private int unitsDestroyed;
    private int rounds;
    private String date;

    public GameHistoryItem(int gameId, boolean isWin, float accuracy,
                           int unitsDestroyed, int rounds, String date) {
        this.gameId = gameId;
        this.isWin = isWin;
        this.accuracy = accuracy;
        this.unitsDestroyed = unitsDestroyed;
        this.rounds = rounds;
        this.date = date;
    }

    // Getters
    public int getGameId() { return gameId; }
    public boolean isWin() { return isWin; }
    public float getAccuracy() { return accuracy; }
    public int getUnitsDestroyed() { return unitsDestroyed; }
    public int getRounds() { return rounds; }
    public String getDate() { return date; }
}