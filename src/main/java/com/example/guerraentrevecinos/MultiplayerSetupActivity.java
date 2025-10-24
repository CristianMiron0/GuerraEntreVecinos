package com.example.guerraentrevecinos;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.FirebaseManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class MultiplayerSetupActivity extends AppCompatActivity {

    // UI Components
    private GridLayout gameGrid;
    private View unitSelectionMenu;
    private ImageButton btnSelectSunflower, btnSelectRose, btnSelectDog, btnSelectCat;
    private TextView tvSunflowerCount, tvRoseCount, tvDogCount, tvCatCount;
    private TextView tvStatusText;

    // Game State
    private String selectedUnitType = null;
    private int sunflowerCount = 3;
    private int roseCount = 2;
    private int dogCount = 1;
    private int catCount = 1;
    private int totalUnitsPlaced = 0;
    private static final int TOTAL_UNITS = 7;

    // Grid cells and unit positions
    private ImageView[][] gridCells = new ImageView[8][8];
    private List<SetupActivity.UnitPosition> playerUnits = new ArrayList<>();

    // Multiplayer
    private FirebaseManager firebaseManager;
    private String roomCode;
    private boolean isHost;
    private String selectedPower;
    private ValueEventListener roomListener;
    private boolean opponentReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        firebaseManager = FirebaseManager.getInstance();
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        selectedPower = getIntent().getStringExtra("SELECTED_POWER");

        // Initialize views
        initializeViews();

        // Add status text
        addStatusText();

        // Setup unit selection buttons
        setupUnitSelectionButtons();

        // Create the 8x8 grid
        createGameGrid();

        // Listen for opponent ready status
        listenForOpponent();
    }

    private void addStatusText() {
        // Add a status TextView programmatically
        tvStatusText = new TextView(this);
        tvStatusText.setText("Place your units. Waiting for opponent...");
        tvStatusText.setTextSize(16);
        tvStatusText.setTextColor(Color.WHITE);
        tvStatusText.setPadding(16, 8, 16, 8);
        tvStatusText.setBackgroundColor(Color.parseColor("#FF9800"));

        // You can add this to your layout or keep it in a toast
    }

    private void initializeViews() {
        gameGrid = findViewById(R.id.gameGrid);
        unitSelectionMenu = findViewById(R.id.unitSelectionMenu);

        btnSelectSunflower = findViewById(R.id.btnSelectSunflower);
        btnSelectRose = findViewById(R.id.btnSelectRose);
        btnSelectDog = findViewById(R.id.btnSelectDog);
        btnSelectCat = findViewById(R.id.btnSelectCat);

        tvSunflowerCount = findViewById(R.id.tvSunflowerCount);
        tvRoseCount = findViewById(R.id.tvRoseCount);
        tvDogCount = findViewById(R.id.tvDogCount);
        tvCatCount = findViewById(R.id.tvCatCount);
    }

    private void setupUnitSelectionButtons() {
        btnSelectSunflower.setOnClickListener(v -> selectUnit("sunflower", btnSelectSunflower, sunflowerCount));
        btnSelectRose.setOnClickListener(v -> selectUnit("rose", btnSelectRose, roseCount));
        btnSelectDog.setOnClickListener(v -> selectUnit("dog", btnSelectDog, dogCount));
        btnSelectCat.setOnClickListener(v -> selectUnit("cat", btnSelectCat, catCount));
    }

    private void selectUnit(String unitType, ImageButton button, int count) {
        if (count <= 0) {
            Toast.makeText(this, "No more " + unitType + "s available!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSelectSunflower.setSelected(false);
        btnSelectRose.setSelected(false);
        btnSelectDog.setSelected(false);
        btnSelectCat.setSelected(false);

        button.setSelected(true);
        selectedUnitType = unitType;

        Toast.makeText(this, "Selected " + unitType + ". Click on grid to place.", Toast.LENGTH_SHORT).show();
    }

    private void createGameGrid() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = 32;
        int cellSize = (screenWidth - gridPadding) / 8;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                ImageView cell = new ImageView(this);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(2, 2, 2, 2);
                params.rowSpec = GridLayout.spec(row);
                params.columnSpec = GridLayout.spec(col);
                cell.setLayoutParams(params);

                cell.setBackgroundColor(Color.parseColor("#8FBC8F"));
                cell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.setPadding(8, 8, 8, 8);

                gridCells[row][col] = cell;

                final int finalRow = row;
                final int finalCol = col;
                cell.setOnClickListener(v -> onCellClicked(finalRow, finalCol));

                gameGrid.addView(cell);
            }
        }
    }

    private void onCellClicked(int row, int col) {
        ImageView cell = gridCells[row][col];

        if (selectedUnitType == null) {
            Toast.makeText(this, "Select a unit first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cell.getTag() != null) {
            Toast.makeText(this, "Cell already occupied!", Toast.LENGTH_SHORT).show();
            return;
        }

        placeUnit(row, col, selectedUnitType);
    }

    private void placeUnit(int row, int col, String unitType) {
        ImageView cell = gridCells[row][col];

        int iconResource = getUnitIcon(unitType);
        cell.setImageResource(iconResource);
        cell.setTag(unitType);

        playerUnits.add(new SetupActivity.UnitPosition(row, col, unitType, 2));

        cell.setScaleX(0.3f);
        cell.setScaleY(0.3f);
        cell.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .start();

        updateUnitCount(unitType);
        totalUnitsPlaced++;

        if (totalUnitsPlaced >= TOTAL_UNITS) {
            markAsReady();
        }
    }

    private void updateUnitCount(String unitType) {
        switch (unitType) {
            case "sunflower":
                sunflowerCount--;
                tvSunflowerCount.setText("x" + sunflowerCount);
                if (sunflowerCount == 0) {
                    btnSelectSunflower.setEnabled(false);
                    btnSelectSunflower.setAlpha(0.3f);
                    if (selectedUnitType != null && selectedUnitType.equals("sunflower")) {
                        selectedUnitType = null;
                        btnSelectSunflower.setSelected(false);
                    }
                }
                break;
            case "rose":
                roseCount--;
                tvRoseCount.setText("x" + roseCount);
                if (roseCount == 0) {
                    btnSelectRose.setEnabled(false);
                    btnSelectRose.setAlpha(0.3f);
                    if (selectedUnitType != null && selectedUnitType.equals("rose")) {
                        selectedUnitType = null;
                        btnSelectRose.setSelected(false);
                    }
                }
                break;
            case "dog":
                dogCount--;
                tvDogCount.setText("x" + dogCount);
                if (dogCount == 0) {
                    btnSelectDog.setEnabled(false);
                    btnSelectDog.setAlpha(0.3f);
                    if (selectedUnitType != null && selectedUnitType.equals("dog")) {
                        selectedUnitType = null;
                        btnSelectDog.setSelected(false);
                    }
                }
                break;
            case "cat":
                catCount--;
                tvCatCount.setText("x" + catCount);
                if (catCount == 0) {
                    btnSelectCat.setEnabled(false);
                    btnSelectCat.setAlpha(0.3f);
                    if (selectedUnitType != null && selectedUnitType.equals("cat")) {
                        selectedUnitType = null;
                        btnSelectCat.setSelected(false);
                    }
                }
                break;
        }
    }

    private void markAsReady() {
        unitSelectionMenu.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(500)
                .withEndAction(() -> {
                    unitSelectionMenu.setVisibility(View.GONE);
                    Toast.makeText(this, "Ready! Waiting for opponent...", Toast.LENGTH_LONG).show();

                    // Mark ready in Firebase
                    firebaseManager.setPlayerReady(roomCode, isHost, true);
                })
                .start();
    }

    private void listenForOpponent() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String playerKey = isHost ? "player2" : "player1";
                boolean opponentReady = snapshot.child(playerKey).child("ready").getValue(Boolean.class) != null &&
                        snapshot.child(playerKey).child("ready").getValue(Boolean.class);

                MultiplayerSetupActivity.this.opponentReady = opponentReady;

                // Check if both ready
                boolean meReady = totalUnitsPlaced >= TOTAL_UNITS;
                if (meReady && opponentReady) {
                    Toast.makeText(MultiplayerSetupActivity.this,
                            "Both players ready! Starting battle...",
                            Toast.LENGTH_SHORT).show();

                    new Handler().postDelayed(() -> startMultiplayerBattle(), 1500);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MultiplayerSetupActivity.this,
                        "Connection error", Toast.LENGTH_SHORT).show();
            }
        };

        firebaseManager.listenToRoom(roomCode, roomListener);
    }

    private void startMultiplayerBattle() {
        // Remove listener
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }

        // Launch multiplayer battle
        Intent intent = new Intent(this, MultiplayerBattleActivity.class);
        intent.putExtra("ROOM_CODE", roomCode);
        intent.putExtra("IS_HOST", isHost);
        intent.putExtra("SELECTED_POWER", selectedPower);
        intent.putParcelableArrayListExtra("PLAYER_UNITS", new ArrayList<>(playerUnits));
        startActivity(intent);
        finish();
    }

    private int getUnitIcon(String unitType) {
        switch (unitType) {
            case "sunflower":
                return R.drawable.sunflower_icon;
            case "rose":
                return R.drawable.rose_red;
            case "dog":
                return R.drawable.dog_icon;
            case "cat":
                return R.drawable.cat_icon;
            default:
                return R.drawable.sunflower_icon;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }
    }
}