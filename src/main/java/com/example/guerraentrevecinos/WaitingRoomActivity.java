package com.example.guerraentrevecinos;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityWaitingRoomBinding;
import com.example.guerraentrevecinos.FirebaseManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

public class WaitingRoomActivity extends AppCompatActivity {

    private ActivityWaitingRoomBinding binding;
    private FirebaseManager firebaseManager;
    private String roomCode;
    private boolean isHost;
    private ValueEventListener roomListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWaitingRoomBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseManager = FirebaseManager.getInstance();
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);

        binding.tvRoomCode.setText(roomCode);

        // Copy code button
        binding.btnCopyCode.setOnClickListener(v -> copyRoomCode());

        // Cancel button
        binding.btnCancel.setOnClickListener(v -> cancelAndLeave());

        // Listen for opponent joining
        listenForOpponent();
    }

    private void copyRoomCode() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Room Code", roomCode);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Room code copied!", Toast.LENGTH_SHORT).show();
    }

    private void listenForOpponent() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Room was deleted
                    Toast.makeText(WaitingRoomActivity.this,
                            "Room closed", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Check if both players present
                boolean player1Present = snapshot.child("player1").exists();
                boolean player2Present = snapshot.child("player2").exists();

                if (player1Present && player2Present) {
                    // Both players connected!
                    binding.tvStatus.setText("Opponent joined! Starting game...");
                    binding.tvOpponentStatus.setText("✅ Opponent: Ready");
                    binding.tvOpponentStatus.setTextColor(getColor(android.R.color.holo_green_dark));

                    // Wait a moment then go to power selection
                    binding.btnCancel.postDelayed(() -> {
                        Intent intent = new Intent(WaitingRoomActivity.this, PowerSelectionActivity.class);
                        intent.putExtra("GAME_MODE", "MULTIPLAYER");
                        intent.putExtra("ROOM_CODE", roomCode);
                        intent.putExtra("IS_HOST", isHost);
                        startActivity(intent);
                        finish();
                    }, 1500);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(WaitingRoomActivity.this,
                        "Connection error: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        firebaseManager.listenToRoom(roomCode, roomListener);
    }

    private void cancelAndLeave() {
        // Remove listener
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }

        // If host, delete room
        if (isHost) {
            firebaseManager.deleteRoom(roomCode);
        }

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }
    }
}