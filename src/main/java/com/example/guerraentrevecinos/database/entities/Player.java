package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "players")
public class Player {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "player_id")
    private int playerId;

    @ColumnInfo(name = "player_name")
    private String playerName;

    @ColumnInfo(name = "is_ai")
    private boolean isAi;

    @ColumnInfo(name = "total_games")
    private int totalGames;

    @ColumnInfo(name = "total_wins")
    private int totalWins;

    @ColumnInfo(name = "total_losses")
    private int totalLosses;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    // Constructor
    public Player(String playerName, boolean isAi) {
        this.playerName = playerName;
        this.isAi = isAi;
        this.totalGames = 0;
        this.totalWins = 0;
        this.totalLosses = 0;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public boolean isAi() { return isAi; }
    public void setAi(boolean ai) { isAi = ai; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getTotalWins() { return totalWins; }
    public void setTotalWins(int totalWins) { this.totalWins = totalWins; }

    public int getTotalLosses() { return totalLosses; }
    public void setTotalLosses(int totalLosses) { this.totalLosses = totalLosses; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}