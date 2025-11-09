package com.example.guerraentrevecinos;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.FirebaseGameRoom;
import com.example.guerraentrevecinos.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class MultiplayerBattleActivity extends AppCompatActivity {

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
    private boolean isHost; // Player 1 is host
    private String myPlayerKey; // "player1" or "player2"
    private String opponentPlayerKey;
    private ValueEventListener roomListener;

    // Powers
    private PowerManager powerManager;
    private String selectedPower;
    private boolean isSelectingUnitForPower = false;
    private String activePowerMode = null; // "move", "fence", "fertilizer", "spy"

    // Abilities
    private AbilityManager abilityManager;
    private SetupActivity.UnitPosition lastAttackedPlayerUnit = null;

    // Pending action (waiting for mini-duel)
    private int pendingAttackRow = -1;
    private int pendingAttackCol = -1;
    private boolean isAttacker = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        // Get data
        roomCode = getIntent().getStringExtra("ROOM_CODE");
        isHost = getIntent().getBooleanExtra("IS_HOST", false);
        selectedPower = getIntent().getStringExtra("SELECTED_POWER");
        playerUnits = getIntent().getParcelableArrayListExtra("PLAYER_UNITS");
        enemyUnits = new ArrayList<>(); // Will be synced from Firebase

        myPlayerKey = isHost ? "player1" : "player2";
        opponentPlayerKey = isHost ? "player2" : "player1";

        // Initialize managers
        firebaseManager = FirebaseManager.getInstance();
        powerManager = new PowerManager(selectedPower != null ? selectedPower : "spy_drone");
        abilityManager = new AbilityManager(this);

        // Initialize views
        initializeViews();

        // Setup power buttons
        setupPowerButtons();

        // Create grids
        createPlayerGrid();
        createEnemyGrid();

        // Initialize game state in Firebase
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

        // Set Tier 2 power icon
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

        // ✅ Garden Hose click
        btnGardenHose.setOnClickListener(v -> {
            if (powerManager.canUseGardenHose()) {
                powerManager.activateGardenHose();
                Toast.makeText(this, "💧 Garden Hose activated! Pick 2 numbers in next duel.",
                        Toast.LENGTH_LONG).show();
                updatePowerButtons();
            }
        });

        // ✅ Nighttime Relocation click
        btnNighttimeRelocation.setOnClickListener(v -> {
            if (powerManager.canUseNighttimeRelocation()) {
                activateUnitSelectionMode("move");
            }
        });

        // ✅ Tier 2 Power click
        btnTier2Power.setOnClickListener(v -> {
            if (powerManager.canUseTier2Power()) {
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

    private void activateUnitSelectionMode(String powerMode) {
        isSelectingUnitForPower = true;
        activePowerMode = powerMode;

        String message = "";
        switch (powerMode) {
            case "move":
                message = "🌙 Select a unit to move to adjacent cell";
                break;
            case "spy":
                message = "🐝 Click on enemy grid to reveal 3x3 area";
                break;
            case "fence":
                message = "🛡️ Select a unit to protect";
                break;
            case "fertilizer":
                message = "🌱 Select a wounded unit to heal";
                break;
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        tvTurnIndicator.setText(message);
    }

    private void createPlayerGrid() {
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

                // Show player units
                for (SetupActivity.UnitPosition unit : playerUnits) {
                    if (unit.row == row && unit.col == col) {
                        cell.setImageResource(getUnitIcon(unit.type));
                        cell.setTag(unit);
                        break;
                    }
                }

                playerCells[row][col] = cell;

                // ✅ Click listener for powers
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
        FirebaseGameRoom.GameStateData gameState = new FirebaseGameRoom.GameStateData();
        gameState.currentRound = 1;
        gameState.currentTurn = "player1"; // Host goes first
        gameState.player1UnitsRemaining = 7;
        gameState.player2UnitsRemaining = 7;

        firebaseManager.updateGameState(roomCode, gameState);

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if (!"playing".equals(status)) {
                    snapshot.getRef().child("status").setValue("playing");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenForGameUpdates() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(MultiplayerBattleActivity.this,
                            "Game ended", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                DataSnapshot gameStateSnapshot = snapshot.child("gameState");
                String currentTurn = gameStateSnapshot.child("currentTurn").getValue(String.class);
                Integer round = gameStateSnapshot.child("currentRound").getValue(Integer.class);

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
                    updateTurnIndicator();
                    setEnemyGridClickable(isMyTurn);
                }

                DataSnapshot lastActionSnapshot = snapshot.child("lastAction");
                if (lastActionSnapshot.exists()) {
                    String actionPlayer = lastActionSnapshot.child("player").getValue(String.class);

                    if (opponentPlayerKey.equals(actionPlayer)) {
                        Boolean duelPending = lastActionSnapshot.child("duelPending").getValue(Boolean.class);

                        if (duelPending != null && duelPending) {
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);
                            Boolean wasHit = lastActionSnapshot.child("wasHit").getValue(Boolean.class);

                            if (targetRow != null && targetCol != null && wasHit != null && wasHit) {
                                SetupActivity.UnitPosition hitUnit = null;
                                for (SetupActivity.UnitPosition unit : playerUnits) {
                                    if (unit.row == targetRow && unit.col == targetCol && unit.health > 0) {
                                        hitUnit = unit;
                                        break;
                                    }
                                }

                                if (hitUnit != null) {
                                    pendingAttackRow = targetRow;
                                    pendingAttackCol = targetCol;
                                    isAttacker = false;

                                    showRockFalling(targetRow, targetCol);

                                    SetupActivity.UnitPosition finalHitUnit = hitUnit;
                                    new Handler().postDelayed(() -> {
                                        launchMiniDuel(targetRow, targetCol, finalHitUnit.type, false);
                                    }, 1500);

                                    snapshot.getRef().child("lastAction").child("duelPending").setValue(false);
                                }
                            }
                        }
                    }
                }

                Integer p1Units = gameStateSnapshot.child("player1UnitsRemaining").getValue(Integer.class);
                Integer p2Units = gameStateSnapshot.child("player2UnitsRemaining").getValue(Integer.class);

                if (p1Units != null && p1Units == 0) {
                    endGame(false);
                } else if (p2Units != null && p2Units == 0) {
                    endGame(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MultiplayerBattleActivity.this,
                        "Connection error", Toast.LENGTH_SHORT).show();
            }
        };

        firebaseManager.listenToRoom(roomCode, roomListener);
    }

    private void onEnemyCellClicked(int row, int col) {
        if (!isMyTurn) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hasAttackedThisTurn) {
            Toast.makeText(this, "You already attacked this turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        hasAttackedThisTurn = true;
        setEnemyGridClickable(false);

        FirebaseGameRoom.LastActionData action = new FirebaseGameRoom.LastActionData();
        action.type = "attack";
        action.player = myPlayerKey;
        action.targetRow = row;
        action.targetCol = col;
        action.wasHit = true;
        action.duelPending = true;
        action.timestamp = System.currentTimeMillis();

        firebaseManager.sendAction(roomCode, action);

        pendingAttackRow = row;
        pendingAttackCol = col;
        isAttacker = true;

        new Handler().postDelayed(() -> {
            Toast.makeText(this, "Attacking (" + row + "," + col + ")...",
                    Toast.LENGTH_SHORT).show();
        }, 500);
    }

    private void launchMiniDuel(int row, int col, String unitType, boolean isPlayerAttacking) {
        Intent intent = new Intent(this, MiniDuelActivity.class);
        intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, unitType);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, row);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, col);
        intent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);
        intent.putExtra("GARDEN_HOSE_ACTIVE", powerManager.isGardenHoseActive());
        startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MINI_DUEL && resultCode == RESULT_OK) {
            boolean wasHit = data.getBooleanExtra(MiniDuelActivity.EXTRA_WAS_HIT, false);

            if (isAttacker) {
                if (wasHit) {
                    updateUnitsRemaining(opponentPlayerKey, -1);

                    ImageView cell = enemyCells[pendingAttackRow][pendingAttackCol];
                    cell.setBackgroundColor(Color.parseColor("#FF0000"));
                    cell.setImageResource(R.drawable.explosion_icon);
                    cell.setAlpha(1f);
                    enemyRevealedCells[pendingAttackRow][pendingAttackCol] = true;

                    Toast.makeText(this, "Enemy unit destroyed!", Toast.LENGTH_SHORT).show();
                } else {
                    ImageView cell = enemyCells[pendingAttackRow][pendingAttackCol];
                    cell.setBackgroundColor(Color.parseColor("#FFA500"));
                    cell.setAlpha(1f);
                    enemyRevealedCells[pendingAttackRow][pendingAttackCol] = true;

                    Toast.makeText(this, "Enemy unit damaged!", Toast.LENGTH_SHORT).show();
                }

                endMyTurn();

            } else {
                SetupActivity.UnitPosition unit = null;
                for (SetupActivity.UnitPosition u : playerUnits) {
                    if (u.row == pendingAttackRow && u.col == pendingAttackCol && u.health > 0) {
                        unit = u;
                        break;
                    }
                }

                if (unit != null) {
                    if (wasHit) {
                        // ✅ Check if cat and teleport
                        if (unit.type.equals("cat") && !unit.abilityUsed) {
                            boolean teleported = abilityManager.activateCatTeleport(
                                    unit, playerCells, playerUnits, true);
                            if (teleported) {
                                unit.health = 1;
                                Toast.makeText(this, "Your cat survived by teleporting!", Toast.LENGTH_LONG).show();
                                return;
                            }
                        }

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

                        Toast.makeText(this, "Your unit was destroyed!", Toast.LENGTH_LONG).show();
                    } else {
                        // ✅ Check fence shield
                        if (powerManager.isUnitProtected(unit)) {
                            ImageView cell = playerCells[unit.row][unit.col];
                            cell.setBackgroundColor(Color.parseColor("#8FBC8F"));

                            cell.animate()
                                    .scaleX(1.3f)
                                    .scaleY(1.3f)
                                    .setDuration(200)
                                    .withEndAction(() -> {
                                        cell.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                                    })
                                    .start();

                            powerManager.removeFenceProtection();
                            Toast.makeText(this, "🛡️ Fence Shield absorbed the attack!", Toast.LENGTH_LONG).show();
                            return;
                        }

                        unit.health--;
                        lastAttackedPlayerUnit = unit;

                        ImageView cell = playerCells[unit.row][unit.col];

                        // ✅ Check if rose and activate color change
                        if (unit.type.equals("rose") && !unit.abilityUsed) {
                            abilityManager.activateRoseColorChange(unit, cell);
                        }

                        if (unit.health == 1) {
                            cell.setBackgroundColor(Color.parseColor("#FFA500"));

                            if (unit.type.equals("rose")) {
                                cell.setImageResource(abilityManager.getRoseIcon(unit));
                            } else {
                                cell.setImageResource(getUnitIcon(unit.type));
                            }

                            Toast.makeText(this, "Your unit was damaged!", Toast.LENGTH_LONG).show();

                            // ✅ Check if dog and activate fear
                            if (unit.type.equals("dog") && !unit.abilityUsed) {
                                abilityManager.activateDogFear(unit, cell);
                            }
                        }
                    }
                }
            }
        }
    }

    // ✅ Handle player grid clicks for powers
    private void onPlayerCellClickedForPower(int row, int col) {
        if (!isSelectingUnitForPower) return;

        ImageView cell = playerCells[row][col];
        SetupActivity.UnitPosition unit = null;

        for (SetupActivity.UnitPosition u : playerUnits) {
            if (u.row == row && u.col == col && u.health > 0) {
                unit = u;
                break;
            }
        }

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

    private void handleNighttimeRelocation(SetupActivity.UnitPosition unit) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌙 Move " + unit.type)
                .setMessage("Choose direction:")
                .setPositiveButton("⬆️ Up", (dialog, which) -> moveUnit(unit, -1, 0))
                .setNeutralButton("⬇️ Down", (dialog, which) -> moveUnit(unit, 1, 0))
                .setNegativeButton("⬅️ Left", (dialog, which) -> moveUnit(unit, 0, -1))
                .create()
                .show();

        new Handler().postDelayed(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🌙 Move " + unit.type)
                    .setMessage("Or:")
                    .setPositiveButton("➡️ Right", (dialog, which) -> moveUnit(unit, 0, 1))
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        }, 100);
    }

    private void moveUnit(SetupActivity.UnitPosition unit, int rowOffset, int colOffset) {
        int newRow = unit.row + rowOffset;
        int newCol = unit.col + colOffset;

        if (newRow < 0 || newRow >= 8 || newCol < 0 || newCol >= 8) {
            Toast.makeText(this, "Can't move outside the grid!", Toast.LENGTH_SHORT).show();
            return;
        }

        for (SetupActivity.UnitPosition u : playerUnits) {
            if (u.row == newRow && u.col == newCol && u.health > 0) {
                Toast.makeText(this, "Cell is occupied!", Toast.LENGTH_SHORT).show();
                return;
            }
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
        newCell.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .start();

        powerManager.useNighttimeRelocation();

        isSelectingUnitForPower = false;
        activePowerMode = null;
        tvTurnIndicator.setText(isMyTurn ? "YOUR TURN - ATTACK!" : "OPPONENT'S TURN");
        updatePowerButtons();

        Toast.makeText(this, "🌙 Unit moved successfully!", Toast.LENGTH_SHORT).show();
    }

    private void handleFenceShield(SetupActivity.UnitPosition unit) {
        powerManager.setFenceProtectedUnit(unit);
        powerManager.useTier2Power();

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(android.graphics.Color.parseColor("#4DB6AC"));

        Toast.makeText(this, "🛡️ " + unit.type + " is now protected!", Toast.LENGTH_LONG).show();

        isSelectingUnitForPower = false;
        activePowerMode = null;
        tvTurnIndicator.setText(isMyTurn ? "YOUR TURN - ATTACK!" : "OPPONENT'S TURN");
        updatePowerButtons();
    }

    private void handleFertilizer(SetupActivity.UnitPosition unit) {
        if (unit.health >= 2) {
            Toast.makeText(this, "Unit is already at full health!", Toast.LENGTH_SHORT).show();
            return;
        }

        unit.health = 2;

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(android.graphics.Color.parseColor("#8FBC8F"));

        cell.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction(() -> {
                    cell.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        powerManager.useTier2Power();

        Toast.makeText(this, "🌱 " + unit.type + " healed to full HP!", Toast.LENGTH_LONG).show();

        isSelectingUnitForPower = false;
        activePowerMode = null;
        tvTurnIndicator.setText(isMyTurn ? "YOUR TURN - ATTACK!" : "OPPONENT'S TURN");
        updatePowerButtons();
    }

    private void onEnemyCellClickedForPower(int row, int col) {
        if (!isSelectingUnitForPower || !activePowerMode.equals("spy")) return;

        revealAreaWithSpyDrone(row, col);
    }

    private void revealAreaWithSpyDrone(int centerRow, int centerCol) {
        int revealed = 0;

        for (int row = centerRow - 1; row <= centerRow + 1; row++) {
            for (int col = centerCol - 1; col <= centerCol + 1; col++) {
                if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                    ImageView cell = enemyCells[row][col];

                    if (!enemyRevealedCells[row][col]) {
                        enemyRevealedCells[row][col] = true;
                        cell.setBackgroundColor(android.graphics.Color.parseColor("#C5E1A5"));
                        cell.setAlpha(1f);
                        revealed++;
                    }
                }
            }
        }

        powerManager.useTier2Power();

        Toast.makeText(this, "🐝 Revealed " + revealed + " cells!", Toast.LENGTH_LONG).show();

        isSelectingUnitForPower = false;
        activePowerMode = null;
        tvTurnIndicator.setText(isMyTurn ? "YOUR TURN - ATTACK!" : "OPPONENT'S TURN");
        updatePowerButtons();
    }

    private void endMyTurn() {
        String newTurn = opponentPlayerKey;

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                snapshot.getRef().child("gameState").child("currentTurn").setValue(newTurn);

                if ("player1".equals(newTurn)) {
                    Integer currentRound = snapshot.child("gameState").child("currentRound").getValue(Integer.class);
                    if (currentRound != null) {
                        snapshot.getRef().child("gameState").child("currentRound").setValue(currentRound + 1);
                    }
                }

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
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
        tvRoundCounter.setText("Round: " + currentRound + "/" + MAX_ROUNDS);
    }

    private void updatePowerButtons() {
        if (powerManager.canUseGardenHose()) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
        } else {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\n" + powerManager.getGardenHoseCooldown());
        }

        if (powerManager.canUseNighttimeRelocation()) {
            btnNighttimeRelocation.setEnabled(true);
            btnNighttimeRelocation.setText("🌙\nMove");
        } else {
            btnNighttimeRelocation.setEnabled(false);
            btnNighttimeRelocation.setText("🌙\n" + powerManager.getNighttimeRelocationCooldown());
        }

        if (powerManager.canUseTier2Power()) {
            btnTier2Power.setEnabled(true);
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
        } else {
            btnTier2Power.setEnabled(false);
            String icon = "";
            switch (selectedPower) {
                case "spy_drone": icon = "🐝"; break;
                case "fence_shield": icon = "🛡️"; break;
                case "fertilizer": icon = "🌱"; break;
            }
            btnTier2Power.setText(icon + "\n" + powerManager.getTier2PowerCooldown());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) {
            firebaseManager.removeRoomListener(roomCode, roomListener);
        }
    }
}