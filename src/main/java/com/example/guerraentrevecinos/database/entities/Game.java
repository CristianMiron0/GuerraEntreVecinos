package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "games",
        foreignKeys = {
                @ForeignKey(
                        entity = Player.class,
                        parentColumns = "player_id",
                        childColumns = "player1_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Player.class,
                        parentColumns = "player_id",
                        childColumns = "player2_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("player1_id"),
                @Index("player2_id")
        }
)
public class Game {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "game_id")
    private int gameId;

    @ColumnInfo(name = "player1_id")
    private int player1Id;

    @ColumnInfo(name = "player2_id")
    private int player2Id;

    @ColumnInfo(name = "winner_id")
    private Integer winnerId;

    @ColumnInfo(name = "game_mode")
    private String gameMode; // "solo_vs_ai" or "multiplayer"

    @ColumnInfo(name = "game_status")
    private String gameStatus; // "in_progress" or "finished"

    @ColumnInfo(name = "current_round")
    private int currentRound;

    @ColumnInfo(name = "max_rounds")
    private int maxRounds;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "finished_at")
    private Long finishedAt;

    // Constructor
    public Game(int player1Id, int player2Id, String gameMode) {
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.gameMode = gameMode;
        this.gameStatus = "in_progress";
        this.currentRound = 1;
        this.maxRounds = 30;
        this.createdAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public int getPlayer1Id() { return player1Id; }
    public void setPlayer1Id(int player1Id) { this.player1Id = player1Id; }

    public int getPlayer2Id() { return player2Id; }
    public void setPlayer2Id(int player2Id) { this.player2Id = player2Id; }

    public Integer getWinnerId() { return winnerId; }
    public void setWinnerId(Integer winnerId) { this.winnerId = winnerId; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public String getGameStatus() { return gameStatus; }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }

    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Long finishedAt) { this.finishedAt = finishedAt; }
}