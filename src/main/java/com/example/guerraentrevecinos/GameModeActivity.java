package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityGameModeBinding;

public class GameModeActivity extends AppCompatActivity {

    private ActivityGameModeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Solo Mode
        binding.btnSoloMode.setOnClickListener(v -> {
            animateButtonPress(v);
            v.postDelayed(() -> {
                // ✅ Go to power selection first
                Intent intent = new Intent(this, PowerSelectionActivity.class);
                intent.putExtra("GAME_MODE", "SOLO");
                startActivity(intent);
            }, 150);
        });

        // ✅ Multiplayer Mode (NOW ENABLED!)
        binding.btnMultiplayerMode.setEnabled(true);
        binding.btnMultiplayerMode.setAlpha(1f);
        binding.btnMultiplayerMode.setOnClickListener(v -> {
            animateButtonPress(v);
            v.postDelayed(() -> {
                Intent intent = new Intent(this, MultiplayerMenuActivity.class);
                startActivity(intent);
            }, 150);
        });
    }

    private void animateButtonPress(android.view.View button) {
        button.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    button.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }
}