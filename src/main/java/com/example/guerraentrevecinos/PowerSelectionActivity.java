package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.button.MaterialButton;

public class PowerSelectionActivity extends AppCompatActivity {

    private CardView cardSpyDrone, cardFenceShield, cardFertilizer;
    private ImageView iconSpyDroneSelected, iconFenceShieldSelected, iconFertilizerSelected;
    private MaterialButton btnContinue;

    private String selectedPower = null;
    private String gameMode;

    // ✅ Multiplayer fields
    private String roomCode;
    private boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_power_selection);

        // Get game mode
        gameMode = getIntent().getStringExtra("GAME_MODE");

        // ✅ Get multiplayer data
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);

        // Initialize views
        initializeViews();

        // Setup click listeners
        setupPowerCards();

        // Continue button
        btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra("GAME_MODE", gameMode);
            intent.putExtra("SELECTED_POWER", selectedPower);
            // ✅ Pass multiplayer data
            if (gameMode.equals("MULTIPLAYER")) {
                intent.putExtra("ROOM_CODE", roomCode);
                intent.putExtra("IS_HOST", isHost);
            }

            startActivity(intent);
            finish();
        });
    }

    private void initializeViews() {
        cardSpyDrone = findViewById(R.id.cardSpyDrone);
        cardFenceShield = findViewById(R.id.cardFenceShield);
        cardFertilizer = findViewById(R.id.cardFertilizer);

        iconSpyDroneSelected = findViewById(R.id.iconSpyDroneSelected);
        iconFenceShieldSelected = findViewById(R.id.iconFenceShieldSelected);
        iconFertilizerSelected = findViewById(R.id.iconFertilizerSelected);

        btnContinue = findViewById(R.id.btnContinue);
    }

    private void setupPowerCards() {
        cardSpyDrone.setOnClickListener(v -> selectPower("spy_drone"));
        cardFenceShield.setOnClickListener(v -> selectPower("fence_shield"));
        cardFertilizer.setOnClickListener(v -> selectPower("fertilizer"));
    }

    private void selectPower(String power) {
        selectedPower = power;

        // Hide all check marks
        iconSpyDroneSelected.setVisibility(View.GONE);
        iconFenceShieldSelected.setVisibility(View.GONE);
        iconFertilizerSelected.setVisibility(View.GONE);

        // Reset card elevations
        cardSpyDrone.setCardElevation(4f);
        cardFenceShield.setCardElevation(4f);
        cardFertilizer.setCardElevation(4f);

        // Show selected check mark and elevate card
        switch (power) {
            case "spy_drone":
                iconSpyDroneSelected.setVisibility(View.VISIBLE);
                cardSpyDrone.setCardElevation(12f);
                break;
            case "fence_shield":
                iconFenceShieldSelected.setVisibility(View.VISIBLE);
                cardFenceShield.setCardElevation(12f);
                break;
            case "fertilizer":
                iconFertilizerSelected.setVisibility(View.VISIBLE);
                cardFertilizer.setCardElevation(12f);
                break;
        }

        // Enable continue button
        btnContinue.setEnabled(true);
    }
}