package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "power_usages",
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
public class PowerUsage {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "power_usage_id")
    private int powerUsageId;

    @ColumnInfo(name = "game_id")
    private int gameId;

    @ColumnInfo(name = "player_id")
    private int playerId;

    @ColumnInfo(name = "power_name")
    private String powerName; // "garden_hose", "nighttime_relocation", etc.

    @ColumnInfo(name = "used_at_round")
    private int usedAtRound;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // Constructor
    public PowerUsage(int gameId, int playerId, String powerName, int usedAtRound) {
        this.gameId = gameId;
        this.playerId = playerId;
        this.powerName = powerName;
        this.usedAtRound = usedAtRound;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getPowerUsageId() { return powerUsageId; }
    public void setPowerUsageId(int powerUsageId) { this.powerUsageId = powerUsageId; }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }

    public String getPowerName() { return powerName; }
    public void setPowerName(String powerName) { this.powerName = powerName; }

    public int getUsedAtRound() { return usedAtRound; }
    public void setUsedAtRound(int usedAtRound) { this.usedAtRound = usedAtRound; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}