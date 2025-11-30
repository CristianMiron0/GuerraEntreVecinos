package com.example.guerraentrevecinos;

import android.util.Log;
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

    private static final String TAG = "FirebaseManager";
    private static FirebaseManager instance;
    private FirebaseDatabase database;
    private DatabaseReference gamesRef;
    private FirebaseAuth auth;
    private boolean isInitialized = false;

    private FirebaseManager() {
        try {
            Log.d(TAG, "Initializing Firebase...");

            // Initialize Firebase Auth first
            auth = FirebaseAuth.getInstance();

            // Initialize Firebase Database with your URL
            database = FirebaseDatabase.getInstance("https://guerraentrevecinos-default-rtdb.europe-west1.firebasedatabase.app/");

            // Enable offline persistence (but only once)
            try {
                database.setPersistenceEnabled(true);
            } catch (Exception e) {
                Log.w(TAG, "Persistence already enabled or error: " + e.getMessage());
            }

            gamesRef = database.getReference("games");

            isInitialized = true;
            Log.d(TAG, "Firebase initialized successfully");

            // ✅ FIX: Sign in immediately!
            signInAnonymously();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
            isInitialized = false;
        }
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    private void signInAnonymously() {
        if (auth == null) {
            Log.e(TAG, "FirebaseAuth is null!");
            return;
        }

        if (auth.getCurrentUser() == null) {
            Log.d(TAG, "Signing in anonymously...");
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Anonymous sign in successful: " + authResult.getUser().getUid());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Anonymous sign in failed", e);
                    });
        } else {
            Log.d(TAG, "Already signed in: " + auth.getCurrentUser().getUid());
        }
    }

    public String getCurrentUserId() {
        if (auth == null) {
            Log.e(TAG, "FirebaseAuth is null when getting user ID!");
            return "offline_user_" + System.currentTimeMillis();
        }
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    public void createRoom(String roomCode, String playerName, OnRoomCreatedListener listener) {
        if (!isInitialized) {
            Log.e(TAG, "Firebase not initialized!");
            listener.onFailure("Firebase not initialized. Please restart the app.");
            return;
        }

        Log.d(TAG, "Creating room with code: " + roomCode);

        // FIX: Always sign in first, then create room
        if (auth.getCurrentUser() == null) {
            Log.d(TAG, "Not authenticated, signing in...");
            auth.signInAnonymously()
                    .addOnSuccessListener(authResult -> {
                        Log.d(TAG, "Auth success, creating room...");
                        createRoomInternal(roomCode, playerName, listener);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Auth failed during room creation", e);
                        listener.onFailure("Authentication failed: " + e.getMessage());
                    });
        } else {
            Log.d(TAG, "Already authenticated: " + auth.getCurrentUser().getUid());
            createRoomInternal(roomCode, playerName, listener);
        }
    }

    private void createRoomInternal(String roomCode, String playerName, OnRoomCreatedListener listener) {
        try {
            FirebaseGameRoom room = new FirebaseGameRoom(roomCode);
            room.player1 = new FirebaseGameRoom.PlayerData(getCurrentUserId(), playerName);

            Log.d(TAG, "Attempting to write room to Firebase: " + roomCode);

            gamesRef.child(roomCode).setValue(room.toMap())
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Room created successfully: " + roomCode);
                        listener.onSuccess(roomCode);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to create room", e);
                        listener.onFailure("Failed to create room: " + e.getMessage());
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in createRoomInternal", e);
            listener.onFailure("Error: " + e.getMessage());
        }
    }

    public void joinRoom(String roomCode, String playerName, OnRoomJoinedListener listener) {
        if (!isInitialized) {
            Log.e(TAG, "Firebase not initialized!");
            listener.onFailure("Firebase not initialized. Please restart the app.");
            return;
        }

        Log.d(TAG, "Attempting to join room: " + roomCode);

        try {
            gamesRef.child(roomCode).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    DatabaseReference roomRef = gamesRef.child(roomCode);

                    FirebaseGameRoom.PlayerData player2 = new FirebaseGameRoom.PlayerData(
                            getCurrentUserId(), playerName
                    );

                    roomRef.child("player2").setValue(player2.toMap())
                            .addOnSuccessListener(aVoid -> {
                                roomRef.child("status").setValue("setup");
                                Log.d(TAG, "Joined room successfully: " + roomCode);
                                listener.onSuccess(roomCode);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to join room", e);
                                listener.onFailure("Failed to join: " + e.getMessage());
                            });
                } else {
                    Log.e(TAG, "Room not found: " + roomCode);
                    listener.onFailure("Room not found");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Exception in joinRoom", e);
            listener.onFailure("Error: " + e.getMessage());
        }
    }

    public void listenToRoom(String roomCode, ValueEventListener listener) {
        if (!isInitialized || gamesRef == null) {
            Log.e(TAG, "Cannot listen to room - Firebase not initialized!");
            return;
        }
        gamesRef.child(roomCode).addValueEventListener(listener);
    }

    public void removeRoomListener(String roomCode, ValueEventListener listener) {
        if (gamesRef != null) {
            gamesRef.child(roomCode).removeEventListener(listener);
        }
    }

    public void setPlayerReady(String roomCode, boolean isPlayer1, boolean ready) {
        if (!isInitialized || gamesRef == null) {
            Log.e(TAG, "Cannot set player ready - Firebase not initialized!");
            return;
        }
        String path = isPlayer1 ? "player1/ready" : "player2/ready";
        gamesRef.child(roomCode).child(path).setValue(ready);
    }

    public void updateGameState(String roomCode, FirebaseGameRoom.GameStateData gameState) {
        if (!isInitialized || gamesRef == null) {
            Log.e(TAG, "Cannot update game state - Firebase not initialized!");
            return;
        }
        gamesRef.child(roomCode).child("gameState").setValue(gameState.toMap());
    }

    public void sendAction(String roomCode, FirebaseGameRoom.LastActionData action) {
        if (!isInitialized || gamesRef == null) {
            Log.e(TAG, "Cannot send action - Firebase not initialized!");
            return;
        }
        gamesRef.child(roomCode).child("lastAction").setValue(action.toMap());
    }

    public void deleteRoom(String roomCode) {
        if (gamesRef != null) {
            gamesRef.child(roomCode).removeValue();
        }
    }

    public interface OnRoomCreatedListener {
        void onSuccess(String roomCode);
        void onFailure(String error);
    }

    public interface OnRoomJoinedListener {
        void onSuccess(String roomCode);
        void onFailure(String error);
    }
}