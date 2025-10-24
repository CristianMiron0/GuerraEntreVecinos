package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityMultiplayerMenuBinding;
import com.example.guerraentrevecinos.FirebaseManager;

public class MultiplayerMenuActivity extends AppCompatActivity {

    private ActivityMultiplayerMenuBinding binding;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMultiplayerMenuBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseManager = FirebaseManager.getInstance();

        // Create Room
        binding.btnCreateRoom.setOnClickListener(v -> createRoom());

        // Join Room
        binding.btnJoinRoom.setOnClickListener(v -> joinRoom());

        // Back
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void createRoom() {
        binding.btnCreateRoom.setEnabled(false);
        binding.btnCreateRoom.setText("Creating...");

        String roomCode = firebaseManager.generateRoomCode();

        firebaseManager.createRoom(roomCode, "Player 1",
                new FirebaseManager.OnRoomCreatedListener() {
                    @Override
                    public void onSuccess(String code) {
                        // Go to waiting room
                        Intent intent = new Intent(MultiplayerMenuActivity.this, WaitingRoomActivity.class);
                        intent.putExtra("ROOM_CODE", code);
                        intent.putExtra("IS_HOST", true);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(MultiplayerMenuActivity.this,
                                "Error: " + error, Toast.LENGTH_SHORT).show();
                        binding.btnCreateRoom.setEnabled(true);
                        binding.btnCreateRoom.setText("Create Room");
                    }
                });
    }

    private void joinRoom() {
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
                        // Go to waiting room
                        Intent intent = new Intent(MultiplayerMenuActivity.this, WaitingRoomActivity.class);
                        intent.putExtra("ROOM_CODE", code);
                        intent.putExtra("IS_HOST", false);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(MultiplayerMenuActivity.this,
                                "Error: " + error, Toast.LENGTH_SHORT).show();
                        binding.btnJoinRoom.setEnabled(true);
                        binding.btnJoinRoom.setText("Join Room");
                    }
                });
    }
}