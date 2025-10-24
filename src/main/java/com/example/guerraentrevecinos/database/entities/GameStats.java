package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "game_stats",
        foreignKeys = {
                @ForeignKey(
                        entity = Game.class,
                        parentColumns = "game_id",
                        childColumns = "game_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("game_id")}
)
public class GameStats {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "stat_id")
    private int statId;

    @ColumnInfo(name = "game_id")
    private int gameId;

    @ColumnInfo(name = "player_id")
    private int playerId;

    @ColumnInfo(name = "total_attacks")
    private int totalAttacks;

    @ColumnInfo(name = "successful_hits")
    private int successfulHits;

    @ColumnInfo(name = "units_destroyed")
    private int unitsDestroyed;

    @ColumnInfo(name = "powers_used")
    private int powersUsed;

    @ColumnInfo(name = "accuracy_percentage")
    private float accuracyPercentage;

    // Constructor
    public GameStats(int gameId, int playerId) {
        this.gameId = gameId;
        this.playerId = playerId;
        this.totalAttacks = 0;
        this.successfulHits = 0;
        this.unitsDestroyed = 0;
        this.powersUsed = 0;
        this.accuracyPercentage = 0f;
    }

    // Getters and Setters
    public int getStatId() { return statId; }
    public void setStatId(int statId) { this.statId = statId; }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public int getTotalAttacks() { return totalAttacks; }
    public void setTotalAttacks(int totalAttacks) { this.totalAttacks = totalAttacks; }

    public int getSuccessfulHits() { return successfulHits; }
    public void setSuccessfulHits(int successfulHits) { this.successfulHits = successfulHits; }

    public int getUnitsDestroyed() { return unitsDestroyed; }
    public void setUnitsDestroyed(int unitsDestroyed) { this.unitsDestroyed = unitsDestroyed; }

    public int getPowersUsed() { return powersUsed; }
    public void setPowersUsed(int powersUsed) { this.powersUsed = powersUsed; }

    public float getAccuracyPercentage() { return accuracyPercentage; }
    public void setAccuracyPercentage(float accuracyPercentage) { this.accuracyPercentage = accuracyPercentage; }
}