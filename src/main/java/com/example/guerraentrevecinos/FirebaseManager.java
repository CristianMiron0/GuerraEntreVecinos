package com.example.guerraentrevecinos;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.util.Random;

public class FirebaseManager {

    private static FirebaseManager instance;
    private FirebaseDatabase database;
    private DatabaseReference gamesRef;
    private FirebaseAuth auth;

    private FirebaseManager() {
        database = FirebaseDatabase.getInstance();
        gamesRef = database.getReference("games");
        auth = FirebaseAuth.getInstance();

        // Sign in anonymously
        signInAnonymously();
    }

    public static FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    private void signInAnonymously() {
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously();
        }
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // Generate random 6-character room code
    public String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    // Create a new game room
    public void createRoom(String roomCode, String playerName, OnRoomCreatedListener listener) {
        FirebaseGameRoom room = new FirebaseGameRoom(roomCode);
        room.player1 = new FirebaseGameRoom.PlayerData(getCurrentUserId(), playerName);

        gamesRef.child(roomCode).setValue(room.toMap())
                .addOnSuccessListener(aVoid -> listener.onSuccess(roomCode))
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }

    // Join an existing room
    public void joinRoom(String roomCode, String playerName, OnRoomJoinedListener listener) {
        gamesRef.child(roomCode).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                DatabaseReference roomRef = gamesRef.child(roomCode);

                FirebaseGameRoom.PlayerData player2 = new FirebaseGameRoom.PlayerData(
                        getCurrentUserId(), playerName
                );

                roomRef.child("player2").setValue(player2.toMap())
                        .addOnSuccessListener(aVoid -> {
                            roomRef.child("status").setValue("setup");
                            listener.onSuccess(roomCode);
                        })
                        .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
            } else {
                listener.onFailure("Room not found");
            }
        });
    }

    // Listen for room updates
    public void listenToRoom(String roomCode, ValueEventListener listener) {
        gamesRef.child(roomCode).addValueEventListener(listener);
    }

    // Remove room listener
    public void removeRoomListener(String roomCode, ValueEventListener listener) {
        gamesRef.child(roomCode).removeEventListener(listener);
    }

    // Update player ready status
    public void setPlayerReady(String roomCode, boolean isPlayer1, boolean ready) {
        String path = isPlayer1 ? "player1/ready" : "player2/ready";
        gamesRef.child(roomCode).child(path).setValue(ready);
    }

    // Update game state
    public void updateGameState(String roomCode, FirebaseGameRoom.GameStateData gameState) {
        gamesRef.child(roomCode).child("gameState").setValue(gameState.toMap());
    }

    // Send action (attack, power use)
    public void sendAction(String roomCode, FirebaseGameRoom.LastActionData action) {
        gamesRef.child(roomCode).child("lastAction").setValue(action.toMap());
    }

    // Delete room
    public void deleteRoom(String roomCode) {
        gamesRef.child(roomCode).removeValue();
    }

    // Interfaces
    public interface OnRoomCreatedListener {
        void onSuccess(String roomCode);
        void onFailure(String error);
    }

    public interface OnRoomJoinedListener {
        void onSuccess(String roomCode);
        void onFailure(String error);
    }
}