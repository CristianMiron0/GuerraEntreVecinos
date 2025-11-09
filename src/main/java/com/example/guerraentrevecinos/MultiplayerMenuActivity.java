package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityMultiplayerMenuBinding;

public class MultiplayerMenuActivity extends AppCompatActivity {

    private static final String TAG = "MultiplayerMenuActivity";
    private ActivityMultiplayerMenuBinding binding;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            binding = ActivityMultiplayerMenuBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            Log.d(TAG, "Activity created, initializing Firebase...");

            // Initialize Firebase Manager
            firebaseManager = FirebaseManager.getInstance();

            // Check if Firebase is initialized
            if (!firebaseManager.isInitialized()) {
                Toast.makeText(this, "Firebase initialization failed. Check your connection.",
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Firebase not initialized!");
            } else {
                Log.d(TAG, "Firebase initialized successfully");
            }

            // Create Room
            binding.btnCreateRoom.setOnClickListener(v -> createRoom());

            // Join Room
            binding.btnJoinRoom.setOnClickListener(v -> joinRoom());

            // Back
            binding.btnBack.setOnClickListener(v -> finish());

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createRoom() {
        try {
            binding.btnCreateRoom.setEnabled(false);
            binding.btnCreateRoom.setText("Creating...");

            String roomCode = firebaseManager.generateRoomCode();
            Log.d(TAG, "Generated room code: " + roomCode);

            firebaseManager.createRoom(roomCode, "Player 1",
                    new FirebaseManager.OnRoomCreatedListener() {
                        @Override
                        public void onSuccess(String code) {
                            Log.d(TAG, "Room created successfully: " + code);
                            runOnUiThread(() -> {
                                Intent intent = new Intent(MultiplayerMenuActivity.this, WaitingRoomActivity.class);
                                intent.putExtra("ROOM_CODE", code);
                                intent.putExtra("IS_HOST", true);
                                startActivity(intent);
                                finish();
                            });
                        }

                        @Override
                        public void onFailure(String error) {
                            Log.e(TAG, "Room creation failed: " + error);
                            runOnUiThread(() -> {
                                Toast.makeText(MultiplayerMenuActivity.this,
                                        "Error: " + error, Toast.LENGTH_LONG).show();
                                binding.btnCreateRoom.setEnabled(true);
                                binding.btnCreateRoom.setText("Create Room");
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in createRoom", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            binding.btnCreateRoom.setEnabled(true);
            binding.btnCreateRoom.setText("Create Room");
        }
    }

    private void joinRoom() {
        try {
            String roomCode = binding.etRoomCode.getText().toString().trim().toUpperCase();

            if (roomCode.length() != 6) {
                Toast.makeText(this, "Room code must be 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            binding.btnJoinRoom.setEnabled(false);
            binding.btnJoinRoom.setText("Joining...");

            firebaseManager.joinRoom(roomCode, "Player 2",
                    new FirebaseManager.OnRoomJoinedListener() {
                        @Override
                        public void onSuccess(String code) {
                            runOnUiThread(() -> {
                                Intent intent = new Intent(MultiplayerMenuActivity.this, WaitingRoomActivity.class);
                                intent.putExtra("ROOM_CODE", code);
                                intent.putExtra("IS_HOST", false);
                                startActivity(intent);
                                finish();
                            });
                        }

                        @Override
                        public void onFailure(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(MultiplayerMenuActivity.this,
                                        "Error: " + error, Toast.LENGTH_SHORT).show();
                                binding.btnJoinRoom.setEnabled(true);
                                binding.btnJoinRoom.setText("Join Room");
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in joinRoom", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            binding.btnJoinRoom.setEnabled(true);
            binding.btnJoinRoom.setText("Join Room");
        }
    }
}