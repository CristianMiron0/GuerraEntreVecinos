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
import java.util.Random;

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

        // TODO: Implement power button clicks (same as solo mode)

        updatePowerButtons();
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
                cell.setOnClickListener(v -> onEnemyCellClicked(finalRow, finalCol));

                enemyCells[row][col] = cell;
                enemyGrid.addView(cell);
            }
        }
    }

    private void initializeGameState() {
        // Host initializes game state
        FirebaseGameRoom.GameStateData gameState = new FirebaseGameRoom.GameStateData();
        gameState.currentRound = 1;
        gameState.currentTurn = "player1"; // Host goes first
        gameState.player1UnitsRemaining = 7;
        gameState.player2UnitsRemaining = 7;

        firebaseManager.updateGameState(roomCode, gameState);

        // Update status
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

                // Get game state
                DataSnapshot gameStateSnapshot = snapshot.child("gameState");
                String currentTurn = gameStateSnapshot.child("currentTurn").getValue(String.class);
                Integer round = gameStateSnapshot.child("currentRound").getValue(Integer.class);

                if (round != null && round != currentRound) {
                    currentRound = round;
                    updateRoundCounter();
                    powerManager.decrementCooldowns();
                    updatePowerButtons();
                }

                // Check if it's my turn
                boolean newIsMyTurn = myPlayerKey.equals(currentTurn);
                if (newIsMyTurn != isMyTurn) {
                    isMyTurn = newIsMyTurn;
                    hasAttackedThisTurn = false;
                    updateTurnIndicator();
                    setEnemyGridClickable(isMyTurn);
                }

                // Check for opponent's action
                DataSnapshot lastActionSnapshot = snapshot.child("lastAction");
                if (lastActionSnapshot.exists()) {
                    String actionPlayer = lastActionSnapshot.child("player").getValue(String.class);

                    // If opponent attacked me
                    if (opponentPlayerKey.equals(actionPlayer)) {
                        Boolean duelPending = lastActionSnapshot.child("duelPending").getValue(Boolean.class);

                        if (duelPending != null && duelPending) {
                            // Opponent attacked, launch mini-duel for defense
                            Integer targetRow = lastActionSnapshot.child("targetRow").getValue(Integer.class);
                            Integer targetCol = lastActionSnapshot.child("targetCol").getValue(Integer.class);
                            Boolean wasHit = lastActionSnapshot.child("wasHit").getValue(Boolean.class);

                            if (targetRow != null && targetCol != null && wasHit != null && wasHit) {
                                // Find the unit that was hit
                                SetupActivity.UnitPosition hitUnit = playerUnits.stream().filter(unit -> unit.row == targetRow && unit.col == targetCol && unit.health > 0).findFirst().orElse(null);

                                if (hitUnit != null) {
                                    pendingAttackRow = targetRow;
                                    pendingAttackCol = targetCol;
                                    isAttacker = false;

                                    showRockFalling(targetRow, targetCol);

                                    new Handler().postDelayed(() -> {
                                        launchMiniDuel(targetRow, targetCol, hitUnit.type, false);
                                    }, 1500);

                                    // Clear duel pending flag
                                    snapshot.getRef().child("lastAction").child("duelPending").setValue(false);
                                }
                            }
                        }
                    }
                }

                // Check win condition
                Integer p1Units = gameStateSnapshot.child("player1UnitsRemaining").getValue(Integer.class);
                Integer p2Units = gameStateSnapshot.child("player2UnitsRemaining").getValue(Integer.class);

                if (p1Units != null && p1Units == 0) {
                    endGame(false); // Player 2 wins
                } else if (p2Units != null && p2Units == 0) {
                    endGame(true); // Player 1 wins
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

        // Send attack action to Firebase
        FirebaseGameRoom.LastActionData action = new FirebaseGameRoom.LastActionData();
        action.type = "attack";
        action.player = myPlayerKey;
        action.targetRow = row;
        action.targetCol = col;
        action.wasHit = true; // Assume hit for now (will be determined in mini-duel)
        action.duelPending = true;
        action.timestamp = System.currentTimeMillis();

        firebaseManager.sendAction(roomCode, action);

        pendingAttackRow = row;
        pendingAttackCol = col;
        isAttacker = true;

        // Wait a moment for opponent to receive
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
                // I attacked
                if (wasHit) {
                    // Update enemy units remaining
                    updateUnitsRemaining(opponentPlayerKey, -1);

                    // Show hit feedback
                    ImageView cell = enemyCells[pendingAttackRow][pendingAttackCol];
                    cell.setBackgroundColor(Color.parseColor("#FF0000"));
                    cell.setImageResource(R.drawable.explosion_icon);
                    cell.setAlpha(1f);
                    enemyRevealedCells[pendingAttackRow][pendingAttackCol] = true;

                    Toast.makeText(this, "Enemy unit destroyed!", Toast.LENGTH_SHORT).show();
                } else {
                    // Damaged
                    ImageView cell = enemyCells[pendingAttackRow][pendingAttackCol];
                    cell.setBackgroundColor(Color.parseColor("#FFA500"));
                    cell.setAlpha(1f);
                    enemyRevealedCells[pendingAttackRow][pendingAttackCol] = true;

                    Toast.makeText(this, "Enemy unit damaged!", Toast.LENGTH_SHORT).show();
                }

                // End my turn
                endMyTurn();

            } else {
                // I defended
                SetupActivity.UnitPosition unit = null;
                for (SetupActivity.UnitPosition u : playerUnits) {
                    if (u.row == pendingAttackRow && u.col == pendingAttackCol && u.health > 0) {
                        unit = u;
                        break;
                    }
                }

                if (unit != null) {
                    if (wasHit) {
                        // Destroyed
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
                        // Damaged
                        unit.health--;

                        ImageView cell = playerCells[unit.row][unit.col];
                        cell.setBackgroundColor(Color.parseColor("#FFA500"));

                        Toast.makeText(this, "Your unit was damaged!", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }

    private void endMyTurn() {
        // Switch turn to opponent
        String newTurn = opponentPlayerKey;

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

                // Remove listener after one update
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

                // Remove listener
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
        // Garden Hose
        if (powerManager.canUseGardenHose()) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
        } else {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\n" + powerManager.getGardenHoseCooldown());
        }

        // Nighttime Relocation
        if (powerManager.canUseNighttimeRelocation()) {
            btnNighttimeRelocation.setEnabled(true);
            btnNighttimeRelocation.setText("🌙\nMove");
        } else {
            btnNighttimeRelocation.setEnabled(false);
            btnNighttimeRelocation.setText("🌙\n" + powerManager.getNighttimeRelocationCooldown());
        }

        // Tier 2 Power
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
                    // Clean up Firebase
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