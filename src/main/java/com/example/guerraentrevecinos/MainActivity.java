package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Start Game button
        binding.btnStartGame.setOnClickListener(v -> {
            animateButtonPress(v);

            v.postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, GameModeActivity.class);
                startActivity(intent);
            }, 150);
        });

        // ✅ Statistics button
        binding.btnStatistics.setOnClickListener(v -> {
            animateButtonPress(v);

            v.postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, StatisticsActivity.class);
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