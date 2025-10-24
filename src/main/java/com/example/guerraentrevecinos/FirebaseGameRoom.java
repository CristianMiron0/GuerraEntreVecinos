package com.example.guerraentrevecinos;

import java.util.HashMap;
import java.util.Map;

public class FirebaseGameRoom {

    public String roomCode;
    public String status; // "waiting", "setup", "playing", "finished"
    public long createdAt;

    public PlayerData player1;
    public PlayerData player2;

    public GameStateData gameState;
    public LastActionData lastAction;

    public FirebaseGameRoom() {
        // Required empty constructor for Firebase
    }

    public FirebaseGameRoom(String roomCode) {
        this.roomCode = roomCode;
        this.status = "waiting";
        this.createdAt = System.currentTimeMillis();
        this.gameState = new GameStateData();
    }

    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("roomCode", roomCode);
        result.put("status", status);
        result.put("createdAt", createdAt);
        result.put("player1", player1 != null ? player1.toMap() : null);
        result.put("player2", player2 != null ? player2.toMap() : null);
        result.put("gameState", gameState != null ? gameState.toMap() : null);
        result.put("lastAction", lastAction != null ? lastAction.toMap() : null);
        return result;
    }

    public static class PlayerData {
        public String userId;
        public String displayName;
        public boolean ready;
        public String selectedPower;

        public PlayerData() {}

        public PlayerData(String userId, String displayName) {
            this.userId = userId;
            this.displayName = displayName;
            this.ready = false;
        }

        public Map<String, Object> toMap() {
            HashMap<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("displayName", displayName);
            result.put("ready", ready);
            result.put("selectedPower", selectedPower);
            return result;
        }
    }

    public static class GameStateData {
        public int currentRound = 1;
        public String currentTurn; // "player1" or "player2"
        public int player1UnitsRemaining = 7;
        public int player2UnitsRemaining = 7;

        public GameStateData() {}

        public Map<String, Object> toMap() {
            HashMap<String, Object> result = new HashMap<>();
            result.put("currentRound", currentRound);
            result.put("currentTurn", currentTurn);
            result.put("player1UnitsRemaining", player1UnitsRemaining);
            result.put("player2UnitsRemaining", player2UnitsRemaining);
            return result;
        }
    }

    public static class LastActionData {
        public String type; // "attack", "power_use"
        public String player; // "player1" or "player2"
        public int targetRow;
        public int targetCol;
        public boolean wasHit;
        public boolean duelPending;
        public long timestamp;

        public LastActionData() {}

        public Map<String, Object> toMap() {
            HashMap<String, Object> result = new HashMap<>();
            result.put("type", type);
            result.put("player", player);
            result.put("targetRow", targetRow);
            result.put("targetCol", targetCol);
            result.put("wasHit", wasHit);
            result.put("duelPending", duelPending);
            result.put("timestamp", timestamp);
            return result;
        }
    }
}