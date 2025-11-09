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
import java.util.List;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        Log.d(TAG, "onCreate started");

        // Get data
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        selectedPower = getIntent().getStringExtra("SELECTED_POWER");
        playerUnits = getIntent().getParcelableArrayListExtra("PLAYER_UNITS");
        enemyUnits = new ArrayList<>();

        myPlayerKey = isHost ? "player1" : "player2";
        opponentPlayerKey = isHost ? "player2" : "player1";

        Log.d(TAG, "Room: " + roomCode + ", isHost: " + isHost + ", myKey: " + myPlayerKey);

        // Initialize managers
        firebaseManager = FirebaseManager.getInstance();
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

        // Listen for game updates
        listenForGameUpdates();
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

        updatePowerButtons();
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
                cell.setOnClickListener(v -> onEnemyCellClicked(finalRow, finalCol));

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

                // Get game state
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

                // Check for pending action
                DataSnapshot lastActionSnapshot = snapshot.child("lastAction");
                if (lastActionSnapshot.exists()) {
                    String actionPlayer = lastActionSnapshot.child("player").getValue(String.class);
                    String actionType = lastActionSnapshot.child("type").getValue(String.class);
                    Boolean duelPending = lastActionSnapshot.child("duelPending").getValue(Boolean.class);
                    Long timestamp = lastActionSnapshot.child("timestamp").getValue(Long.class);

                    // Only process new actions (prevent re-processing same action)
                    if (timestamp != null && timestamp > lastProcessedActionTimestamp) {
                        Log.d(TAG, "NEW Action - player: " + actionPlayer + ", type: " + actionType +
                                ", duelPending: " + duelPending + ", timestamp: " + timestamp);

                        // DEFENDER: Opponent attacked me
                        if (opponentPlayerKey.equals(actionPlayer) &&
                                "attack".equals(actionType) &&
                                duelPending != null && duelPending) {

                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            if (targetRow != null && targetCol != null) {
                                Log.d(TAG, "I'M DEFENDING! Opponent attacked at (" + targetRow + "," + targetCol + ")");

                                lastProcessedActionTimestamp = timestamp;

                                // Check if there's actually a unit here
                                // ✅ FIX: Use final reference for lambda
                                final SetupActivity.UnitPosition hitUnit = findUnitAtPosition(targetRow, targetCol);

                                if (hitUnit != null) {
                                    // UNIT HIT - Launch defender mini-duel
                                    Log.d(TAG, "HIT! Unit type: " + hitUnit.type + " - Launching DEFENDER duel");

                                    waitingForDuelResult = true;
                                    iAmAttackerInCurrentDuel = false; // Mark that I'm defending
                                    pendingAttackRow = targetRow;
                                    pendingAttackCol = targetCol;
                                    pendingUnitType = hitUnit.type;

                                    showRockFalling(targetRow, targetCol);

                                    // Update action to indicate unit was found and include unit type
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

                                    // ✅ FIX: Use final variables in lambda
                                    final int finalRow = targetRow;
                                    final int finalCol = targetCol;
                                    final String finalUnitType = hitUnit.type;

                                    // Launch defender mini-duel after animation
                                    new Handler().postDelayed(() -> {
                                        Log.d(TAG, "Launching DEFENDER mini-duel NOW");
                                        launchMiniDuel(finalRow, finalCol, finalUnitType, false);
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

                        // ATTACKER: Received response from defender
                        else if (opponentPlayerKey.equals(actionPlayer) && "duel_ready".equals(actionType)) {
                            // Defender confirmed there's a unit - launch attacker duel
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);
                            Boolean wasHit = lastActionSnapshot.child("wasHit").getValue(Boolean.class);
                            String unitType = lastActionSnapshot.child("unitType").getValue(String.class);

                            if (wasHit != null && wasHit && targetRow != null && targetCol != null) {
                                Log.d(TAG, "Defender confirmed HIT! Launching ATTACKER duel. Unit: " + unitType);

                                lastProcessedActionTimestamp = timestamp;

                                // ✅ FIX: Use final variables for lambda
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

                        // ATTACKER: Received miss notification from defender
                        else if (opponentPlayerKey.equals(actionPlayer) && "miss".equals(actionType)) {
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                            Log.d(TAG, "Defender confirmed MISS at (" + targetRow + "," + targetCol + ")");

                            lastProcessedActionTimestamp = timestamp;

                            // Show miss
                            if (targetRow != null && targetCol != null) {
                                final int finalRow = targetRow;
                                final int finalCol = targetCol;

                                runOnUiThread(() -> showMissOnEnemyGrid(finalRow, finalCol));
                            }

                            waitingForDuelResult = false;

                            // End turn
                            new Handler().postDelayed(() -> endMyTurn(), 1500);
                        }

                        // BOTH PLAYERS: Check if both choices are in
                        if (waitingForDuelResult) {
                            // Always check for both choices when waiting
                            Integer attackerChoice = lastActionSnapshot.child("attackerChoice").getValue(Integer.class);
                            Integer defenderChoice = lastActionSnapshot.child("defenderChoice").getValue(Integer.class);

                            Log.d(TAG, "Checking choices - Attacker: " + attackerChoice +
                                    ", Defender: " + defenderChoice + ", Waiting: " + waitingForDuelResult);

                            if (attackerChoice != null && defenderChoice != null) {
                                Log.d(TAG, "✅ BOTH CHOICES RECEIVED! Attacker: " + attackerChoice +
                                        ", Defender: " + defenderChoice);

                                // Only process once
                                if (timestamp != null && timestamp > lastProcessedActionTimestamp) {
                                    lastProcessedActionTimestamp = timestamp;

                                    // Calculate result
                                    boolean wasHit = attackerChoice.equals(defenderChoice);

                                    Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                                    Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);

                                    Log.d(TAG, "Choices match: " + wasHit + " - Processing result as " +
                                            (iAmAttackerInCurrentDuel ? "ATTACKER" : "DEFENDER"));

                                    if (targetRow != null && targetCol != null) {
                                        // Use the role we stored when duel started
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
                    } else {
                        Log.d(TAG, "Ignoring old/duplicate action with timestamp: " + timestamp);
                    }
                }

                // Check win condition
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
            Toast.makeText(this, "Wait for current action to complete!", Toast.LENGTH_SHORT).show();
            return;
        }

        hasAttackedThisTurn = true;
        waitingForDuelResult = true;
        iAmAttackerInCurrentDuel = true; // Mark that I'm attacking
        setEnemyGridClickable(false);

        pendingAttackRow = row;
        pendingAttackCol = col;

        // Send attack action to Firebase - defender will check if there's a unit
        FirebaseGameRoom.LastActionData action = new FirebaseGameRoom.LastActionData();
        action.type = "attack";
        action.player = myPlayerKey;
        action.targetRow = row;
        action.targetCol = col;
        action.wasHit = false; // Will be determined by defender
        action.duelPending = true;
        action.timestamp = System.currentTimeMillis();

        firebaseManager.sendAction(roomCode, action);

        Log.d(TAG, "Attack sent to Firebase: (" + row + "," + col + ") - waiting for defender response");

        // Show attacking animation
        Toast.makeText(this, "Attacking (" + row + "," + col + ")...", Toast.LENGTH_SHORT).show();

        // Don't launch mini-duel yet - wait for defender to respond
    }

    private void launchMiniDuel(int row, int col, String unitType, boolean isPlayerAttacking) {
        Log.d(TAG, "Launching mini-duel. Attacking: " + isPlayerAttacking + ", Unit: " + unitType);

        Intent intent = new Intent(this, MiniDuelActivity.class);
        intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, unitType);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, row);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, col);
        intent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);
        intent.putExtra("GARDEN_HOSE_ACTIVE", powerManager.isGardenHoseActive());
        intent.putExtra(MiniDuelActivity.EXTRA_IS_MULTIPLAYER, true);
        intent.putExtra(MiniDuelActivity.EXTRA_ROOM_CODE, roomCode); // ✅ Pass room code
        startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MINI_DUEL && resultCode == RESULT_OK) {
            // ✅ Only handle final result - MiniDuel handles saving choices itself
            boolean wasHit = data.getBooleanExtra(MiniDuelActivity.EXTRA_WAS_HIT, false);
            int row = pendingAttackRow;
            int col = pendingAttackCol;

            Log.d(TAG, "Received FINAL result from mini-duel - wasHit: " + wasHit);

            if (iAmAttackerInCurrentDuel) {
                handleMyAttackResult(row, col, wasHit);
            } else {
                handleOpponentAttackResult(row, col, wasHit);
            }

            waitingForDuelResult = false;
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
        Log.d(TAG, "Handling my attack result at (" + row + "," + col + "), wasHit: " + wasHit);

        runOnUiThread(() -> {
            if (wasHit) {
                updateUnitsRemaining(opponentPlayerKey, -1);

                ImageView cell = enemyCells[row][col];
                cell.setBackgroundColor(Color.parseColor("#FF0000"));
                cell.setImageResource(R.drawable.explosion_icon);
                cell.setAlpha(1f);
                enemyRevealedCells[row][col] = true;

                Toast.makeText(this, "💥 Direct hit! Enemy unit destroyed!", Toast.LENGTH_LONG).show();
            } else {
                ImageView cell = enemyCells[row][col];
                cell.setBackgroundColor(Color.parseColor("#FFA500"));
                cell.setAlpha(1f);
                enemyRevealedCells[row][col] = true;

                Toast.makeText(this, "⚡ Partial hit! Enemy unit damaged!", Toast.LENGTH_LONG).show();
            }
        });

        // Clear choices and end turn
        new Handler().postDelayed(() -> {
            clearChoices();
            endMyTurn();
        }, 2000);
    }

    private void handleOpponentAttackResult(int row, int col, boolean wasHit) {
        Log.d(TAG, "Handling opponent attack result at (" + row + "," + col + "), wasHit: " + wasHit);

        // ✅ FIX: Use helper method for final reference
        SetupActivity.UnitPosition unit = findUnitAtPosition(row, col);

        runOnUiThread(() -> {
            if (unit != null) {
                if (wasHit) {
                    unit.health = 0;
                    updateUnitsRemaining(myPlayerKey, -1);

                    ImageView cell = playerCells[unit.row][unit.col];
                    cell.setBackgroundColor(Color.parseColor("#8B4513"));
                    cell.setImageResource(R.drawable.explosion_icon);

                    cell.animate()
                            .scaleX(0.5f)
                            .scaleY(0.5f)
                            .alpha(0.5f)
                            .setDuration(600)
                            .start();

                    Toast.makeText(this, "💀 Your unit was destroyed!", Toast.LENGTH_LONG).show();
                } else {
                    unit.health--;

                    ImageView cell = playerCells[unit.row][unit.col];
                    cell.setBackgroundColor(Color.parseColor("#FFA500"));

                    Toast.makeText(this, "🛡️ Your unit was damaged!", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Clear choices
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
        Log.d(TAG, "Clearing choices from Firebase");

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Clear both choices
                snapshot.getRef().child("lastAction").child("attackerChoice").removeValue();
                snapshot.getRef().child("lastAction").child("defenderChoice").removeValue();

                Log.d(TAG, "Choices cleared");
                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to clear choices: " + error.getMessage());
            }
        });
    }

    private void endMyTurn() {
        Log.d(TAG, "Ending my turn");

        // Clear action first
        clearDuelPending();

        String newTurn = opponentPlayerKey;

        // Small delay to ensure action is cleared
        new Handler().postDelayed(() -> {
            // Update current turn in Firebase
            firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    snapshot.getRef().child("gameState").child("currentTurn").setValue(newTurn);

                    // Increment round if back to player1
                    if ("player1".equals(newTurn)) {
                        Integer currentRound = snapshot.child("gameState").child("currentRound").getValue(Integer.class);
                        if (currentRound != null) {
                            snapshot.getRef().child("gameState").child("currentRound").setValue(currentRound + 1);
                        }
                    }

                    snapshot.getRef().removeEventListener(this);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to end turn: " + error.getMessage());
                }
            });
        }, 300);
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
        // Implementation similar to BattleActivity
        if (powerManager.canUseGardenHose()) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
        } else {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\n" + powerManager.getGardenHoseCooldown());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }
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
}