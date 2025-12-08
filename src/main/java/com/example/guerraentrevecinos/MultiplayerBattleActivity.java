package com.example.guerraentrevecinos;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultiplayerBattleActivity extends AppCompatActivity {

    private static final String TAG = "MultiplayerBattle";
    private static final int REQUEST_CODE_MINI_DUEL = 100;

    // UI Components
    private GridLayout playerGrid, enemyGrid;
    private TextView tvTurnIndicator, tvRoundCounter;
    private View playerGardenSection, enemyGardenSection;
    private MaterialButton btnGardenHose, btnNighttimeRelocation, btnTier2Power;

    // Game State
    private List<SetupActivity.UnitPosition> playerUnits;
    private List<SetupActivity.UnitPosition> enemyUnits;
    private boolean isMyTurn = false;
    private boolean hasAttackedThisTurn = false;
    private int currentRound = 1;
    private static final int MAX_ROUNDS = 30;

    // Grid cells
    private ImageView[][] playerCells = new ImageView[8][8];
    private ImageView[][] enemyCells = new ImageView[8][8];
    private boolean[][] enemyRevealedCells = new boolean[8][8];

    // Multiplayer
    private FirebaseManager firebaseManager;
    private String roomCode;
    private boolean isHost;
    private String myPlayerKey;
    private String opponentPlayerKey;
    private ValueEventListener roomListener;

    // Powers
    private PowerManager powerManager;
    private String selectedPower;
    private AbilityManager abilityManager;

    // Pending duel state
    private boolean waitingForDuelResult = false;
    private int pendingAttackRow = -1;
    private int pendingAttackCol = -1;
    private String pendingUnitType = "";
    private long lastProcessedActionTimestamp = 0; // Track processed actions
    private boolean iAmAttackerInCurrentDuel = false; // Track role in current duel
    private List<SetupActivity.UnitPosition> revealedEnemyUnits = new ArrayList<>();
    private boolean isDuelActivityLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "MultiplayerBattleActivity onCreate START");
        Log.d(TAG, "═══════════════════════════════════════");

        try {
            // Get data
            roomCode = getIntent().getStringExtra("ROOM_CODE");
            isHost = getIntent().getBooleanExtra("IS_HOST", false);
            selectedPower = getIntent().getStringExtra("SELECTED_POWER");
            playerUnits = getIntent().getParcelableArrayListExtra("PLAYER_UNITS");

            Log.d(TAG, "Room Code: " + roomCode);
            Log.d(TAG, "Is Host: " + isHost);
            Log.d(TAG, "Selected Power: " + selectedPower);
            Log.d(TAG, "Player Units: " + (playerUnits != null ? playerUnits.size() : "NULL"));

            // Validate data
            if (roomCode == null || playerUnits == null || playerUnits.isEmpty()) {
                Log.e(TAG, "ERROR: Missing required data!");
                Toast.makeText(this, "Error loading game data", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Initialize empty enemy units
            enemyUnits = new ArrayList<>();

            myPlayerKey = isHost ? "player1" : "player2";
            opponentPlayerKey = isHost ? "player2" : "player1";

            Log.d(TAG, "My Key: " + myPlayerKey);
            Log.d(TAG, "Opponent Key: " + opponentPlayerKey);

            // Initialize managers
            firebaseManager = FirebaseManager.getInstance();

            if (!firebaseManager.isInitialized()) {
                Log.e(TAG, "ERROR: Firebase not initialized!");
                Toast.makeText(this, "Firebase connection error", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            powerManager = new PowerManager(selectedPower != null ? selectedPower : "spy_drone");
            abilityManager = new AbilityManager(this);

            // Initialize views
            initializeViews();
            setupPowerButtons();
            createPlayerGrid();
            createEnemyGrid();

            // Initialize game state if host
            if (isHost) {
                initializeGameState();
            }

            // Listen for game updates FIRST
            listenForGameUpdates();

            // THEN store units and load opponent units
            // Wait 1 second to ensure listener is ready
            new Handler().postDelayed(() -> {
                storeMyUnitsInFirebase();
                loadOpponentUnits();
            }, 1000);

            Log.d(TAG, "onCreate completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "FATAL ERROR in onCreate", e);
            Toast.makeText(this, "Error starting game: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadOpponentUnits() {
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "loadOpponentUnits START");
        Log.d(TAG, "Looking for " + opponentPlayerKey + " units");
        Log.d(TAG, "════════════════════════════════════════");

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot opponentUnitsSnapshot = snapshot.child("units").child(opponentPlayerKey);

                if (!opponentUnitsSnapshot.exists()) {
                    Log.w(TAG, "⚠️ Opponent units not found yet, will keep listening...");
                    // Don't remove listener - keep waiting for opponent
                    return;
                }

                enemyUnits.clear();
                int loadedCount = 0;

                for (DataSnapshot unitSnapshot : opponentUnitsSnapshot.getChildren()) {
                    try {
                        Integer row = unitSnapshot.child("row").getValue(Integer.class);
                        Integer col = unitSnapshot.child("col").getValue(Integer.class);
                        String type = unitSnapshot.child("type").getValue(String.class);
                        Integer health = unitSnapshot.child("health").getValue(Integer.class);

                        if (row != null && col != null && type != null && health != null) {
                            SetupActivity.UnitPosition unit = new SetupActivity.UnitPosition(row, col, type, health);
                            enemyUnits.add(unit);
                            loadedCount++;
                            Log.d(TAG, "  ✅ Loaded: " + type + " at (" + row + "," + col + ")");
                        } else {
                            Log.w(TAG, "  ⚠️ Incomplete unit data: " + unitSnapshot.getKey());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "  ❌ Error loading unit: " + e.getMessage());
                    }
                }

                Log.d(TAG, "════════════════════════════════════════");
                Log.d(TAG, "✅ LOADED " + loadedCount + " opponent units");
                Log.d(TAG, "════════════════════════════════════════");

                if (loadedCount > 0) {
                    runOnUiThread(() -> {
                        Toast.makeText(MultiplayerBattleActivity.this,
                                "Opponent ready! Game starting...",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                // Keep listener active to detect unit updates (health changes, etc.)
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Failed to load opponent units: " + error.getMessage());
            }
        });
    }

    private void storeMyUnitsInFirebase() {
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "storeMyUnitsInFirebase START");
        Log.d(TAG, "Storing " + playerUnits.size() + " units as " + myPlayerKey);
        Log.d(TAG, "════════════════════════════════════════");

        if (playerUnits == null || playerUnits.isEmpty()) {
            Log.e(TAG, "ERROR: No units to store!");
            return;
        }

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "ERROR: Room doesn't exist!");
                    snapshot.getRef().removeEventListener(this);
                    return;
                }

                Log.d(TAG, "Room exists, storing units...");

                // Store all units in one batch
                Map<String, Object> allUnits = new HashMap<>();

                for (int i = 0; i < playerUnits.size(); i++) {
                    SetupActivity.UnitPosition unit = playerUnits.get(i);

                    Map<String, Object> unitData = new HashMap<>();
                    unitData.put("row", unit.row);
                    unitData.put("col", unit.col);
                    unitData.put("type", unit.type);
                    unitData.put("health", unit.health);

                    allUnits.put(String.valueOf(i), unitData);
                }

                // Write all at once
                snapshot.getRef().child("units").child(myPlayerKey).setValue(allUnits)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ SUCCESS: Stored all " + playerUnits.size() + " units");
                            runOnUiThread(() -> {
                                Toast.makeText(MultiplayerBattleActivity.this,
                                        "Units ready!", Toast.LENGTH_SHORT).show();
                            });
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ FAILED to store units: " + e.getMessage());
                            e.printStackTrace();

                            runOnUiThread(() -> {
                                Toast.makeText(MultiplayerBattleActivity.this,
                                        "Error saving units: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });

                            // Retry after 2 seconds
                            new Handler().postDelayed(() -> {
                                Log.d(TAG, "Retrying unit storage...");
                                storeMyUnitsInFirebase();
                            }, 2000);
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Firebase error storing units: " + error.getMessage());

                runOnUiThread(() -> {
                    Toast.makeText(MultiplayerBattleActivity.this,
                            "Connection error: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void initializeViews() {
        playerGrid = findViewById(R.id.playerGrid);
        enemyGrid = findViewById(R.id.enemyGrid);
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator);
        tvRoundCounter = findViewById(R.id.tvRoundCounter);
        playerGardenSection = findViewById(R.id.playerGardenSection);
        enemyGardenSection = findViewById(R.id.enemyGardenSection);

        updateRoundCounter();
    }

    private void setupPowerButtons() {
        btnGardenHose = findViewById(R.id.btnGardenHose);
        btnNighttimeRelocation = findViewById(R.id.btnNighttimeRelocation);
        btnTier2Power = findViewById(R.id.btnTier2Power);

        switch (selectedPower) {
            case "spy_drone":
                btnTier2Power.setText("🐝\nSpy");
                break;
            case "fence_shield":
                btnTier2Power.setText("🛡️\nFence");
                break;
            case "fertilizer":
                btnTier2Power.setText("🌱\nHeal");
                break;
        }

        // ✅ Implement power button clicks
        btnGardenHose.setOnClickListener(v -> {
            if (powerManager.canUseGardenHose() && isMyTurn) {
                powerManager.activateGardenHose();
                Toast.makeText(this, "💧 Garden Hose activated! Next attack picks 2 numbers.",
                        Toast.LENGTH_LONG).show();
                updatePowerButtons();
            }
        });

        btnNighttimeRelocation.setOnClickListener(v -> {
            if (powerManager.canUseNighttimeRelocation() && isMyTurn) {
                activateUnitSelectionMode("move");
            }
        });

        btnTier2Power.setOnClickListener(v -> {
            if (powerManager.canUseTier2Power() && isMyTurn) {
                handleTier2PowerClick();
            }
        });

        updatePowerButtons();
    }

    private void handleTier2PowerClick() {
        switch (selectedPower) {
            case "spy_drone":
                activateUnitSelectionMode("spy");
                break;
            case "fence_shield":
                activateUnitSelectionMode("fence");
                break;
            case "fertilizer":
                activateUnitSelectionMode("fertilizer");
                break;
        }
    }

    private boolean isSelectingUnitForPower = false;
    private String activePowerMode = null;

    private void activateUnitSelectionMode(String powerMode) {
        isSelectingUnitForPower = true;
        activePowerMode = powerMode;

        String message = "";
        switch (powerMode) {
            case "move":
                message = "🌙 Select a unit to move";
                // ✅ Show player garden for move
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
            case "spy":
                message = "🐝 Click on enemy grid to reveal 3x3 area";
                // ✅ Keep enemy garden visible for spy
                playerGardenSection.setVisibility(View.GONE);
                enemyGardenSection.setVisibility(View.VISIBLE);
                break;
            case "fence":
                message = "🛡️ Select a unit to protect";
                // ✅ Show player garden for fence
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
            case "fertilizer":
                message = "🌱 Select a wounded unit to heal";
                // ✅ Show player garden for fertilizer
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
        }

        Toast.makeText(this, message + "\n(Tap screen to cancel)", Toast.LENGTH_LONG).show();
        tvTurnIndicator.setText(message);
    }

    private void createPlayerGrid() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = 32;
        int cellSize = (screenWidth - gridPadding) / 8;

        playerGrid.setColumnCount(8);
        playerGrid.setRowCount(8);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                ImageView cell = new ImageView(this);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(2, 2, 2, 2);
                params.rowSpec = GridLayout.spec(row, 1);
                params.columnSpec = GridLayout.spec(col, 1);
                cell.setLayoutParams(params);

                cell.setBackgroundColor(Color.parseColor("#8FBC8F"));
                cell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.setPadding(8, 8, 8, 8);

                for (SetupActivity.UnitPosition unit : playerUnits) {
                    if (unit.row == row && unit.col == col) {
                        cell.setImageResource(getUnitIcon(unit.type));
                        cell.setTag(unit);
                        break;
                    }
                }

                playerCells[row][col] = cell;

                // ✅ Add click listener for power usage
                final int finalRow = row;
                final int finalCol = col;
                cell.setOnClickListener(v -> {
                    if (isSelectingUnitForPower &&
                            (activePowerMode.equals("move") ||
                                    activePowerMode.equals("fence") ||
                                    activePowerMode.equals("fertilizer"))) {
                        onPlayerCellClickedForPower(finalRow, finalCol);
                    }
                });

                playerGrid.addView(cell);
            }
        }
    }

    private void createEnemyGrid() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int gridPadding = 32;
        int cellSize = (screenWidth - gridPadding) / 8;

        enemyGrid.setColumnCount(8);
        enemyGrid.setRowCount(8);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                ImageView cell = new ImageView(this);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = cellSize;
                params.height = cellSize;
                params.setMargins(2, 2, 2, 2);
                params.rowSpec = GridLayout.spec(row, 1);
                params.columnSpec = GridLayout.spec(col, 1);
                cell.setLayoutParams(params);

                cell.setBackgroundColor(Color.parseColor("#999999"));
                cell.setAlpha(0.8f);
                cell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.setPadding(8, 8, 8, 8);

                final int finalRow = row;
                final int finalCol = col;
                cell.setOnClickListener(v -> {
                    // ✅ Check if using spy drone power
                    if (isSelectingUnitForPower && activePowerMode.equals("spy")) {
                        onEnemyCellClickedForPower(finalRow, finalCol);
                    } else {
                        // Normal attack
                        onEnemyCellClicked(finalRow, finalCol);
                    }
                });

                enemyCells[row][col] = cell;
                enemyGrid.addView(cell);
            }
        }
    }

    private void initializeGameState() {
        Log.d(TAG, "Host initializing game state");
        FirebaseGameRoom.GameStateData gameState = new FirebaseGameRoom.GameStateData();
        gameState.currentRound = 1;
        gameState.currentTurn = "player1"; // Host goes first
        gameState.player1UnitsRemaining = 7;
        gameState.player2UnitsRemaining = 7;

        firebaseManager.updateGameState(roomCode, gameState);
    }

    private void listenForGameUpdates() {
        Log.d(TAG, "Starting to listen for game updates");

        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Room snapshot doesn't exist");
                    Toast.makeText(MultiplayerBattleActivity.this,
                            "Game ended", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                Log.d(TAG, "Received game update");

                // DEBUG: Print Firebase structure
                Log.d(TAG, "════════════════════════════════════");
                Log.d(TAG, "FIREBASE SNAPSHOT");
                Log.d(TAG, "════════════════════════════════════");

                if (snapshot.child("activeFears").exists()) {
                    Log.d(TAG, "Active Fears:");
                    for (DataSnapshot fear : snapshot.child("activeFears").getChildren()) {
                        Log.d(TAG, "  - " + fear.getKey());
                    }
                } else {
                    Log.d(TAG, "No active fears");
                }

                Log.d(TAG, "My Units (" + myPlayerKey + "):");
                DataSnapshot myUnits = snapshot.child("units").child(myPlayerKey);
                if (myUnits.exists()) {
                    for (DataSnapshot unit : myUnits.getChildren()) {
                        String type = unit.child("type").getValue(String.class);
                        Integer row = unit.child("row").getValue(Integer.class);
                        Integer col = unit.child("col").getValue(Integer.class);
                        Log.d(TAG, "  - " + type + " at (" + row + "," + col + ")");
                    }
                }

                Log.d(TAG, "Opponent Units (" + opponentPlayerKey + "):");
                DataSnapshot oppUnits = snapshot.child("units").child(opponentPlayerKey);
                if (oppUnits.exists()) {
                    for (DataSnapshot unit : oppUnits.getChildren()) {
                        String type = unit.child("type").getValue(String.class);
                        Integer row = unit.child("row").getValue(Integer.class);
                        Integer col = unit.child("col").getValue(Integer.class);
                        Log.d(TAG, "  - " + type + " at (" + row + "," + col + ")");
                    }
                }

                Log.d(TAG, "════════════════════════════════════");

                // ========================================
                // 1. UPDATE GAME STATE (Turn & Round)
                // ========================================
                DataSnapshot gameStateSnapshot = snapshot.child("gameState");
                String currentTurn = gameStateSnapshot.child("currentTurn").getValue(String.class);
                Integer round = gameStateSnapshot.child("currentRound").getValue(Integer.class);

                Log.d(TAG, "Current turn: " + currentTurn + ", My turn: " + myPlayerKey + ", Round: " + round);

                if (round != null && round != currentRound) {
                    currentRound = round;
                    updateRoundCounter();
                    powerManager.decrementCooldowns();
                    updatePowerButtons();
                }

                boolean newIsMyTurn = myPlayerKey.equals(currentTurn);
                if (newIsMyTurn != isMyTurn) {
                    isMyTurn = newIsMyTurn;
                    hasAttackedThisTurn = false;
                    waitingForDuelResult = false;
                    updateTurnIndicator();
                    setEnemyGridClickable(isMyTurn);
                    Log.d(TAG, "Turn changed. My turn: " + isMyTurn);
                }

                // ========================================
                // 2. CAT TELEPORT - Listen for Unit Position Changes
                // ========================================
                DataSnapshot opponentUnitsSnapshot = snapshot.child("units").child(opponentPlayerKey);
                if (opponentUnitsSnapshot.exists() && enemyUnits != null && !enemyUnits.isEmpty()) {

                    // Rebuild enemy units from Firebase
                    List<SetupActivity.UnitPosition> updatedEnemyUnits = new ArrayList<>();

                    for (DataSnapshot unitSnap : opponentUnitsSnapshot.getChildren()) {
                        String type = unitSnap.child("type").getValue(String.class);
                        Integer row = unitSnap.child("row").getValue(Integer.class);
                        Integer col = unitSnap.child("col").getValue(Integer.class);
                        Integer health = unitSnap.child("health").getValue(Integer.class);

                        if (type != null && row != null && col != null && health != null && health > 0) {
                            updatedEnemyUnits.add(new SetupActivity.UnitPosition(row, col, type, health));
                        }
                    }

                    // Check for cat changes
                    for (SetupActivity.UnitPosition updatedUnit : updatedEnemyUnits) {
                        if ("cat".equals(updatedUnit.type)) {

                            // Find old cat in our local list
                            SetupActivity.UnitPosition oldCat = null;
                            for (SetupActivity.UnitPosition localUnit : enemyUnits) {
                                if ("cat".equals(localUnit.type) && localUnit.health > 0) {
                                    oldCat = localUnit;
                                    break;
                                }
                            }

                            // Cat moved?
                            if (oldCat != null &&
                                    (oldCat.row != updatedUnit.row || oldCat.col != updatedUnit.col)) {

                                final int oldRow = oldCat.row;
                                final int oldCol = oldCat.col;
                                final int newRow = updatedUnit.row;
                                final int newCol = updatedUnit.col;
                                final SetupActivity.UnitPosition finalUpdatedUnit = updatedUnit;

                                Log.d(TAG, "🐱 ENEMY CAT TELEPORTED!");
                                Log.d(TAG, "   From: (" + oldRow + "," + oldCol + ")");
                                Log.d(TAG, "   To: (" + newRow + "," + newCol + ")");

                                runOnUiThread(() -> {
                                    // Clear old position
                                    ImageView oldCell = enemyCells[oldRow][oldCol];
                                    oldCell.setImageDrawable(null);
                                    oldCell.setTag(null);

                                    if (enemyRevealedCells[oldRow][oldCol]) {
                                        oldCell.setBackgroundColor(Color.parseColor("#C5E1A5"));
                                    } else {
                                        oldCell.setBackgroundColor(Color.parseColor("#999999"));
                                    }

                                    // Show at new position
                                    ImageView newCell = enemyCells[newRow][newCol];
                                    enemyRevealedCells[newRow][newCol] = true;

                                    newCell.setBackgroundColor(Color.parseColor("#FFA500"));
                                    newCell.setImageResource(R.drawable.cat_icon);
                                    newCell.setTag(finalUpdatedUnit);
                                    newCell.setAlpha(1f);

                                    // Animate
                                    newCell.setScaleX(0.1f);
                                    newCell.setScaleY(0.1f);
                                    newCell.setRotation(0f);

                                    ImageView finalNewCell = newCell;
                                    newCell.animate()
                                            .scaleX(1.2f)
                                            .scaleY(1.2f)
                                            .rotation(720f)
                                            .setDuration(500)
                                            .withEndAction(() -> {
                                                finalNewCell.animate()
                                                        .scaleX(1f)
                                                        .scaleY(1f)
                                                        .setDuration(200)
                                                        .start();
                                            })
                                            .start();

                                    Toast.makeText(MultiplayerBattleActivity.this,
                                            "🐱 Enemy cat teleported to (" + newRow + "," + newCol + ")!",
                                            Toast.LENGTH_LONG).show();
                                });

                                break;
                            }
                        }
                    }

                    // ✅ NEW: Check if cat was removed (died after teleport)
                    SetupActivity.UnitPosition oldCat = null;
                    for (SetupActivity.UnitPosition localUnit : enemyUnits) {
                        if ("cat".equals(localUnit.type) && localUnit.health > 0) {
                            oldCat = localUnit;
                            break;
                        }
                    }

                    // Cat existed before but not now = died
                    if (oldCat != null) {
                        boolean catStillExists = false;
                        for (SetupActivity.UnitPosition u : updatedEnemyUnits) {
                            if ("cat".equals(u.type)) {
                                catStillExists = true;
                                break;
                            }
                        }

                        if (!catStillExists) {
                            final int deadCatRow = oldCat.row;
                            final int deadCatCol = oldCat.col;

                            Log.d(TAG, "🐱💀 ENEMY CAT DIED at (" + deadCatRow + "," + deadCatCol + ")");

                            runOnUiThread(() -> {
                                ImageView deadCell = enemyCells[deadCatRow][deadCatCol];

                                // Only update if cell shows cat (might already be updated by attack result)
                                if (deadCell.getTag() != null) {
                                    deadCell.setBackgroundColor(Color.parseColor("#8B4513"));
                                    deadCell.setImageResource(R.drawable.explosion_icon);
                                    deadCell.setTag(null);

                                    ImageView finalDeadCell = deadCell;
                                    deadCell.animate()
                                            .scaleX(0.5f)
                                            .scaleY(0.5f)
                                            .alpha(0.5f)
                                            .setDuration(600)
                                            .start();

                                    Log.d(TAG, "Showed cat death animation");
                                }
                            });
                        }
                    }

                    // Update local list
                    enemyUnits = updatedEnemyUnits;
                }

                // ========================================
                // 3. CHECK FOR PENDING ACTIONS
                // ========================================
                DataSnapshot lastActionSnapshot = snapshot.child("lastAction");
                if (lastActionSnapshot.exists()) {
                    String actionPlayer = lastActionSnapshot.child("player").getValue(String.class);
                    String actionType = lastActionSnapshot.child("type").getValue(String.class);
                    Long timestamp = lastActionSnapshot.child("timestamp").getValue(Long.class);

                    if (timestamp != null && timestamp > lastProcessedActionTimestamp) {
                        Log.d(TAG, "====================================");
                        Log.d(TAG, "NEW Action Detected");
                        Log.d(TAG, "Player: " + actionPlayer);
                        Log.d(TAG, "Type: " + actionType);
                        Log.d(TAG, "Timestamp: " + timestamp);
                        Log.d(TAG, "My State - Waiting: " + waitingForDuelResult +
                                ", Is Attacker: " + iAmAttackerInCurrentDuel);
                        Log.d(TAG, "====================================");

                        // ========================================
                        // 1. DEFENDER: Opponent attacked me
                        // ========================================
                        if (opponentPlayerKey.equals(actionPlayer) &&
                                "attack".equals(actionType) &&
                                !waitingForDuelResult) { // ✅ Only if not already in a duel

                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            if (targetRow != null && targetCol != null) {
                                Log.d(TAG, ">>> I'M DEFENDING at (" + targetRow + "," + targetCol + ")");

                                lastProcessedActionTimestamp = timestamp;

                                final SetupActivity.UnitPosition hitUnit = findUnitAtPosition(targetRow, targetCol);

                                if (hitUnit != null) {
                                    Log.d(TAG, ">>> HIT! Unit: " + hitUnit.type + " - Launching DEFENDER duel");

                                    // ✅ Set state BEFORE launching duel
                                    waitingForDuelResult = true;
                                    iAmAttackerInCurrentDuel = false;
                                    pendingAttackRow = targetRow;
                                    pendingAttackCol = targetCol;
                                    pendingUnitType = hitUnit.type;

                                    showRockFalling(targetRow, targetCol);

                                    // Notify attacker we're ready
                                    FirebaseGameRoom.LastActionData responseAction = new FirebaseGameRoom.LastActionData();
                                    responseAction.type = "duel_ready";
                                    responseAction.player = myPlayerKey;
                                    responseAction.targetRow = targetRow;
                                    responseAction.targetCol = targetCol;
                                    responseAction.unitType = hitUnit.type;
                                    responseAction.timestamp = System.currentTimeMillis();

                                    firebaseManager.sendAction(roomCode, responseAction);

                                    final int finalRow = targetRow;
                                    final int finalCol = targetCol;
                                    final String finalUnitType = hitUnit.type;

                                    // Launch defender duel after delay
                                    new Handler().postDelayed(() -> {
                                        // ✅ Double-check we're still in the right state
                                        if (waitingForDuelResult && !iAmAttackerInCurrentDuel) {
                                            Log.d(TAG, ">>> Launching DEFENDER MiniDuel NOW");
                                            launchMiniDuel(finalRow, finalCol, finalUnitType, false);
                                        } else {
                                            Log.w(TAG, ">>> CANCELLED DEFENDER launch - state changed");
                                        }
                                    }, 1500);

                                } else {
                                    // Miss
                                    Log.d(TAG, ">>> MISS at (" + targetRow + "," + targetCol + ")");

                                    FirebaseGameRoom.LastActionData missAction = new FirebaseGameRoom.LastActionData();
                                    missAction.type = "miss";
                                    missAction.player = myPlayerKey;
                                    missAction.targetRow = targetRow;
                                    missAction.targetCol = targetCol;
                                    missAction.timestamp = System.currentTimeMillis();

                                    firebaseManager.sendAction(roomCode, missAction);
                                }
                            }
                        }

                        // ========================================
                        // 2. ATTACKER: Defender confirmed hit
                        // ========================================
                        else if (opponentPlayerKey.equals(actionPlayer) &&
                                "duel_ready".equals(actionType) &&
                                waitingForDuelResult && // ✅ Only if we sent an attack
                                iAmAttackerInCurrentDuel) { // ✅ And we're the attacker

                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);
                            String unitType = lastActionSnapshot.child("unitType").getValue(String.class);

                            if (targetRow != null && targetCol != null) {
                                Log.d(TAG, ">>> Defender confirmed HIT! Launching ATTACKER duel");

                                lastProcessedActionTimestamp = timestamp;

                                final int finalRow = targetRow;
                                final int finalCol = targetCol;
                                final String finalUnitType = unitType != null ? unitType : "sunflower";

                                runOnUiThread(() -> {
                                    // ✅ One final check before launching
                                    if (waitingForDuelResult && iAmAttackerInCurrentDuel) {
                                        Log.d(TAG, ">>> Launching ATTACKER MiniDuel NOW");
                                        launchMiniDuel(finalRow, finalCol, finalUnitType, true);
                                    } else {
                                        Log.w(TAG, ">>> CANCELLED ATTACKER launch - state changed");
                                    }
                                });
                            }
                        }

                        // ========================================
                        // 3. ATTACKER: Defender confirmed miss
                        // ========================================
                        else if (opponentPlayerKey.equals(actionPlayer) &&
                                "miss".equals(actionType) &&
                                waitingForDuelResult &&
                                iAmAttackerInCurrentDuel) {

                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            Log.d(TAG, ">>> Defender confirmed MISS");

                            lastProcessedActionTimestamp = timestamp;

                            if (targetRow != null && targetCol != null) {
                                final int finalRow = targetRow;
                                final int finalCol = targetCol;

                                runOnUiThread(() -> showMissOnEnemyGrid(finalRow, finalCol));
                            }

                            waitingForDuelResult = false;
                            iAmAttackerInCurrentDuel = false;
                            new Handler().postDelayed(() -> endMyTurn(), 1500);
                        }
                    } else {
                        // Old action - ignore
                        if (timestamp != null) {
                            Log.d(TAG, "Ignoring old action: timestamp=" + timestamp +
                                    ", lastProcessed=" + lastProcessedActionTimestamp);
                        }
                    }
                }

                // ========================================
                // 9. CHECK WIN CONDITION
                // ========================================
                Integer p1Units = gameStateSnapshot.child("player1UnitsRemaining").getValue(Integer.class);
                Integer p2Units = gameStateSnapshot.child("player2UnitsRemaining").getValue(Integer.class);

                if (p1Units != null && p1Units == 0) {
                    endGame(!isHost);
                } else if (p2Units != null && p2Units == 0) {
                    endGame(isHost);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled: " + error.getMessage());
                Toast.makeText(MultiplayerBattleActivity.this,
                        "Connection error", Toast.LENGTH_SHORT).show();
            }
        };

        firebaseManager.listenToRoom(roomCode, roomListener);
    }

    private void onEnemyCellClicked(int row, int col) {
        Log.d(TAG, "Enemy cell clicked: (" + row + "," + col + ")");

        if (!isMyTurn) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hasAttackedThisTurn) {
            Toast.makeText(this, "You already attacked this turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (waitingForDuelResult) {
            Toast.makeText(this, "Wait for current action!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for Dog Fear FIRST
        checkDogFearAndAttack(row, col);
    }

    private void verifyCatPosition(int row, int col, String context) {
        runOnUiThread(() -> {
            ImageView cell = enemyCells[row][col];

            Log.d(TAG, "=== VERIFY CAT POSITION: " + context + " ===");
            Log.d(TAG, "Position: (" + row + "," + col + ")");
            Log.d(TAG, "Cell has image: " + (cell.getDrawable() != null));
            Log.d(TAG, "Cell background color: " + cell.getDrawable());
            Log.d(TAG, "Cell tag: " + cell.getTag());
            Log.d(TAG, "Cell alpha: " + cell.getAlpha());
            Log.d(TAG, "Cell visibility: " + cell.getVisibility());
            Log.d(TAG, "Revealed: " + enemyRevealedCells[row][col]);

            // Check if there's a unit at this position
            SetupActivity.UnitPosition unitHere = null;
            for (SetupActivity.UnitPosition u : enemyUnits) {
                if (u.row == row && u.col == col && u.health > 0) {
                    unitHere = u;
                    break;
                }
            }

            if (unitHere != null) {
                Log.d(TAG, "Unit found: " + unitHere.type + " (HP: " + unitHere.health + ")");
            } else {
                Log.d(TAG, "No unit found at this position in enemyUnits list!");
            }

            Log.d(TAG, "=====================================");
        });
    }

    private void removeUnitFromFirebase(String playerKey, int row, int col) {
        Log.d(TAG, "Removing dead unit from Firebase: " + playerKey + " at (" + row + "," + col + ")");

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot unitsSnapshot = snapshot.child("units").child(playerKey);

                // Find and remove the unit
                for (DataSnapshot unitSnap : unitsSnapshot.getChildren()) {
                    Integer snapRow = unitSnap.child("row").getValue(Integer.class);
                    Integer snapCol = unitSnap.child("col").getValue(Integer.class);

                    if (snapRow != null && snapCol != null &&
                            snapRow == row && snapCol == col) {

                        unitSnap.getRef().removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Removed dead unit from Firebase");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to remove unit: " + e.getMessage());
                                });

                        break;
                    }
                }

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error removing unit: " + error.getMessage());
            }
        });
    }


    private void checkDogFearAndAttack(int row, int col) {
        String fearId = opponentPlayerKey + "_" + row + "_" + col;

        Log.d(TAG, "Checking fear: " + fearId);

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot fear = snapshot.child("dogFears").child(fearId);

                if (fear.exists()) {
                    Integer fearRow = fear.child("row").getValue(Integer.class);
                    Integer fearCol = fear.child("col").getValue(Integer.class);

                    if (fearRow != null && fearRow == row && fearCol != null && fearCol == col) {
                        Log.d(TAG, "🐕 DOG FEAR BLOCKED!");

                        runOnUiThread(() -> {
                            ImageView cell = enemyCells[row][col];

                            // Red flash animation
                            cell.setBackgroundColor(Color.parseColor("#FF0000"));
                            cell.animate()
                                    .translationX(-20f).setDuration(50)
                                    .withEndAction(() -> {
                                        cell.animate().translationX(20f).setDuration(50)
                                                .withEndAction(() -> {
                                                    cell.animate().translationX(0f).setDuration(50).start();
                                                }).start();
                                    }).start();

                            // Restore color
                            new Handler().postDelayed(() -> {
                                if (enemyRevealedCells[row][col]) {
                                    cell.setBackgroundColor(Color.parseColor("#FFE082"));
                                } else {
                                    cell.setBackgroundColor(Color.parseColor("#999999"));
                                }
                            }, 300);

                            Toast.makeText(MultiplayerBattleActivity.this,
                                    "🐕 Dog's FEAR blocks attack!",
                                    Toast.LENGTH_LONG).show();
                        });

                        // Remove fear
                        fear.getRef().removeValue();
                        snapshot.getRef().removeEventListener(this);
                        return;
                    }
                }

                // No fear - proceed with attack
                snapshot.getRef().removeEventListener(this);
                proceedWithAttack(row, col);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Fear check error: " + error.getMessage());
                proceedWithAttack(row, col);
            }
        });
    }

    private void proceedWithAttack(int row, int col) {
        Log.d(TAG, "Attacking: (" + row + "," + col + ")");

        hasAttackedThisTurn = true;
        waitingForDuelResult = true;
        iAmAttackerInCurrentDuel = true;
        setEnemyGridClickable(false);

        pendingAttackRow = row;
        pendingAttackCol = col;

        // Get Garden Hose status NOW
        final boolean gardenHoseActive = powerManager.isGardenHoseActive();

        Log.d(TAG, "Garden Hose active: " + gardenHoseActive);

        // Create duel data
        Map<String, Object> duelData = new HashMap<>();
        duelData.put("attackerChoice", null);
        duelData.put("defenderChoice", null);
        duelData.put("gardenHoseActive", gardenHoseActive);
        if (gardenHoseActive) {
            duelData.put("attackerSecondChoice", null);
        }

        // Send attack action
        FirebaseGameRoom.LastActionData action = new FirebaseGameRoom.LastActionData();
        action.type = "attack";
        action.player = myPlayerKey;
        action.targetRow = row;
        action.targetCol = col;
        action.timestamp = System.currentTimeMillis();

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Initialize duel
                snapshot.getRef().child("currentDuel").setValue(duelData)
                        .addOnSuccessListener(aVoid -> {
                            // Send action
                            snapshot.getRef().child("lastAction").setValue(action.toMap())
                                    .addOnSuccessListener(a -> {
                                        Log.d(TAG, "✅ Attack sent successfully");
                                    });
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Attack error: " + error.getMessage());
                runOnUiThread(() -> {
                    hasAttackedThisTurn = false;
                    waitingForDuelResult = false;
                    setEnemyGridClickable(true);
                });
            }
        });

        Toast.makeText(this, "Attacking..." +
                        (gardenHoseActive ? " 💧 Garden Hose!" : ""),
                Toast.LENGTH_SHORT).show();
    }

    private void launchMiniDuel(int row, int col, String unitType, boolean isPlayerAttacking) {
        // ✅ Prevent double launch
        if (isDuelActivityLaunched) {
            Log.w(TAG, "❌ MiniDuel already launched! Ignoring duplicate call.");
            return;
        }

        Log.d(TAG, "====================================");
        Log.d(TAG, "LAUNCHING MINI DUEL");
        Log.d(TAG, "Position: (" + row + "," + col + ")");
        Log.d(TAG, "Unit: " + unitType);
        Log.d(TAG, "Is Attacking: " + isPlayerAttacking);
        Log.d(TAG, "====================================");

        // ✅ Mark as launched
        isDuelActivityLaunched = true;

        // Get Garden Hose status from Firebase
        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean gardenHose = snapshot.child("currentDuel")
                        .child("gardenHoseActive").getValue(Boolean.class);

                boolean hoseActive = (gardenHose != null && gardenHose);

                Log.d(TAG, "Garden Hose from Firebase: " + hoseActive);

                Intent intent = new Intent(MultiplayerBattleActivity.this, MiniDuelActivity.class);
                intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, unitType);
                intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, row);
                intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, col);
                intent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);
                intent.putExtra("GARDEN_HOSE_ACTIVE", hoseActive);
                intent.putExtra(MiniDuelActivity.EXTRA_IS_MULTIPLAYER, true);
                intent.putExtra(MiniDuelActivity.EXTRA_ROOM_CODE, roomCode);

                startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error launching duel: " + error.getMessage());
                isDuelActivityLaunched = false; // ✅ Reset on error
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ✅ Reset launch guard when MiniDuel returns
        isDuelActivityLaunched = false;

        Log.d(TAG, "====================================");
        Log.d(TAG, "MINI DUEL RETURNED");
        Log.d(TAG, "Request: " + requestCode + ", Result: " + resultCode);
        Log.d(TAG, "====================================");

        if (requestCode == REQUEST_CODE_MINI_DUEL && resultCode == RESULT_OK) {
            boolean wasHit = data.getBooleanExtra(MiniDuelActivity.EXTRA_WAS_HIT, false);
            int row = pendingAttackRow;
            int col = pendingAttackCol;

            Log.d(TAG, "Result: hit=" + wasHit + ", pos=(" + row + "," + col + ")");
            Log.d(TAG, "Role: " + (iAmAttackerInCurrentDuel ? "ATTACKER" : "DEFENDER"));

            // Deactivate Garden Hose after use
            if (powerManager.isGardenHoseActive()) {
                powerManager.deactivateGardenHose();
                updatePowerButtons();
                Log.d(TAG, "Garden Hose deactivated");
            }

            // Clear duel data
            firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    snapshot.getRef().child("currentDuel").removeValue();
                    snapshot.getRef().removeEventListener(this);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });

            // Process result
            if (iAmAttackerInCurrentDuel) {
                handleMyAttackResult(row, col, wasHit);
            } else {
                handleOpponentAttackResult(row, col, wasHit);
            }
        } else {
            // ✅ Duel cancelled or failed - reset state
            Log.w(TAG, "MiniDuel cancelled or failed");
            waitingForDuelResult = false;
            hasAttackedThisTurn = false;
            setEnemyGridClickable(isMyTurn);
        }
    }

    private void handleOpponentAttackResult(int row, int col, boolean wasHit) {
        Log.d(TAG, "=== DEFENDER PROCESSING RESULT ===");
        Log.d(TAG, "Hit: " + wasHit + " at (" + row + "," + col + ")");

        waitingForDuelResult = false;
        iAmAttackerInCurrentDuel = false;

        SetupActivity.UnitPosition unit = findUnitAtPosition(row, col);

        if (unit == null) {
            Log.w(TAG, "No unit at (" + row + "," + col + ")");
            return;
        }

        runOnUiThread(() -> {
            ImageView cell = playerCells[unit.row][unit.col];

            // Check Fence Protection FIRST
            if (powerManager.isUnitProtected(unit)) {
                cell.setBackgroundColor(Color.parseColor("#8FBC8F"));

                ImageView finalCell = cell;
                cell.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            finalCell.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(200)
                                    .start();
                        })
                        .start();

                powerManager.removeFenceProtection();
                Toast.makeText(this, "🛡️ Fence absorbed attack!",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // ========================================
            // DIRECT HIT - Unit would be destroyed
            // ========================================
            if (wasHit) {
                // CAT TELEPORT before destruction
                if (unit.type.equals("cat") && !unit.abilityUsed) {
                    Log.d(TAG, "🐱 Cat teleporting after DIRECT HIT!");
                    executeCatTeleport(unit, cell);
                    return; // Don't destroy the cat
                }

                // Unit destroyed (not cat or ability already used)
                unit.health = 0;
                updateUnitsRemaining(myPlayerKey, -1);
                removeUnitFromFirebase(myPlayerKey, unit.row, unit.col);

                cell.setBackgroundColor(Color.parseColor("#8B4513"));
                cell.setImageResource(R.drawable.explosion_icon);

                ImageView finalCell = cell;
                cell.animate()
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .alpha(0.5f)
                        .rotation(360f)
                        .setDuration(600)
                        .start();

                Toast.makeText(this, "💀 Your " + unit.type + " destroyed!",
                        Toast.LENGTH_LONG).show();
            }
            // ========================================
            // PARTIAL HIT - Damaged
            // ========================================
            else {
                unit.health--; // Reduce HP

                Log.d(TAG, "Unit damaged. HP: 2 → " + unit.health);

                // ✅ CHECK: Did partial hit kill the unit?
                if (unit.health <= 0) {
                    Log.d(TAG, "Unit died from partial hit!");

                    // CAT TELEPORT on partial hit death
                    if (unit.type.equals("cat") && !unit.abilityUsed) {
                        Log.d(TAG, "🐱 Cat teleporting after PARTIAL HIT death!");
                        executeCatTeleport(unit, cell);
                        return; // Don't destroy the cat
                    }

                    // Regular unit destroyed by partial hit
                    unit.health = 0;
                    updateUnitsRemaining(myPlayerKey, -1);
                    removeUnitFromFirebase(opponentPlayerKey, row, col);

                    cell.setBackgroundColor(Color.parseColor("#8B4513")); // Brown
                    cell.setImageResource(R.drawable.explosion_icon);

                    ImageView finalCell = cell;
                    cell.animate()
                            .scaleX(0.5f)
                            .scaleY(0.5f)
                            .alpha(0.5f)
                            .rotation(360f)
                            .setDuration(600)
                            .start();

                    Toast.makeText(this, "💀 Your " + unit.type + " destroyed!",
                            Toast.LENGTH_LONG).show();

                    return; // Exit early - unit is dead
                }

                // Unit survived with 1 HP - show damage
                cell.setBackgroundColor(Color.parseColor("#FFA500")); // Orange

                // Rose color change ability
                if (unit.type.equals("rose") && !unit.abilityUsed) {
                    Log.d(TAG, "🌹 Rose changing color");
                    abilityManager.activateRoseColorChange(unit, cell);
                }

                cell.setImageResource(unit.type.equals("rose") ?
                        abilityManager.getRoseIcon(unit) : getUnitIcon(unit.type));

                ImageView finalCell1 = cell;
                cell.animate()
                        .alpha(0.3f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            finalCell1.animate()
                                    .alpha(1f)
                                    .setDuration(200)
                                    .start();
                        })
                        .start();

                Toast.makeText(this, "🛡️ Your " + unit.type + " damaged! (" + unit.health + " HP)",
                        Toast.LENGTH_LONG).show();

                // Dog fear activation (only if survived)
                if (unit.type.equals("dog") && !unit.abilityUsed) {
                    Log.d(TAG, "🐕 Dog activating fear");

                    ImageView finalCell2 = cell;
                    new Handler().postDelayed(() -> {
                        abilityManager.activateDogFear(unit, finalCell2);
                        saveDogFearToFirebase(unit.row, unit.col);
                    }, 400);
                }
            }
        });
    }

    private void executeCatTeleport(SetupActivity.UnitPosition cat, ImageView oldCellView) {
        final int oldRow = cat.row;
        final int oldCol = cat.col;

        boolean teleported = abilityManager.activateCatTeleport(cat, playerUnits);

        if (!teleported) {
            Log.e(TAG, "❌ Cat teleport FAILED - no empty cells!");

            // Cat dies if can't teleport
            cat.health = 0;
            updateUnitsRemaining(myPlayerKey, -1);

            oldCellView.setBackgroundColor(Color.parseColor("#8B4513"));
            oldCellView.setImageResource(R.drawable.explosion_icon);

            ImageView finalOldCell = oldCellView;
            oldCellView.animate()
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .alpha(0.5f)
                    .rotation(360f)
                    .setDuration(600)
                    .start();

            Toast.makeText(this, "💀 Cat had no escape! Destroyed!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Teleport succeeded!
        final int newRow = cat.row;
        final int newCol = cat.col;
        cat.health = 1; // Cat survives with 1 HP

        Log.d(TAG, "✅ Cat teleported: (" + oldRow + "," + oldCol + ") → (" +
                newRow + "," + newCol + ")");

        // Update MY grid UI - clear old position
        oldCellView.setImageDrawable(null);
        oldCellView.setTag(null);
        oldCellView.setBackgroundColor(Color.parseColor("#8FBC8F")); // Green (empty)

        // Show cat at new position
        ImageView newCell = playerCells[newRow][newCol];
        newCell.setImageResource(R.drawable.cat_icon);
        newCell.setTag(cat);
        newCell.setBackgroundColor(Color.parseColor("#4CAF50")); // Bright green (safe)

        // Teleport animation
        newCell.setScaleX(0.3f);
        newCell.setScaleY(0.3f);
        newCell.setAlpha(0f);
        ImageView finalNewCell = newCell;
        newCell.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .rotation(720f)
                .setDuration(600)
                .setInterpolator(new OvershootInterpolator())
                .start();

        // Flash old position
        ImageView finalOldCellForFlash = oldCellView;
        oldCellView.animate()
                .alpha(0.3f)
                .setDuration(150)
                .withEndAction(() -> {
                    finalOldCellForFlash.animate()
                            .alpha(1f)
                            .setDuration(150)
                            .start();
                })
                .start();

        Toast.makeText(this,
                "🐱 Your cat teleported to (" + newRow + "," + newCol + ")!",
                Toast.LENGTH_LONG).show();

        // Save to Firebase so opponent sees it
        saveCatTeleportToFirebase(oldRow, oldCol, newRow, newCol);
    }

    private void removeDeadEnemyUnit(int row, int col) {
        // Remove from enemyUnits list
        SetupActivity.UnitPosition toRemove = null;
        for (SetupActivity.UnitPosition u : enemyUnits) {
            if (u.row == row && u.col == col) {
                toRemove = u;
                break;
            }
        }
        if (toRemove != null) {
            enemyUnits.remove(toRemove);
            Log.d(TAG, "Removed dead unit from enemyUnits at (" + row + "," + col + ")");
        }
    }

    private void saveDogFearToFirebase(int row, int col) {
        String fearId = myPlayerKey + "_" + row + "_" + col;

        Map<String, Object> fearData = new HashMap<>();
        fearData.put("row", row);
        fearData.put("col", col);
        fearData.put("timestamp", System.currentTimeMillis());

        Log.d(TAG, "Saving dog fear: " + fearId);

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                snapshot.getRef().child("dogFears").child(fearId).setValue(fearData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Dog fear saved: " + fearId);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Dog fear save failed: " + e.getMessage());
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error: " + error.getMessage());
            }
        });
    }

    private void saveCatTeleportToFirebase(int oldRow, int oldCol, int newRow, int newCol) {
        Log.d(TAG, "====================================");
        Log.d(TAG, "SAVING CAT TELEPORT TO FIREBASE");
        Log.d(TAG, "Old: (" + oldRow + "," + oldCol + ")");
        Log.d(TAG, "New: (" + newRow + "," + newCol + ")");
        Log.d(TAG, "====================================");

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot unitsSnapshot = snapshot.child("units").child(myPlayerKey);

                if (!unitsSnapshot.exists()) {
                    Log.e(TAG, "❌ No units found for " + myPlayerKey);
                    snapshot.getRef().removeEventListener(this);
                    return;
                }

                boolean catFound = false;

                // Find the cat by OLD position (since that's what's in Firebase)
                for (DataSnapshot unitSnap : unitsSnapshot.getChildren()) {
                    String type = unitSnap.child("type").getValue(String.class);
                    Integer snapRow = unitSnap.child("row").getValue(Integer.class);
                    Integer snapCol = unitSnap.child("col").getValue(Integer.class);

                    Log.d(TAG, "Checking unit: " + type + " at (" + snapRow + "," + snapCol + ")");

                    // Match by type AND old position
                    if ("cat".equals(type) &&
                            snapRow != null && snapCol != null &&
                            snapRow == oldRow && snapCol == oldCol) {

                        catFound = true;
                        Log.d(TAG, "✅ Found cat at old position! Updating...");

                        // Update to new position
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("row", newRow);
                        updates.put("col", newCol);
                        updates.put("health", 1); // Cat survives with 1 HP
                        updates.put("timestamp", System.currentTimeMillis()); // Track when it moved

                        unitSnap.getRef().updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Cat position updated in Firebase!");
                                    Log.d(TAG, "   New position: (" + newRow + "," + newCol + ")");

                                    // Verify the update
                                    unitSnap.getRef().get().addOnSuccessListener(verifySnap -> {
                                        Integer verifyRow = verifySnap.child("row").getValue(Integer.class);
                                        Integer verifyCol = verifySnap.child("col").getValue(Integer.class);
                                        Log.d(TAG, "   Verified in DB: (" + verifyRow + "," + verifyCol + ")");
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to update cat position: " + e.getMessage());
                                    e.printStackTrace();
                                });

                        break; // Found and updated, stop searching
                    }
                }

                if (!catFound) {
                    Log.e(TAG, "❌ Cat not found at old position (" + oldRow + "," + oldCol + ")");
                    Log.e(TAG, "   This means the cat might have already been updated");
                    Log.e(TAG, "   Or the old position is incorrect");
                }

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "❌ Firebase error saving cat teleport: " + error.getMessage());
            }
        });
    }

    private void sendDuelChoice(int row, int col, int choice, boolean isAttacker) {
        Log.d(TAG, "=== SENDING DUEL CHOICE ===");
        Log.d(TAG, "IsAttacker: " + isAttacker + ", Choice: " + choice);
        Log.d(TAG, "Row: " + row + ", Col: " + col);

        String choiceKey = isAttacker ? "attackerChoice" : "defenderChoice";

        // First update the choice value
        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d(TAG, "Saving " + choiceKey + " = " + choice);

                snapshot.getRef().child("lastAction").child(choiceKey).setValue(choice)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Choice saved successfully: " + choiceKey + " = " + choice);

                            // Verify it was saved
                            snapshot.getRef().child("lastAction").child(choiceKey).get()
                                    .addOnSuccessListener(dataSnapshot -> {
                                        Integer savedValue = dataSnapshot.getValue(Integer.class);
                                        Log.d(TAG, "Verification - " + choiceKey + " in DB: " + savedValue);
                                    });

                            // Then update the action type to trigger listener
                            FirebaseGameRoom.LastActionData choiceAction = new FirebaseGameRoom.LastActionData();
                            choiceAction.type = isAttacker ? "attacker_choice" : "defender_choice";
                            choiceAction.player = myPlayerKey;
                            choiceAction.targetRow = row;
                            choiceAction.targetCol = col;
                            choiceAction.timestamp = System.currentTimeMillis();

                            firebaseManager.sendAction(roomCode, choiceAction);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to save choice: " + e.getMessage());
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error sending choice: " + error.getMessage());
            }
        });

        // Show waiting UI
        runOnUiThread(() -> {
            Toast.makeText(this, "Waiting for opponent's choice...", Toast.LENGTH_LONG).show();
        });
    }

    private void handleMyAttackResult(int row, int col, boolean wasHit) {
        Log.d(TAG, "=== ATTACKER PROCESSING RESULT ===");
        Log.d(TAG, "Hit: " + wasHit + " at (" + row + "," + col + ")");

        waitingForDuelResult = false;
        iAmAttackerInCurrentDuel = false;

        runOnUiThread(() -> {
            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

            if (wasHit) {
                updateUnitsRemaining(opponentPlayerKey, -1);

                // ✅ Remove from local enemyUnits list
                removeDeadEnemyUnit(row, col);

                cell.setBackgroundColor(Color.parseColor("#FF0000"));
                cell.setImageResource(R.drawable.explosion_icon);
                cell.setTag(null); // ✅ Clear tag
                cell.setAlpha(1f);

                ImageView finalCell = cell;
                cell.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .alpha(0.5f)
                        .setDuration(600)
                        .start();

                Toast.makeText(this, "💥 Enemy unit destroyed!", Toast.LENGTH_LONG).show();
            } else {
                // Partial hit - reduce HP in enemyUnits
                for (SetupActivity.UnitPosition u : enemyUnits) {
                    if (u.row == row && u.col == col) {
                        u.health--;
                        Log.d(TAG, "Enemy unit damaged. HP now: " + u.health);

                        // ✅ If died from partial hit
                        if (u.health <= 0) {
                            Log.d(TAG, "Enemy unit died from partial hit!");
                            removeDeadEnemyUnit(row, col);
                            updateUnitsRemaining(opponentPlayerKey, -1);

                            cell.setBackgroundColor(Color.parseColor("#FF0000"));
                            cell.setImageResource(R.drawable.explosion_icon);
                            cell.setTag(null);

                            Toast.makeText(this, "💥 Enemy unit destroyed!", Toast.LENGTH_LONG).show();
                        } else {
                            // Still alive - show damage
                            cell.setBackgroundColor(Color.parseColor("#FFA500"));
                            Toast.makeText(this, "⚡ Enemy unit damaged!", Toast.LENGTH_LONG).show();
                        }
                        break;
                    }
                }

                cell.setAlpha(1f);

                ImageView finalCell = cell;
                cell.animate()
                        .alpha(0.5f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            finalCell.animate().alpha(1f).setDuration(200).start();
                        })
                        .start();
            }
        });

        // End turn after delay
        new Handler().postDelayed(this::endMyTurn, 2000);
    }

    private void clearDuelPending() {
        Log.d(TAG, "Clearing duel pending flag");

        // Create a clear action that resets everything
        FirebaseGameRoom.LastActionData clearAction = new FirebaseGameRoom.LastActionData();
        clearAction.type = "cleared";
        clearAction.player = myPlayerKey;
        clearAction.duelPending = false;
        clearAction.wasHit = false;
        clearAction.timestamp = System.currentTimeMillis();

        firebaseManager.sendAction(roomCode, clearAction);
    }

    private void clearChoices() {
        Log.d(TAG, "=== CLEARING DUEL CHOICES ===");

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Clear all duel-related data
                snapshot.getRef().child("lastAction").child("attackerChoice").removeValue();
                snapshot.getRef().child("lastAction").child("defenderChoice").removeValue();
                snapshot.getRef().child("lastAction").child("attackerSecondChoice").removeValue();
                snapshot.getRef().child("lastAction").child("gardenHoseActive").removeValue();

                // FIX: Also clear duelPending flag
                snapshot.getRef().child("lastAction").child("duelPending").setValue(false);

                Log.d(TAG, "✅ Choices and duel flags cleared");
                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to clear choices: " + error.getMessage());
            }
        });
    }

    private void endMyTurn() {
        Log.d(TAG, "=== ENDING MY TURN ===");
        Log.d(TAG, "My player key: " + myPlayerKey + ", Switching to: " + opponentPlayerKey);

        // ✅ FIX: Reset local state immediately
        waitingForDuelResult = false;
        hasAttackedThisTurn = false;

        String newTurn = opponentPlayerKey;

        // Small delay to ensure choices are cleared
        new Handler().postDelayed(() -> {
            firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // FIX: Update turn
                    snapshot.getRef().child("gameState").child("currentTurn").setValue(newTurn)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "✅ Turn switched to: " + newTurn);

                                // FIX: Increment round if back to player1
                                if ("player1".equals(newTurn)) {
                                    Integer currentRound = snapshot.child("gameState")
                                            .child("currentRound").getValue(Integer.class);
                                    if (currentRound != null) {
                                        int nextRound = currentRound + 1;
                                        snapshot.getRef().child("gameState")
                                                .child("currentRound")
                                                .setValue(nextRound)
                                                .addOnSuccessListener(a -> {
                                                    Log.d(TAG, "✅ Round incremented to: " + nextRound);
                                                });
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "❌ Failed to switch turn: " + e.getMessage());
                            });

                    snapshot.getRef().removeEventListener(this);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to end turn: " + error.getMessage());
                }
            });
        }, 500); // FIX: Increased delay to 500ms to ensure Firebase sync
    }

    private void updateUnitsRemaining(String playerKey, int change) {
        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String unitsKey = playerKey + "UnitsRemaining";
                Integer current = snapshot.child("gameState").child(unitsKey).getValue(Integer.class);

                if (current != null) {
                    snapshot.getRef().child("gameState").child(unitsKey).setValue(current + change);
                }

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateTurnIndicator() {
        runOnUiThread(() -> {
            Log.d(TAG, "updateTurnIndicator - isMyTurn: " + isMyTurn);

            if (isMyTurn) {
                tvTurnIndicator.setText("YOUR TURN - ATTACK!");
                tvTurnIndicator.setTextColor(getColor(android.R.color.holo_green_dark));
                playerGardenSection.setVisibility(View.GONE);
                enemyGardenSection.setVisibility(View.VISIBLE);
            } else {
                tvTurnIndicator.setText("OPPONENT'S TURN");
                tvTurnIndicator.setTextColor(getColor(android.R.color.holo_red_dark));
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
            }

            // ✅ FIX: Update power buttons AFTER turn indicator
            updatePowerButtons();

            logGameState("Turn Indicator Updated");
        });
    }

    private void setEnemyGridClickable(boolean clickable) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                enemyCells[row][col].setClickable(clickable);

                if (!clickable) {
                    enemyCells[row][col].setAlpha(0.5f);
                } else {
                    if (!enemyRevealedCells[row][col]) {
                        enemyCells[row][col].setAlpha(0.8f);
                    } else {
                        enemyCells[row][col].setAlpha(1f);
                    }
                }
            }
        }
    }

    private void showRockFalling(int row, int col) {
        ImageView cell = playerCells[row][col];

        int originalColor = Color.parseColor("#8FBC8F");
        cell.setBackgroundColor(Color.parseColor("#FF6B6B"));

        new Handler().postDelayed(() -> {
            cell.setBackgroundColor(originalColor);
        }, 300);

        cell.animate()
                .translationX(-10f)
                .setDuration(50)
                .withEndAction(() -> {
                    cell.animate().translationX(10f).setDuration(50)
                            .withEndAction(() -> {
                                cell.animate().translationX(-10f).setDuration(50)
                                        .withEndAction(() -> {
                                            cell.animate().translationX(0f).setDuration(50).start();
                                        }).start();
                            }).start();
                }).start();
    }

    private void updateRoundCounter() {
        runOnUiThread(() -> {
            tvRoundCounter.setText("Round: " + currentRound + "/" + MAX_ROUNDS);
        });
    }

    private void updatePowerButtons() {
        Log.d(TAG, "updatePowerButtons called - isMyTurn: " + isMyTurn +
                ", hasAttacked: " + hasAttackedThisTurn +
                ", gardenHoseCooldown: " + powerManager.getGardenHoseCooldown());

        // ✅ FIX: Only disable during opponent's turn OR if already attacked
        boolean canUsePowers = isMyTurn && !hasAttackedThisTurn && !waitingForDuelResult;

        // Garden Hose
        if (powerManager.isGardenHoseActive()) {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\nActive");
            btnGardenHose.setAlpha(0.7f);
        } else if (powerManager.canUseGardenHose() && canUsePowers) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
            btnGardenHose.setAlpha(1.0f);
            Log.d(TAG, "Garden Hose ENABLED");
        } else {
            btnGardenHose.setEnabled(false);
            int cooldown = powerManager.getGardenHoseCooldown();
            btnGardenHose.setText(cooldown > 0 ? "💧\n" + cooldown : "💧\nHose");
            btnGardenHose.setAlpha(0.5f);
            Log.d(TAG, "Garden Hose DISABLED - Cooldown: " + cooldown + ", canUsePowers: " + canUsePowers);
        }

        // Nighttime Relocation
        if (powerManager.canUseNighttimeRelocation() && canUsePowers) {
            btnNighttimeRelocation.setEnabled(true);
            btnNighttimeRelocation.setText("🌙\nMove");
            btnNighttimeRelocation.setAlpha(1.0f);
        } else {
            btnNighttimeRelocation.setEnabled(false);
            int cooldown = powerManager.getNighttimeRelocationCooldown();
            btnNighttimeRelocation.setText(cooldown > 0 ? "🌙\n" + cooldown : "🌙\nMove");
            btnNighttimeRelocation.setAlpha(0.5f);
        }

        // Tier 2 Power
        String icon = "";
        String name = "";
        switch (selectedPower) {
            case "spy_drone":
                icon = "🐝";
                name = "Spy";
                break;
            case "fence_shield":
                icon = "🛡️";
                name = "Fence";
                break;
            case "fertilizer":
                icon = "🌱";
                name = "Heal";
                break;
        }

        if (powerManager.canUseTier2Power() && canUsePowers) {
            btnTier2Power.setEnabled(true);
            btnTier2Power.setText(icon + "\n" + name);
            btnTier2Power.setAlpha(1.0f);
        } else {
            btnTier2Power.setEnabled(false);
            int cooldown = powerManager.getTier2PowerCooldown();
            btnTier2Power.setText(cooldown > 0 ? icon + "\n" + cooldown : icon + "\n" + name);
            btnTier2Power.setAlpha(0.5f);
        }
    }

    private void endGame(boolean iWon) {
        String message = iWon ? "🎉 YOU WIN!" : "💀 YOU LOSE!";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Game Over")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Main Menu", (dialog, which) -> {
                    if (isHost) {
                        firebaseManager.deleteRoom(roomCode);
                    }

                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .show();
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

    // ✅ Helper method to find unit at position
    private SetupActivity.UnitPosition findUnitAtPosition(int row, int col) {
        for (SetupActivity.UnitPosition unit : playerUnits) {
            if (unit.row == row && unit.col == col && unit.health > 0) {
                return unit;
            }
        }
        return null;
    }

    // ✅ Power handlers
    private void onPlayerCellClickedForPower(int row, int col) {
        if (!isSelectingUnitForPower) return;

        SetupActivity.UnitPosition unit = findUnitAtPosition(row, col);

        if (unit == null) {
            Toast.makeText(this, "No unit here!", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (activePowerMode) {
            case "move":
                handleNighttimeRelocation(unit);
                break;
            case "fence":
                handleFenceShield(unit);
                break;
            case "fertilizer":
                handleFertilizer(unit);
                break;
        }
    }

    private void onEnemyCellClickedForPower(int row, int col) {
        if (!isSelectingUnitForPower || !activePowerMode.equals("spy")) return;
        revealAreaWithSpyDrone(row, col);
    }

    private void handleNighttimeRelocation(SetupActivity.UnitPosition unit) {
        // Store original unit for later
        final SetupActivity.UnitPosition selectedUnit = unit;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌙 Move " + unit.type)
                .setMessage("Choose direction:")
                .setPositiveButton("⬆️ Up", (dialog, which) -> {
                    moveUnitAndReturn(selectedUnit, -1, 0);
                })
                .setNegativeButton("⬇️ Down", (dialog, which) -> {
                    moveUnitAndReturn(selectedUnit, 1, 0);
                })
                .setNeutralButton("Cancel", (dialog, which) -> {
                    cancelPowerMode();
                })
                .show();

        // Show second dialog for left/right after a small delay
        new Handler().postDelayed(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🌙 Move " + unit.type)
                    .setMessage("Or choose horizontal:")
                    .setPositiveButton("⬅️ Left", (dialog, which) -> {
                        moveUnitAndReturn(selectedUnit, 0, -1);
                    })
                    .setNegativeButton("➡️ Right", (dialog, which) -> {
                        moveUnitAndReturn(selectedUnit, 0, 1);
                    })
                    .setNeutralButton("Cancel", (dialog, which) -> {
                        cancelPowerMode();
                    })
                    .show();
        }, 100);
    }

    private void moveUnitAndReturn(SetupActivity.UnitPosition unit, int rowOffset, int colOffset) {
        int newRow = unit.row + rowOffset;
        int newCol = unit.col + colOffset;

        // Validate move
        if (newRow < 0 || newRow >= 8 || newCol < 0 || newCol >= 8) {
            Toast.makeText(this, "Can't move outside grid!", Toast.LENGTH_SHORT).show();
            cancelPowerMode();
            return;
        }

        // Check if new position is occupied
        if (findUnitAtPosition(newRow, newCol) != null) {
            Toast.makeText(this, "Cell is occupied!", Toast.LENGTH_SHORT).show();
            cancelPowerMode();
            return;
        }

        // Store old position
        int oldRow = unit.row;
        int oldCol = unit.col;

        // Clear old cell
        ImageView oldCell = playerCells[oldRow][oldCol];
        oldCell.setImageDrawable(null);
        oldCell.setTag(null);
        oldCell.setBackgroundColor(Color.parseColor("#8FBC8F"));

        // Update unit position
        unit.row = newRow;
        unit.col = newCol;

        // Update new cell
        ImageView newCell = playerCells[newRow][newCol];
        newCell.setImageResource(getUnitIcon(unit.type));
        newCell.setTag(unit);

        // Animate move
        newCell.setScaleX(0.3f);
        newCell.setScaleY(0.3f);
        newCell.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .start();

        // Use power
        powerManager.useNighttimeRelocation();

        Toast.makeText(this, "🌙 " + unit.type + " moved from (" + oldRow + "," + oldCol +
                ") to (" + newRow + "," + newCol + ")!", Toast.LENGTH_LONG).show();

        // ✅ Save power usage to Firebase
        FirebaseGameRoom.LastActionData powerAction = new FirebaseGameRoom.LastActionData();
        powerAction.type = "power_used";
        powerAction.player = myPlayerKey;
        powerAction.timestamp = System.currentTimeMillis();

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                snapshot.getRef().child("lastAction").child("powerType").setValue("nighttime_relocation");
                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        firebaseManager.sendAction(roomCode, powerAction);

        // ✅ FIX: Return to enemy garden after move
        cancelPowerMode();
        updatePowerButtons();

        // Switch back to enemy garden
        runOnUiThread(() -> {
            playerGardenSection.setVisibility(View.GONE);
            enemyGardenSection.setVisibility(View.VISIBLE);
            tvTurnIndicator.setText("YOUR TURN - ATTACK!");
            tvTurnIndicator.setTextColor(getColor(android.R.color.holo_green_dark));
        });
    }

    private void moveUnit(SetupActivity.UnitPosition unit, int rowOffset, int colOffset) {
        int newRow = unit.row + rowOffset;
        int newCol = unit.col + colOffset;

        if (newRow < 0 || newRow >= 8 || newCol < 0 || newCol >= 8) {
            Toast.makeText(this, "Can't move outside grid!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (findUnitAtPosition(newRow, newCol) != null) {
            Toast.makeText(this, "Cell is occupied!", Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView oldCell = playerCells[unit.row][unit.col];
        oldCell.setImageDrawable(null);
        oldCell.setTag(null);

        unit.row = newRow;
        unit.col = newCol;

        ImageView newCell = playerCells[newRow][newCol];
        newCell.setImageResource(getUnitIcon(unit.type));
        newCell.setTag(unit);
        newCell.setScaleX(0.3f);
        newCell.setScaleY(0.3f);
        newCell.animate().scaleX(1f).scaleY(1f).setDuration(400).start();

        powerManager.useNighttimeRelocation();
        cancelPowerMode();
        updatePowerButtons();

        Toast.makeText(this, "🌙 Unit moved!", Toast.LENGTH_SHORT).show();
    }

    private void handleFenceShield(SetupActivity.UnitPosition unit) {
        powerManager.setFenceProtectedUnit(unit);
        powerManager.useTier2Power();

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#4DB6AC"));

        cancelPowerMode();
        updatePowerButtons();

        Toast.makeText(this, "🛡️ " + unit.type + " is protected!", Toast.LENGTH_LONG).show();
    }

    private void handleFertilizer(SetupActivity.UnitPosition unit) {
        if (unit.health >= 2) {
            Toast.makeText(this, "Unit is at full health!", Toast.LENGTH_SHORT).show();
            return;
        }

        unit.health = 2;

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#8FBC8F"));
        cell.animate().scaleX(1.2f).scaleY(1.2f).setDuration(200)
                .withEndAction(() -> cell.animate().scaleX(1f).scaleY(1f).setDuration(200).start())
                .start();

        powerManager.useTier2Power();
        cancelPowerMode();
        updatePowerButtons();

        Toast.makeText(this, "🌱 " + unit.type + " healed!", Toast.LENGTH_LONG).show();
    }

    private void revealAreaWithSpyDrone(int centerRow, int centerCol) {
        int revealed = 0;

        for (int row = centerRow - 1; row <= centerRow + 1; row++) {
            for (int col = centerCol - 1; col <= centerCol + 1; col++) {
                if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                    ImageView cell = enemyCells[row][col];

                    if (!enemyRevealedCells[row][col]) {
                        enemyRevealedCells[row][col] = true;
                        revealed++;

                        // ✅ FIX: Check if there's an enemy unit here (now we have the data!)
                        boolean hasUnit = false;
                        for (SetupActivity.UnitPosition unit : enemyUnits) {
                            if (unit.row == row && unit.col == col && unit.health > 0) {
                                // Found a unit! Show it
                                cell.setBackgroundColor(Color.parseColor("#FFE082")); // Yellow
                                cell.setImageResource(getUnitIcon(unit.type));
                                cell.setAlpha(1f);
                                hasUnit = true;

                                Log.d(TAG, "Spy Drone revealed unit: " + unit.type + " at (" + row + "," + col + ")");
                                break;
                            }
                        }

                        if (!hasUnit) {
                            // Empty cell
                            cell.setBackgroundColor(Color.parseColor("#C5E1A5")); // Light green
                            cell.setAlpha(1f);
                        }

                        // Animation
                        cell.animate()
                                .scaleX(1.1f)
                                .scaleY(1.1f)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    cell.animate()
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(200)
                                            .start();
                                })
                                .start();
                    }
                }
            }
        }

        powerManager.useTier2Power();

        Toast.makeText(this, "🐝 Revealed " + revealed + " cells!", Toast.LENGTH_LONG).show();

        // Save to Firebase
        FirebaseGameRoom.LastActionData powerAction = new FirebaseGameRoom.LastActionData();
        powerAction.type = "power_used";
        powerAction.player = myPlayerKey;
        powerAction.timestamp = System.currentTimeMillis();

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                snapshot.getRef().child("lastAction").child("powerType").setValue("spy_drone");
                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        firebaseManager.sendAction(roomCode, powerAction);

        cancelPowerMode();
        updatePowerButtons();
    }

    private void cancelPowerMode() {
        isSelectingUnitForPower = false;
        activePowerMode = null;

        // ✅ FIX: Always return to enemy garden after power mode
        runOnUiThread(() -> {
            if (isMyTurn) {
                playerGardenSection.setVisibility(View.GONE);
                enemyGardenSection.setVisibility(View.VISIBLE);
                tvTurnIndicator.setText("YOUR TURN - ATTACK!");
                tvTurnIndicator.setTextColor(getColor(android.R.color.holo_green_dark));
            }
        });
    }

    private void showMissOnEnemyGrid(int row, int col) {
        runOnUiThread(() -> {
            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

            cell.setBackgroundColor(Color.parseColor("#2196F3")); // Blue
            cell.setImageResource(R.drawable.splash_icon);
            cell.setAlpha(1f);

            Toast.makeText(this, "Miss! Empty cell.", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }
    }

    private void logGameState(String event) {
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "EVENT: " + event);
        Log.d(TAG, "Room: " + roomCode);
        Log.d(TAG, "I am: " + (isHost ? "HOST (Player1)" : "GUEST (Player2)"));
        Log.d(TAG, "My Key: " + myPlayerKey);
        Log.d(TAG, "Opponent Key: " + opponentPlayerKey);
        Log.d(TAG, "Is My Turn: " + isMyTurn);
        Log.d(TAG, "Has Attacked: " + hasAttackedThisTurn);
        Log.d(TAG, "Waiting For Duel: " + waitingForDuelResult);
        Log.d(TAG, "Round: " + currentRound);
        Log.d(TAG, "Player Units: " + playerUnits.size());
        Log.d(TAG, "Enemy Units: " + enemyUnits.size());
        Log.d(TAG, "Garden Hose Active: " + powerManager.isGardenHoseActive());
        Log.d(TAG, "Garden Hose Cooldown: " + powerManager.getGardenHoseCooldown());
        Log.d(TAG, "════════════════════════════════════════");
    }


    private void updateUnitPositionInFirebase(int newRow, int newCol, int health, String unitType) {
        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot unitsSnapshot = snapshot.child("units").child(myPlayerKey);

                // Find the cat unit and update its position
                for (DataSnapshot unitSnapshot : unitsSnapshot.getChildren()) {
                    String type = unitSnapshot.child("type").getValue(String.class);

                    if (unitType.equals(type)) {
                        // Found the unit - update its position
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("row", newRow);
                        updates.put("col", newCol);
                        updates.put("health", health);

                        unitSnapshot.getRef().updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ Unit position updated in Firebase");
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ Failed to update unit position: " + e.getMessage());
                                });

                        break; // Found and updated, exit loop
                    }
                }

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error updating unit position: " + error.getMessage());
            }
        });
    }
}