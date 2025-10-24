package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "moves",
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
public class Move {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "move_id")
    private int moveId;

    @ColumnInfo(name = "game_id")
    private int gameId;

    @ColumnInfo(name = "round_number")
    private int roundNumber;

    @ColumnInfo(name = "attacking_player_id")
    private int attackingPlayerId;

    @ColumnInfo(name = "target_row")
    private int targetRow;

    @ColumnInfo(name = "target_col")
    private int targetCol;

    @ColumnInfo(name = "was_hit")
    private boolean wasHit;

    @ColumnInfo(name = "attacker_choice")
    private int attackerChoice; // 1-4

    @ColumnInfo(name = "defender_choice")
    private int defenderChoice; // 1-4

    @ColumnInfo(name = "duel_result")
    private String duelResult; // "destroyed", "damaged", "defended"

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    // Constructor
    public Move(int gameId, int roundNumber, int attackingPlayerId,
                int targetRow, int targetCol, boolean wasHit,
                int attackerChoice, int defenderChoice, String duelResult) {
        this.gameId = gameId;
        this.roundNumber = roundNumber;
        this.attackingPlayerId = attackingPlayerId;
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        this.wasHit = wasHit;
        this.attackerChoice = attackerChoice;
        this.defenderChoice = defenderChoice;
        this.duelResult = duelResult;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getMoveId() { return moveId; }
    public void setMoveId(int moveId) { this.moveId = moveId; }

    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public int getAttackingPlayerId() { return attackingPlayerId; }
    public void setAttackingPlayerId(int attackingPlayerId) { this.attackingPlayerId = attackingPlayerId; }

    public int getTargetRow() { return targetRow; }
    public void setTargetRow(int targetRow) { this.targetRow = targetRow; }

    public int getTargetCol() { return targetCol; }
    public void setTargetCol(int targetCol) { this.targetCol = targetCol; }

    public boolean isWasHit() { return wasHit; }
    public void setWasHit(boolean wasHit) { this.wasHit = wasHit; }

    public int getAttackerChoice() { return attackerChoice; }
    public void setAttackerChoice(int attackerChoice) { this.attackerChoice = attackerChoice; }

    public int getDefenderChoice() { return defenderChoice; }
    public void setDefenderChoice(int defenderChoice) { this.defenderChoice = defenderChoice; }

    public String getDuelResult() { return duelResult; }
    public void setDuelResult(String duelResult) { this.duelResult = duelResult; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}