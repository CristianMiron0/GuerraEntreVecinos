package com.example.guerraentrevecinos;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
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

                // ========================================
                // 1. UPDATE GAME STATE (Turn & Round)
                // ========================================
                DataSnapshot gameStateSnapshot = snapshot.child("gameState");
                String currentTurn = gameStateSnapshot.child("currentTurn").getValue(String.class);
                Integer round = gameStateSnapshot.child("currentRound").getValue(Integer.class);

                Log.d(TAG, "Current turn: " + currentTurn + ", My turn: " + myPlayerKey + ", Round: " + round);

                // Update round
                if (round != null && round != currentRound) {
                    currentRound = round;
                    updateRoundCounter();
                    powerManager.decrementCooldowns();
                    updatePowerButtons();
                }

                // Update turn state
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
                // 2. CHECK FOR PENDING ACTIONS
                // ========================================
                DataSnapshot lastActionSnapshot = snapshot.child("lastAction");
                if (lastActionSnapshot.exists()) {
                    String actionPlayer = lastActionSnapshot.child("player").getValue(String.class);
                    String actionType = lastActionSnapshot.child("type").getValue(String.class);
                    Boolean duelPending = lastActionSnapshot.child("duelPending").getValue(Boolean.class);
                    Long timestamp = lastActionSnapshot.child("timestamp").getValue(Long.class);

                    // Only process new actions (prevent re-processing)
                    if (timestamp != null && timestamp > lastProcessedActionTimestamp) {
                        Log.d(TAG, "NEW Action - player: " + actionPlayer + ", type: " + actionType +
                                ", duelPending: " + duelPending + ", timestamp: " + timestamp);

                        // ========================================
                        // 3. DEFENDER: Opponent attacked me
                        // ========================================
                        if (opponentPlayerKey.equals(actionPlayer) &&
                                "attack".equals(actionType) &&
                                duelPending != null && duelPending) {

                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            // FIX: Read opponent's Garden Hose status
                            Boolean opponentGardenHose = lastActionSnapshot.child("gardenHoseActive").getValue(Boolean.class);
                            boolean opponentUsingGardenHose = (opponentGardenHose != null && opponentGardenHose);

                            if (targetRow != null && targetCol != null) {
                                Log.d(TAG, "I'M DEFENDING! Opponent attacked at (" + targetRow + "," + targetCol + ")");
                                Log.d(TAG, "Opponent using Garden Hose: " + opponentUsingGardenHose);

                                lastProcessedActionTimestamp = timestamp;

                                // Check if there's a unit here
                                final SetupActivity.UnitPosition hitUnit = findUnitAtPosition(targetRow, targetCol);

                                if (hitUnit != null) {
                                    // UNIT HIT - Launch defender mini-duel
                                    Log.d(TAG, "HIT! Unit type: " + hitUnit.type + " - Launching DEFENDER duel");

                                    waitingForDuelResult = true;
                                    iAmAttackerInCurrentDuel = false;
                                    pendingAttackRow = targetRow;
                                    pendingAttackCol = targetCol;
                                    pendingUnitType = hitUnit.type;

                                    showRockFalling(targetRow, targetCol);

                                    // Update action with unit info
                                    FirebaseGameRoom.LastActionData responseAction = new FirebaseGameRoom.LastActionData();
                                    responseAction.type = "duel_ready";
                                    responseAction.player = myPlayerKey;
                                    responseAction.targetRow = targetRow;
                                    responseAction.targetCol = targetCol;
                                    responseAction.wasHit = true;
                                    responseAction.duelPending = true;
                                    responseAction.unitType = hitUnit.type;
                                    responseAction.timestamp = System.currentTimeMillis();

                                    firebaseManager.sendAction(roomCode, responseAction);

                                    // Store values for lambda
                                    final int finalRow = targetRow;
                                    final int finalCol = targetCol;
                                    final String finalUnitType = hitUnit.type;
                                    final boolean finalOpponentGardenHose = opponentUsingGardenHose;

                                    // Launch defender mini-duel after animation
                                    new Handler().postDelayed(() -> {
                                        Log.d(TAG, "Launching DEFENDER mini-duel NOW");

                                        Intent intent = new Intent(MultiplayerBattleActivity.this, MiniDuelActivity.class);
                                        intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, finalUnitType);
                                        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, finalRow);
                                        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, finalCol);
                                        intent.putExtra("IS_PLAYER_ATTACKING", false);
                                        intent.putExtra("GARDEN_HOSE_ACTIVE", finalOpponentGardenHose); // ✅ KEY FIX!
                                        intent.putExtra(MiniDuelActivity.EXTRA_IS_MULTIPLAYER, true);
                                        intent.putExtra(MiniDuelActivity.EXTRA_ROOM_CODE, roomCode);

                                        startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);
                                    }, 1500);

                                } else {
                                    // MISS - No unit here
                                    Log.d(TAG, "MISS! No unit at (" + targetRow + "," + targetCol + ")");

                                    FirebaseGameRoom.LastActionData missAction = new FirebaseGameRoom.LastActionData();
                                    missAction.type = "miss";
                                    missAction.player = myPlayerKey;
                                    missAction.targetRow = targetRow;
                                    missAction.targetCol = targetCol;
                                    missAction.wasHit = false;
                                    missAction.duelPending = false;
                                    missAction.timestamp = System.currentTimeMillis();

                                    firebaseManager.sendAction(roomCode, missAction);
                                }
                            }
                        }

                        // ========================================
                        // 4. ATTACKER: Received response from defender
                        // ========================================
                        else if (opponentPlayerKey.equals(actionPlayer) && "duel_ready".equals(actionType)) {
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);
                            Boolean wasHit = lastActionSnapshot.child("wasHit").getValue(Boolean.class);
                            String unitType = lastActionSnapshot.child("unitType").getValue(String.class);

                            if (wasHit != null && wasHit && targetRow != null && targetCol != null) {
                                Log.d(TAG, "Defender confirmed HIT! Launching ATTACKER duel. Unit: " + unitType);

                                lastProcessedActionTimestamp = timestamp;

                                final int finalRow = targetRow;
                                final int finalCol = targetCol;
                                final String finalUnitType = unitType != null ? unitType : "sunflower";

                                // Launch attacker mini-duel
                                runOnUiThread(() -> {
                                    Log.d(TAG, "Launching ATTACKER mini-duel NOW");
                                    launchMiniDuel(finalRow, finalCol, finalUnitType, true);
                                });
                            }
                        }

                        // ========================================
                        // 5. ATTACKER: Received miss notification
                        // ========================================
                        else if (opponentPlayerKey.equals(actionPlayer) && "miss".equals(actionType)) {
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            Log.d(TAG, "Defender confirmed MISS at (" + targetRow + "," + targetCol + ")");

                            lastProcessedActionTimestamp = timestamp;

                            if (targetRow != null && targetCol != null) {
                                final int finalRow = targetRow;
                                final int finalCol = targetCol;

                                runOnUiThread(() -> showMissOnEnemyGrid(finalRow, finalCol));
                            }

                            waitingForDuelResult = false;
                            new Handler().postDelayed(() -> endMyTurn(), 1500);
                        }

                        // ========================================
                        // 6. BOTH PLAYERS: Check if both choices are in
                        // ========================================
                        if (waitingForDuelResult) {
                            Integer attackerChoice = lastActionSnapshot.child("attackerChoice").getValue(Integer.class);
                            Integer defenderChoice = lastActionSnapshot.child("defenderChoice").getValue(Integer.class);
                            duelPending = lastActionSnapshot.child("duelPending").getValue(Boolean.class);

                            // ✅ FIX: Check for Garden Hose second choice
                            Integer attackerSecond = lastActionSnapshot.child("attackerSecondChoice").getValue(Integer.class);
                            Boolean gardenHoseActive = lastActionSnapshot.child("gardenHoseActive").getValue(Boolean.class);

                            Log.d(TAG, "Checking choices - Attacker: " + attackerChoice +
                                    (attackerSecond != null ? " & " + attackerSecond : "") +
                                    ", Defender: " + defenderChoice +
                                    ", Waiting: " + waitingForDuelResult +
                                    ", GardenHose: " + gardenHoseActive);

                            if (attackerChoice != null && defenderChoice != null &&
                                    (duelPending == null || !duelPending)) {

                                Log.w(TAG, "⚠️ DEADLOCK DETECTED! Both choices exist but not processed. Force clearing...");

                                waitingForDuelResult = false;

                                runOnUiThread(() -> {
                                    Toast.makeText(MultiplayerBattleActivity.this,
                                            "Synchronization issue detected. Continuing game...",
                                            Toast.LENGTH_SHORT).show();

                                    // Force clear and continue
                                    new Handler().postDelayed(() -> {
                                        clearChoices();
                                        if (isMyTurn) {
                                            endMyTurn();
                                        }
                                    }, 1000);
                                });

                                // Only process once
                                if (timestamp != null && timestamp > lastProcessedActionTimestamp) {
                                    lastProcessedActionTimestamp = timestamp;

                                    // ✅ FIX: Calculate result with Garden Hose support
                                    boolean wasHit;

                                    // Check if attacker used Garden Hose (has second choice)
                                    if (attackerSecond != null && gardenHoseActive != null && gardenHoseActive) {
                                        // Garden Hose: Hit if defender matches EITHER choice
                                        wasHit = (attackerChoice.equals(defenderChoice) ||
                                                attackerSecond.equals(defenderChoice));

                                        Log.d(TAG, "🌊 Garden Hose CALCULATION");
                                        Log.d(TAG, "  Attacker Choice 1: " + attackerChoice);
                                        Log.d(TAG, "  Attacker Choice 2: " + attackerSecond);
                                        Log.d(TAG, "  Defender Choice: " + defenderChoice);
                                        Log.d(TAG, "  Match with Choice 1? " + attackerChoice.equals(defenderChoice));
                                        Log.d(TAG, "  Match with Choice 2? " + attackerSecond.equals(defenderChoice));
                                        Log.d(TAG, "  FINAL RESULT: " + (wasHit ? "HIT (DESTROYED)" : "MISS (DAMAGED)"));
                                    } else {
                                        // Normal: Must match exactly
                                        wasHit = attackerChoice.equals(defenderChoice);

                                        Log.d(TAG, "⚔️ Normal CALCULATION");
                                        Log.d(TAG, "  Attacker: " + attackerChoice);
                                        Log.d(TAG, "  Defender: " + defenderChoice);
                                        Log.d(TAG, "  FINAL RESULT: " + (wasHit ? "HIT (DESTROYED)" : "MISS (DAMAGED)"));
                                    }

                                    Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                                    Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                                    Log.d(TAG, "Final result: " + (wasHit ? "HIT" : "MISS") +
                                            " - Processing as " + (iAmAttackerInCurrentDuel ? "ATTACKER" : "DEFENDER"));

                                    if (targetRow != null && targetCol != null) {
                                        if (iAmAttackerInCurrentDuel) {
                                            Log.d(TAG, "Processing result as ATTACKER");
                                            handleMyAttackResult(targetRow, targetCol, wasHit);
                                        } else {
                                            Log.d(TAG, "Processing result as DEFENDER");
                                            handleOpponentAttackResult(targetRow, targetCol, wasHit);
                                        }
                                    }

                                    waitingForDuelResult = false;
                                }
                            }
                        }

                        // ========================================
                        // 7. Listen for opponent power usage
                        // ========================================
                        else if (opponentPlayerKey.equals(actionPlayer) && "power_used".equals(actionType)) {
                            String powerType = lastActionSnapshot.child("powerType").getValue(String.class);

                            if ("spy_drone".equals(powerType)) {
                                // Opponent used spy drone - no visual effect needed on your side
                                Log.d(TAG, "Opponent used Spy Drone");
                            }
                            // Add other power types as needed

                            lastProcessedActionTimestamp = timestamp;
                        }

                    } else {
                        Log.d(TAG, "Ignoring old/duplicate action with timestamp: " + timestamp);
                    }
                }

                // ========================================
                // 8. CHECK WIN CONDITION
                // ========================================
                Integer p1Units = gameStateSnapshot.child("player1UnitsRemaining").getValue(Integer.class);
                Integer p2Units = gameStateSnapshot.child("player2UnitsRemaining").getValue(Integer.class);

                if (p1Units != null && p1Units == 0) {
                    endGame(!isHost); // Player 2 wins
                } else if (p2Units != null && p2Units == 0) {
                    endGame(isHost); // Player 1 wins
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

        // ========================================
        // VALIDATION CHECKS
        // ========================================
        if (!isMyTurn) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hasAttackedThisTurn) {
            Toast.makeText(this, "You already attacked this turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (waitingForDuelResult) {
            Toast.makeText(this, "Wait for current action to complete!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ========================================
        // ✅ FIX: CHECK FOR DOG FEAR BEFORE ATTACKING
        // ========================================
        final int finalRow = row;
        final int finalCol = col;

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if opponent has a dog with fear at this position
                Boolean hasFear = snapshot.child("dogFearUnits")
                        .child(opponentPlayerKey)
                        .child(finalRow + "," + finalCol)
                        .getValue(Boolean.class);

                if (hasFear != null && hasFear) {
                    runOnUiThread(() -> {
                        Toast.makeText(MultiplayerBattleActivity.this,
                                "🐕 Dog's FEAR prevents attack! Choose another target.",
                                Toast.LENGTH_LONG).show();
                    });

                    // Clear the fear after blocking one attack
                    snapshot.getRef().child("dogFearUnits")
                            .child(opponentPlayerKey)
                            .child(finalRow + "," + finalCol)
                            .removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Dog fear cleared after blocking attack");
                            });

                    snapshot.getRef().removeEventListener(this);
                    return;
                }

                // No fear - proceed with normal attack
                snapshot.getRef().removeEventListener(this);
                proceedWithAttack(finalRow, finalCol);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error checking dog fear: " + error.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(MultiplayerBattleActivity.this,
                            "Connection error. Try again.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void proceedWithAttack(int row, int col) {
        Log.d(TAG, "Proceeding with attack at (" + row + "," + col + ")");

        // ========================================
        // MARK ATTACK IN PROGRESS
        // ========================================
        hasAttackedThisTurn = true;
        waitingForDuelResult = true;
        iAmAttackerInCurrentDuel = true;
        setEnemyGridClickable(false);

        pendingAttackRow = row;
        pendingAttackCol = col;

        // Check Garden Hose status BEFORE sending to Firebase
        final boolean usingGardenHose = powerManager.isGardenHoseActive();

        Log.d(TAG, "Sending attack - Garden Hose active: " + usingGardenHose);

        // ========================================
        // CREATE ATTACK ACTION
        // ========================================
        FirebaseGameRoom.LastActionData action = new FirebaseGameRoom.LastActionData();
        action.type = "attack";
        action.player = myPlayerKey;
        action.targetRow = row;
        action.targetCol = col;
        action.wasHit = false; // Will be determined by defender
        action.duelPending = true;
        action.timestamp = System.currentTimeMillis();

        // ========================================
        // SEND TO FIREBASE (with Garden Hose flag)
        // ========================================
        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Write the main action
                snapshot.getRef().child("lastAction").setValue(action.toMap())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Attack action sent successfully");

                            // ✅ FIX: Add Garden Hose flag if active
                            if (usingGardenHose) {
                                snapshot.getRef().child("lastAction")
                                        .child("gardenHoseActive")
                                        .setValue(true)
                                        .addOnSuccessListener(a -> {
                                            Log.d(TAG, "✅ Garden Hose flag added to attack");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "❌ Failed to add Garden Hose flag: " + e.getMessage());
                                        });
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Failed to send attack: " + e.getMessage());

                            runOnUiThread(() -> {
                                Toast.makeText(MultiplayerBattleActivity.this,
                                        "Failed to send attack. Check connection.",
                                        Toast.LENGTH_SHORT).show();

                                // Reset state so player can try again
                                hasAttackedThisTurn = false;
                                waitingForDuelResult = false;
                                setEnemyGridClickable(true);
                            });
                        });

                // Remove listener after single use
                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());

                runOnUiThread(() -> {
                    Toast.makeText(MultiplayerBattleActivity.this,
                            "Connection error: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show();

                    // Reset state
                    hasAttackedThisTurn = false;
                    waitingForDuelResult = false;
                    setEnemyGridClickable(true);
                });
            }
        });

        // Show feedback to player
        Toast.makeText(this, "Attacking (" + row + "," + col + ")..." +
                        (usingGardenHose ? " 💧 Garden Hose!" : ""),
                Toast.LENGTH_SHORT).show();
    }

    // FIX: Pass Garden Hose status to MiniDuel
    private void launchMiniDuel(int row, int col, String unitType, boolean isPlayerAttacking) {
        Log.d(TAG, "Launching mini-duel. Attacking: " + isPlayerAttacking + ", Unit: " + unitType);

        Intent intent = new Intent(this, MiniDuelActivity.class);
        intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, unitType);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, row);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, col);
        intent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);

        // FIXED: Check if Garden Hose is active for THIS duel
        boolean gardenHoseActive = powerManager.isGardenHoseActive();
        intent.putExtra("GARDEN_HOSE_ACTIVE", gardenHoseActive);

        intent.putExtra(MiniDuelActivity.EXTRA_IS_MULTIPLAYER, true);
        intent.putExtra(MiniDuelActivity.EXTRA_ROOM_CODE, roomCode);

        startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);
    }

    // FIX: Deactivate Garden Hose after MiniDuel result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "=== MINI DUEL RESULT RECEIVED ===");
        Log.d(TAG, "Request code: " + requestCode + ", Result code: " + resultCode);

        if (requestCode == REQUEST_CODE_MINI_DUEL && resultCode == RESULT_OK) {
            boolean wasHit = data.getBooleanExtra(MiniDuelActivity.EXTRA_WAS_HIT, false);
            int row = pendingAttackRow;
            int col = pendingAttackCol;

            Log.d(TAG, "Duel result - wasHit: " + wasHit + ", row: " + row + ", col: " + col);
            Log.d(TAG, "I am attacker: " + iAmAttackerInCurrentDuel);

            // FIX: Deactivate Garden Hose after use
            if (powerManager.isGardenHoseActive()) {
                powerManager.deactivateGardenHose();
                updatePowerButtons();
                Log.d(TAG, "Garden Hose deactivated");
            }

            // FIX: Process result based on role
            if (iAmAttackerInCurrentDuel) {
                Log.d(TAG, "Processing as ATTACKER");
                handleMyAttackResult(row, col, wasHit);
            } else {
                Log.d(TAG, "Processing as DEFENDER");
                handleOpponentAttackResult(row, col, wasHit);
            }
        } else {
            Log.w(TAG, "MiniDuel returned unexpected result code: " + resultCode);

            // FIX: Reset state if duel was cancelled or failed
            waitingForDuelResult = false;
            hasAttackedThisTurn = false;
            setEnemyGridClickable(isMyTurn);
        }
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
        Log.d(TAG, "Handling my attack result at (" + row + "," + col + "), wasHit: " + wasHit);

        runOnUiThread(() -> {
            if (wasHit) {
                updateUnitsRemaining(opponentPlayerKey, -1);

                ImageView cell = enemyCells[row][col];
                cell.setBackgroundColor(Color.parseColor("#FF0000"));
                cell.setImageResource(R.drawable.explosion_icon);
                cell.setAlpha(1f);
                enemyRevealedCells[row][col] = true;

                // FIX: Add explosion animation
                cell.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .alpha(0.5f)
                        .setDuration(600)
                        .start();

                Toast.makeText(this, "💥 Direct hit! Enemy unit destroyed!", Toast.LENGTH_LONG).show();
            } else {
                ImageView cell = enemyCells[row][col];
                cell.setBackgroundColor(Color.parseColor("#FFA500"));
                cell.setAlpha(1f);
                enemyRevealedCells[row][col] = true;

                // FIX: Add damage animation
                cell.animate()
                        .alpha(0.5f)
                        .setDuration(200)
                        .withEndAction(() -> {
                            cell.animate().alpha(1f).setDuration(200).start();
                        })
                        .start();

                Toast.makeText(this, "⚡ Partial hit! Enemy unit damaged!", Toast.LENGTH_LONG).show();
            }
        });

        // FIX: Always clear choices and end turn after processing
        Log.d(TAG, "Clearing choices and ending turn in 2 seconds...");
        new Handler().postDelayed(() -> {
            clearChoices();
            endMyTurn();
        }, 2000);
    }

    private void handleOpponentAttackResult(int row, int col, boolean wasHit) {
        Log.d(TAG, "Handling opponent attack result at (" + row + "," + col + "), wasHit: " + wasHit);

        SetupActivity.UnitPosition unit = findUnitAtPosition(row, col);

        runOnUiThread(() -> {
            if (unit != null) {
                // ========================================
                // 1. ✅ CHECK FENCE PROTECTION (Priority 1)
                // ========================================
                if (powerManager.isUnitProtected(unit)) {
                    ImageView cell = playerCells[unit.row][unit.col];
                    cell.setBackgroundColor(Color.parseColor("#8FBC8F")); // Green (protected)

                    // Shield bounce animation
                    cell.animate()
                            .scaleX(1.3f)
                            .scaleY(1.3f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                cell.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(200)
                                        .start();
                            })
                            .start();

                    powerManager.removeFenceProtection();
                    Toast.makeText(this, "🛡️ Fence Shield absorbed the attack!",
                            Toast.LENGTH_LONG).show();

                    // Clear choices and continue
                    new Handler().postDelayed(this::clearChoices, 2000);
                    return; // Exit - unit fully protected
                }

                // ========================================
                // 2. HANDLE DIRECT HIT (Destroyed)
                // ========================================
                if (wasHit) {
                    // ✅ CAT TELEPORT ABILITY (Before destruction)
                    if (unit.type.equals("cat") && !unit.abilityUsed) {
                        Log.d(TAG, "Cat ability triggered - attempting teleport");

                        boolean teleported = abilityManager.activateCatTeleport(
                                unit, playerCells, playerUnits, true);

                        if (teleported) {
                            unit.health = 1; // Cat survives with 1 HP

                            final int newRow = unit.row;
                            final int newCol = unit.col;

                            Toast.makeText(this, "🐱 Cat teleported to safety at (" + newRow + "," + newCol + ")!",
                                    Toast.LENGTH_LONG).show();

                            // ✅ SYNC CAT TELEPORT TO FIREBASE
                            firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    // Save cat's new position
                                    snapshot.getRef().child("catTeleports")
                                            .child(myPlayerKey)
                                            .child("row").setValue(newRow);

                                    snapshot.getRef().child("catTeleports")
                                            .child(myPlayerKey)
                                            .child("col").setValue(newCol);

                                    snapshot.getRef().child("catTeleports")
                                            .child(myPlayerKey)
                                            .child("survived").setValue(true);

                                    snapshot.getRef().removeEventListener(this);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });

                            // Update opponent's view
                            new Handler().postDelayed(this::clearChoices, 2000);
                            return; // Exit - cat escaped
                        }
                    }

                    // Cat didn't teleport or ability already used - DESTROY unit
                    unit.health = 0;
                    updateUnitsRemaining(myPlayerKey, -1);

                    ImageView cell = playerCells[unit.row][unit.col];
                    cell.setBackgroundColor(Color.parseColor("#8B4513")); // Brown (destroyed)
                    cell.setImageResource(R.drawable.explosion_icon);

                    // Destruction animation
                    cell.animate()
                            .scaleX(0.5f)
                            .scaleY(0.5f)
                            .alpha(0.5f)
                            .rotation(360f)
                            .setDuration(600)
                            .start();

                    Toast.makeText(this, "💀 Your " + unit.type + " was destroyed!",
                            Toast.LENGTH_LONG).show();

                }
                // ========================================
                // 3. HANDLE PARTIAL HIT (Damaged)
                // ========================================
                else {
                    unit.health--;
                    ImageView cell = playerCells[unit.row][unit.col];

                    // ✅ ROSE COLOR CHANGE ABILITY (After being damaged)
                    if (unit.type.equals("rose") && !unit.abilityUsed) {
                        Log.d(TAG, "Rose ability triggered - changing color");
                        abilityManager.activateRoseColorChange(unit, cell);
                    }

                    // Set cell to orange (damaged)
                    cell.setBackgroundColor(Color.parseColor("#FFA500")); // Orange

                    // Update icon (important for rose - it might have changed color)
                    if (unit.type.equals("rose")) {
                        cell.setImageResource(abilityManager.getRoseIcon(unit));
                    } else {
                        cell.setImageResource(getUnitIcon(unit.type));
                    }

                    // Damage flash animation
                    cell.animate()
                            .alpha(0.3f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                cell.animate()
                                        .alpha(1f)
                                        .setDuration(200)
                                        .start();
                            })
                            .start();

                    Toast.makeText(this, "🛡️ Your " + unit.type + " was damaged! (1 HP left)",
                            Toast.LENGTH_LONG).show();

                    // ✅ DOG FEAR FIX: Activate and sync to Firebase
                    if (unit.type.equals("dog") && unit.health == 1 && !unit.abilityUsed) {
                        Log.d(TAG, "Dog ability triggered - activating fear");

                        // Activate locally
                        abilityManager.activateDogFear(unit, cell);

                        // ✅ SYNC TO FIREBASE: Tell opponent this dog has fear active
                        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                // Save dog fear state
                                snapshot.getRef().child("dogFearUnits")
                                        .child(opponentPlayerKey) // My units from opponent's perspective
                                        .child(unit.row + "," + unit.col)
                                        .setValue(true);

                                snapshot.getRef().removeEventListener(this);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    }
                }
            } else {
                Log.w(TAG, "No unit found at (" + row + "," + col + ") - this shouldn't happen");
            }
        });

        // Clear choices after processing
        new Handler().postDelayed(this::clearChoices, 2000);
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
}