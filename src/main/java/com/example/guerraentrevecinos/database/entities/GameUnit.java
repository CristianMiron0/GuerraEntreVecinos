package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "game_units",
        foreignKeys = {
                @ForeignKey(
                        entity = Game.class,
                        parentColumns = "game_id",
                        childColumns = "game_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = Unit.class,
                        parentColumns = "unit_id",
                        childColumns = "unit_type_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("game_id"),
                @Index("unit_type_id")
        }
)
public class GameUnit {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "game_unit_id")
    private int gameUnitId;

    @ColumnInfo(name = "game_id")
    private int gameId;

    @ColumnInfo(name = "unit_type_id")
    private int unitTypeId;

    @ColumnInfo(name = "owner_player_id")
    private int ownerPlayerId;

    @ColumnInfo(name = "grid_row")
    private int gridRow;

    @ColumnInfo(name = "grid_col")
    private int gridCol;

    @ColumnInfo(name = "current_health")
    private int currentHealth;

    @ColumnInfo(name = "is_destroyed")
    private boolean isDestroyed;

    // Constructor
    public GameUnit(int gameId, int unitTypeId, int ownerPlayerId,
                    int gridRow, int gridCol, int currentHealth) {
        this.gameId = gameId;
        this.unitTypeId = unitTypeId;
        this.ownerPlayerId = ownerPlayerId;
        this.gridRow = gridRow;
        this.gridCol = gridCol;
        this.currentHealth = currentHealth;
        this.isDestroyed = false;
    }

    // Getters and Setters
    public int getGameUnitId() { return gameUnitId; }
    public void setGameUnitId(int gameUnitId) { this.gameUnitId = gameUnitId; }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public int getUnitTypeId() { return unitTypeId; }
    public void setUnitTypeId(int unitTypeId) { this.unitTypeId = unitTypeId; }

    public int getOwnerPlayerId() { return ownerPlayerId; }
    public void setOwnerPlayerId(int ownerPlayerId) { this.ownerPlayerId = ownerPlayerId; }

    public int getGridRow() { return gridRow; }
    public void setGridRow(int gridRow) { this.gridRow = gridRow; }

    public int getGridCol() { return gridCol; }
    public void setGridCol(int gridCol) { this.gridCol = gridCol; }

    public int getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(int currentHealth) { this.currentHealth = currentHealth; }

    public boolean isDestroyed() { return isDestroyed; }
    public void setDestroyed(boolean destroyed) { isDestroyed = destroyed; }
}