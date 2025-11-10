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
import androidx.appcompat.app.AppCompatActivity;

import com.example.guerraentrevecinos.database.AppDatabase;
import com.example.guerraentrevecinos.database.entities.GameStats;
import com.example.guerraentrevecinos.database.entities.Move;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BattleActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_MINI_DUEL = 100;

    // UI Components
    private GridLayout playerGrid, enemyGrid;
    private TextView tvTurnIndicator, tvRoundCounter;
    private View playerGardenSection, enemyGardenSection;

    // Game State
    private String gameMode;
    private List<SetupActivity.UnitPosition> playerUnits;
    private List<SetupActivity.UnitPosition> aiUnits;
    private boolean isPlayerTurn = true;
    private boolean hasAttackedThisTurn = false;
    private int currentRound = 1;
    private static final int MAX_ROUNDS = 30;

    // Grid cells
    private ImageView[][] playerCells = new ImageView[8][8];
    private ImageView[][] enemyCells = new ImageView[8][8];

    // Track revealed cells
    private boolean[][] enemyRevealedCells = new boolean[8][8];

    // Store current attack target for AI turn
    private SetupActivity.UnitPosition currentAITarget = null;

    // Database fields
    private AppDatabase database;
    private int gameId;
    private int playerId;
    private int aiPlayerId;

    private PowerManager powerManager;
    private String selectedPower;
    private MaterialButton btnGardenHose, btnNighttimeRelocation, btnTier2Power;
    private boolean isSelectingUnitForPower = false;
    private String activePowerMode = null;

    // Ability manager
    private AbilityManager abilityManager;
    private SetupActivity.UnitPosition lastAttackedPlayerUnit = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battle);

        // Initialize database
        database = AppDatabase.getDatabase(this);

        // Initialize ability manager
        abilityManager = new AbilityManager(this);

        // Get game data
        gameMode = getIntent().getStringExtra("GAME_MODE");
        gameId = getIntent().getIntExtra("GAME_ID", -1);
        playerId = getIntent().getIntExtra("PLAYER_ID", -1);
        aiPlayerId = getIntent().getIntExtra("AI_PLAYER_ID", -1);
        selectedPower = getIntent().getStringExtra("SELECTED_POWER");
        playerUnits = getIntent().getParcelableArrayListExtra("PLAYER_UNITS");
        aiUnits = getIntent().getParcelableArrayListExtra("AI_UNITS");

        // Initialize power manager
        powerManager = new PowerManager(selectedPower != null ? selectedPower : "spy_drone");

        // Initialize views
        initializeViews();

        // Setup power buttons
        setupPowerButtons();

        // Create grids
        createPlayerGrid();
        createEnemyGrid();

        // Start player turn
        startPlayerTurn();
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

                // Click listener for powers
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

                // Fog of war
                cell.setBackgroundColor(Color.parseColor("#999999"));
                cell.setAlpha(0.8f);
                cell.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.setPadding(8, 8, 8, 8);

                final int finalRow = row;
                final int finalCol = col;
                cell.setOnClickListener(v -> {
                    if (isSelectingUnitForPower && activePowerMode.equals("spy")) {
                        onEnemyCellClickedForPower(finalRow, finalCol);
                    } else {
                        onEnemyCellClicked(finalRow, finalCol);
                    }
                });

                enemyCells[row][col] = cell;
                enemyGrid.addView(cell);
            }
        }
    }

    private void startPlayerTurn() {
        isPlayerTurn = true;
        hasAttackedThisTurn = false;

        tvTurnIndicator.setText("YOUR TURN - ATTACK!");
        tvTurnIndicator.setTextColor(getColor(android.R.color.holo_green_dark));

        // Show enemy garden (for attacking)
        playerGardenSection.setVisibility(View.GONE);
        enemyGardenSection.setVisibility(View.VISIBLE);

        // Enable all enemy cells for clicking
        setEnemyGridClickable(true);
    }

    private void onEnemyCellClicked(int row, int col) {
        if (!isPlayerTurn) {
            Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Block attacks while selecting power
        if (isSelectingUnitForPower) {
            Toast.makeText(this, "Finish using power first or cancel!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (hasAttackedThisTurn) {
            Toast.makeText(this, "You already attacked this turn!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mark as attacked this turn
        hasAttackedThisTurn = true;

        // Disable all enemy cells immediately
        setEnemyGridClickable(false);

        // Check if there's a unit here
        SetupActivity.UnitPosition hitUnit = null;
        for (SetupActivity.UnitPosition unit : aiUnits) {
            if (unit.row == row && unit.col == col && unit.health > 0) {
                hitUnit = unit;
                break;
            }
        }

        if (hitUnit != null) {
            // HIT! Launch mini-duel
            launchMiniDuel(row, col, hitUnit.type, true);
        } else {
            // MISS - empty cell
            showMissOnEnemyGrid(row, col);
            new Handler().postDelayed(this::endPlayerTurn, 1500);
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

    private void launchMiniDuel(int row, int col, String unitType, boolean isPlayerAttacking) {
        setEnemyGridClickable(false);

        Intent intent = new Intent(this, MiniDuelActivity.class);
        intent.putExtra(MiniDuelActivity.EXTRA_UNIT_TYPE, unitType);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_ROW, row);
        intent.putExtra(MiniDuelActivity.EXTRA_TARGET_COL, col);
        intent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);

        // Pass Garden Hose status
        intent.putExtra("GARDEN_HOSE_ACTIVE", powerManager.isGardenHoseActive());

        startActivityForResult(intent, REQUEST_CODE_MINI_DUEL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_MINI_DUEL && resultCode == RESULT_OK) {
            boolean wasHit = data.getBooleanExtra(MiniDuelActivity.EXTRA_WAS_HIT, false);
            boolean isPlayerAttacking = data.getBooleanExtra("IS_PLAYER_ATTACKING", true);
            int row = data.getIntExtra(MiniDuelActivity.EXTRA_TARGET_ROW, -1);
            int col = data.getIntExtra(MiniDuelActivity.EXTRA_TARGET_COL, -1);

            int attackerChoice = data.getIntExtra("ATTACKER_CHOICE", -1);
            int defenderChoice = data.getIntExtra("DEFENDER_CHOICE", -1);

            // Deactivate Garden Hose after use
            if (powerManager.isGardenHoseActive()) {
                powerManager.deactivateGardenHose();
                updatePowerButtons();
            }

            String result;
            if (isPlayerAttacking) {
                if (wasHit) {
                    result = "destroyed";
                    destroyEnemyUnit(row, col);
                } else {
                    result = "damaged";
                    damageEnemyUnit(row, col);
                }

                saveMoveToDatabase(row, col, wasHit, attackerChoice, defenderChoice, result);
                new Handler().postDelayed(this::endPlayerTurn, 1500);
            } else {
                if (wasHit) {
                    result = "destroyed";
                    destroyPlayerUnit(currentAITarget);
                } else {
                    result = "damaged";
                    damagePlayerUnit(currentAITarget);
                }

                saveMoveToDatabase(row, col, wasHit, attackerChoice, defenderChoice, result);
                new Handler().postDelayed(this::startNextRound, 1500);
            }
        }
    }

    private void damageEnemyUnit(int row, int col) {
        SetupActivity.UnitPosition unit = null;
        for (SetupActivity.UnitPosition u : aiUnits) {
            if (u.row == row && u.col == col && u.health > 0) {
                unit = u;
                break;
            }
        }

        if (unit != null) {
            unit.health--;

            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

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
                cell.setAlpha(1f);

                Toast.makeText(this, "Enemy " + unit.type + " damaged! (1 HP left)",
                        Toast.LENGTH_LONG).show();

            } else if (unit.health <= 0) {
                unit.health = 0;
                cell.setBackgroundColor(Color.parseColor("#FF0000"));
                cell.setImageResource(R.drawable.explosion_icon);
                cell.setAlpha(1f);

                Toast.makeText(this, "Enemy " + unit.type + " DESTROYED!",
                        Toast.LENGTH_LONG).show();

                cell.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .alpha(0.5f)
                        .setDuration(600)
                        .start();

                checkWinCondition();
            }
        }
    }

    private void destroyEnemyUnit(int row, int col) {
        SetupActivity.UnitPosition unit = null;
        for (SetupActivity.UnitPosition u : aiUnits) {
            if (u.row == row && u.col == col && u.health > 0) {
                unit = u;
                break;
            }
        }

        if (unit != null) {
            if (unit.type.equals("cat") && !unit.abilityUsed) {
                boolean teleported = abilityManager.activateCatTeleport(
                        unit, enemyCells, aiUnits, false);
                if (teleported) {
                    unit.health = 1;
                    enemyRevealedCells[unit.row][unit.col] = true;

                    ImageView newCell = enemyCells[unit.row][unit.col];
                    newCell.setBackgroundColor(Color.parseColor("#FFA500"));
                    newCell.setImageResource(R.drawable.cat_icon);
                    newCell.setAlpha(1f);

                    return;
                }
            }

            unit.health = 0;

            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

            cell.setBackgroundColor(Color.parseColor("#FF0000"));
            cell.setImageResource(R.drawable.explosion_icon);
            cell.setAlpha(1f);

            Toast.makeText(this, "Enemy " + unit.type + " DESTROYED!",
                    Toast.LENGTH_LONG).show();

            cell.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(0.5f)
                    .setDuration(600)
                    .start();

            checkWinCondition();
        }
    }

    private void damagePlayerUnit(SetupActivity.UnitPosition unit) {
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
            Toast.makeText(this, "🛡️ Fence Shield absorbed the attack!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        unit.health--;
        lastAttackedPlayerUnit = unit;

        ImageView cell = playerCells[unit.row][unit.col];

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

            Toast.makeText(this, "Your " + unit.type + " was damaged! (1 HP left)",
                    Toast.LENGTH_LONG).show();

            cell.animate()
                    .alpha(0.5f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        cell.animate().alpha(1f).setDuration(200).start();
                    })
                    .start();

            if (unit.type.equals("dog") && !unit.abilityUsed) {
                abilityManager.activateDogFear(unit, cell);
            }

        } else if (unit.health <= 0) {
            if (unit.type.equals("cat") && !unit.abilityUsed) {
                boolean teleported = abilityManager.activateCatTeleport(
                        unit, playerCells, playerUnits, true);
                if (teleported) {
                    unit.health = 1;
                    return;
                }
            }

            unit.health = 0;
            cell.setBackgroundColor(Color.parseColor("#8B4513"));
            cell.setImageResource(R.drawable.explosion_icon);

            Toast.makeText(this, "Your " + unit.type + " was DESTROYED!",
                    Toast.LENGTH_LONG).show();

            cell.animate()
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .alpha(0.5f)
                    .setDuration(600)
                    .start();

            checkWinCondition();
        }
    }

    private void destroyPlayerUnit(SetupActivity.UnitPosition unit) {
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

        unit.health = 0;

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#8B4513"));
        cell.setImageResource(R.drawable.explosion_icon);

        Toast.makeText(this, "Your " + unit.type + " was DESTROYED!", Toast.LENGTH_LONG).show();

        cell.animate()
                .scaleX(0.5f)
                .scaleY(0.5f)
                .alpha(0.5f)
                .rotation(360f)
                .setDuration(800)
                .start();

        checkWinCondition();
    }

    private void showMissOnEnemyGrid(int row, int col) {
        ImageView cell = enemyCells[row][col];
        enemyRevealedCells[row][col] = true;

        cell.setBackgroundColor(Color.parseColor("#2196F3"));
        cell.setImageResource(R.drawable.splash_icon);
        cell.setAlpha(1f);

        Toast.makeText(this, "Miss! Empty cell.", Toast.LENGTH_SHORT).show();
    }

    private void endPlayerTurn() {
        isPlayerTurn = false;
        hasAttackedThisTurn = false;

        setEnemyGridClickable(false);

        tvTurnIndicator.setText("ENEMY TURN - DEFENDING!");
        tvTurnIndicator.setTextColor(getColor(android.R.color.holo_red_dark));

        enemyGardenSection.setVisibility(View.GONE);
        playerGardenSection.setVisibility(View.VISIBLE);

        new Handler().postDelayed(this::aiTakeTurn, 2000);
    }

    private void aiTakeTurn() {
        List<SetupActivity.UnitPosition> aliveUnits = new ArrayList<>();
        for (SetupActivity.UnitPosition unit : playerUnits) {
            if (unit.health > 0) {
                if (unit.type.equals("dog") && unit.dogFearActive) {
                    continue;
                }
                aliveUnits.add(unit);
            }
        }

        if (aliveUnits.isEmpty()) {
            boolean onlyFearedDogs = false;
            for (SetupActivity.UnitPosition unit : playerUnits) {
                if (unit.health > 0 && unit.type.equals("dog") && unit.dogFearActive) {
                    onlyFearedDogs = true;
                    break;
                }
            }

            if (onlyFearedDogs) {
                for (SetupActivity.UnitPosition unit : playerUnits) {
                    if (unit.health > 0 && unit.type.equals("dog")) {
                        unit.dogFearActive = false;
                        aliveUnits.add(unit);
                        break;
                    }
                }
            } else {
                endGame(false);
                return;
            }
        }

        Random random = new Random();
        currentAITarget = aliveUnits.get(random.nextInt(aliveUnits.size()));

        if (lastAttackedPlayerUnit != null &&
                lastAttackedPlayerUnit.type.equals("dog") &&
                lastAttackedPlayerUnit.dogFearActive) {
            lastAttackedPlayerUnit.dogFearActive = false;

            ImageView cell = playerCells[lastAttackedPlayerUnit.row][lastAttackedPlayerUnit.col];
            if (lastAttackedPlayerUnit.health == 1) {
                cell.setBackgroundColor(Color.parseColor("#FFA500"));
            } else {
                cell.setBackgroundColor(Color.parseColor("#8FBC8F"));
            }
        }

        Toast.makeText(this, "Enemy attacks your " + currentAITarget.type +
                        " at (" + currentAITarget.row + "," + currentAITarget.col + ")!",
                Toast.LENGTH_LONG).show();

        showRockFalling(currentAITarget.row, currentAITarget.col);

        new Handler().postDelayed(() -> {
            launchMiniDuel(currentAITarget.row, currentAITarget.col,
                    currentAITarget.type, false);
        }, 1500);
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

    private void startNextRound() {
        currentRound++;
        updateRoundCounter();

        powerManager.decrementCooldowns();
        updatePowerButtons();

        if (currentRound > MAX_ROUNDS) {
            int playerAlive = 0;
            int aiAlive = 0;

            for (SetupActivity.UnitPosition unit : playerUnits) {
                if (unit.health > 0) playerAlive++;
            }

            for (SetupActivity.UnitPosition unit : aiUnits) {
                if (unit.health > 0) aiAlive++;
            }

            if (playerAlive > aiAlive) {
                endGame(true);
            } else if (aiAlive > playerAlive) {
                endGame(false);
            } else {
                endGame(true);
            }
            return;
        }

        startPlayerTurn();
    }

    private void checkWinCondition() {
        boolean aiHasUnits = false;
        for (SetupActivity.UnitPosition unit : aiUnits) {
            if (unit.health > 0) {
                aiHasUnits = true;
                break;
            }
        }

        if (!aiHasUnits) {
            endGame(true);
            return;
        }

        boolean playerHasUnits = false;
        for (SetupActivity.UnitPosition unit : playerUnits) {
            if (unit.health > 0) {
                playerHasUnits = true;
                break;
            }
        }

        if (!playerHasUnits) {
            endGame(false);
        }
    }

    private void endGame(boolean playerWon) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int winnerId = playerWon ? playerId : aiPlayerId;

                database.gameDao().finishGame(gameId, winnerId, System.currentTimeMillis());

                if (playerWon) {
                    database.playerDao().incrementWins(playerId);
                    database.playerDao().incrementLosses(aiPlayerId);
                } else {
                    database.playerDao().incrementWins(aiPlayerId);
                    database.playerDao().incrementLosses(playerId);
                }

                GameStats playerStats = database.gameStatsDao().getStatsByGameAndPlayer(gameId, playerId);
                if (playerStats != null && playerStats.getTotalAttacks() > 0) {
                    float accuracy = (playerStats.getSuccessfulHits() * 100f) / playerStats.getTotalAttacks();
                    playerStats.setAccuracyPercentage(accuracy);
                    database.gameStatsDao().update(playerStats);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        String message = playerWon ? "🎉 YOU WIN!" : "💀 YOU LOSE!";

        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Game Over")
                    .setMessage(message + "\n\nRound: " + currentRound + "/" + MAX_ROUNDS)
                    .setCancelable(false)
                    .setPositiveButton("Play Again", (dialog, which) -> {
                        Intent intent = new Intent(this, GameModeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Main Menu", (dialog, which) -> {
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        });
    }

    private void updateRoundCounter() {
        tvRoundCounter.setText("Round: " + currentRound + "/" + MAX_ROUNDS);
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

    private void saveMoveToDatabase(int targetRow, int targetCol, boolean wasHit,
                                    int attackerChoice, int defenderChoice, String result) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                Move move = new Move(
                        gameId,
                        currentRound,
                        isPlayerTurn ? playerId : aiPlayerId,
                        targetRow,
                        targetCol,
                        wasHit,
                        attackerChoice,
                        defenderChoice,
                        result
                );
                database.moveDao().insert(move);

                if (wasHit) {
                    database.gameStatsDao().incrementSuccessfulHits(
                            gameId,
                            isPlayerTurn ? playerId : aiPlayerId
                    );
                }

                database.gameStatsDao().incrementTotalAttacks(
                        gameId,
                        isPlayerTurn ? playerId : aiPlayerId
                );

                if (result.equals("destroyed")) {
                    database.gameStatsDao().incrementUnitsDestroyed(
                            gameId,
                            isPlayerTurn ? playerId : aiPlayerId
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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

        btnGardenHose.setOnClickListener(v -> {
            if (powerManager.canUseGardenHose() && isPlayerTurn && !hasAttackedThisTurn) {
                powerManager.activateGardenHose();
                Toast.makeText(this, "💧 Garden Hose activated! Pick 2 numbers in next duel.",
                        Toast.LENGTH_LONG).show();
                updatePowerButtons();
            } else if (hasAttackedThisTurn) {
                Toast.makeText(this, "Already attacked this turn!", Toast.LENGTH_SHORT).show();
            }
        });

        btnNighttimeRelocation.setOnClickListener(v -> {
            if (powerManager.canUseNighttimeRelocation() && isPlayerTurn && !hasAttackedThisTurn) {
                activateUnitSelectionMode("move");
            } else if (hasAttackedThisTurn) {
                Toast.makeText(this, "Already attacked this turn!", Toast.LENGTH_SHORT).show();
            }
        });

        btnTier2Power.setOnClickListener(v -> {
            if (powerManager.canUseTier2Power() && isPlayerTurn && !hasAttackedThisTurn) {
                handleTier2PowerClick();
            } else if (hasAttackedThisTurn) {
                Toast.makeText(this, "Already attacked this turn!", Toast.LENGTH_SHORT).show();
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
                message = "🌙 Select a unit to move";
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
            case "spy":
                message = "🐝 Click on enemy grid to reveal 3x3 area";
                break;
            case "fence":
                message = "🛡️ Select a unit to protect";
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
            case "fertilizer":
                message = "🌱 Select a wounded unit to heal";
                enemyGardenSection.setVisibility(View.GONE);
                playerGardenSection.setVisibility(View.VISIBLE);
                break;
        }

        Toast.makeText(this, message + "\n(Tap outside grid to cancel)", Toast.LENGTH_LONG).show();
        tvTurnIndicator.setText(message);
    }

    private void updatePowerButtons() {
        // Garden Hose
        if (powerManager.canUseGardenHose() && isPlayerTurn && !hasAttackedThisTurn) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
        } else if (powerManager.isGardenHoseActive()) {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\nActive");
        } else {
            btnGardenHose.setEnabled(false);
            int cooldown = powerManager.getGardenHoseCooldown();
            btnGardenHose.setText(cooldown > 0 ? "💧\n" + cooldown : "💧\nHose");
        }

        // Nighttime Relocation
        if (powerManager.canUseNighttimeRelocation() && isPlayerTurn && !hasAttackedThisTurn) {
            btnNighttimeRelocation.setEnabled(true);
            btnNighttimeRelocation.setText("🌙\nMove");
        } else {
            btnNighttimeRelocation.setEnabled(false);
            int cooldown = powerManager.getNighttimeRelocationCooldown();
            btnNighttimeRelocation.setText(cooldown > 0 ? "🌙\n" + cooldown : "🌙\nMove");
        }

        // Tier 2 Power
        if (powerManager.canUseTier2Power() && isPlayerTurn && !hasAttackedThisTurn) {
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
            int cooldown = powerManager.getTier2PowerCooldown();
            btnTier2Power.setText(cooldown > 0 ? icon + "\n" + cooldown : icon + "\nReady");
        }
    }

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
                .setMessage("Choose direction to move:")
                .setPositiveButton("⬆️ Up", (dialog, which) -> moveUnit(unit, -1, 0))
                .setNegativeButton("⬇️ Down", (dialog, which) -> moveUnit(unit, 1, 0))
                .setNeutralButton("Cancel", (dialog, which) -> cancelPowerMode())
                .show();

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌙 Move " + unit.type)
                .setMessage("Or choose:")
                .setPositiveButton("⬅️ Left", (dialog, which) -> moveUnit(unit, 0, -1))
                .setNegativeButton("➡️ Right", (dialog, which) -> moveUnit(unit, 0, 1))
                .setNeutralButton("Cancel", (dialog, which) -> cancelPowerMode())
                .show();
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
        savePowerUsageToDatabase("nighttime_relocation");

        cancelPowerMode();
        updatePowerButtons();

        Toast.makeText(this, "🌙 Unit moved successfully!", Toast.LENGTH_SHORT).show();
    }

    private void handleFenceShield(SetupActivity.UnitPosition unit) {
        powerManager.setFenceProtectedUnit(unit);
        powerManager.useTier2Power();

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#4DB6AC"));

        Toast.makeText(this, "🛡️ " + unit.type + " is now protected!", Toast.LENGTH_LONG).show();

        savePowerUsageToDatabase("fence_shield");

        cancelPowerMode();
        updatePowerButtons();
    }

    private void handleFertilizer(SetupActivity.UnitPosition unit) {
        if (unit.health >= 2) {
            Toast.makeText(this, "Unit is already at full health!", Toast.LENGTH_SHORT).show();
            return;
        }

        unit.health = 2;

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#8FBC8F"));

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

        savePowerUsageToDatabase("fertilizer");

        cancelPowerMode();
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

                        boolean hasUnit = false;
                        for (SetupActivity.UnitPosition unit : aiUnits) {
                            if (unit.row == row && unit.col == col && unit.health > 0) {
                                cell.setBackgroundColor(Color.parseColor("#FFE082"));
                                cell.setImageResource(getUnitIcon(unit.type));
                                cell.setAlpha(1f);
                                hasUnit = true;
                                break;
                            }
                        }

                        if (!hasUnit) {
                            cell.setBackgroundColor(Color.parseColor("#C5E1A5"));
                            cell.setAlpha(1f);
                        }

                        revealed++;
                    }
                }
            }
        }

        powerManager.useTier2Power();

        Toast.makeText(this, "🐝 Revealed " + revealed + " cells!", Toast.LENGTH_LONG).show();

        savePowerUsageToDatabase("spy_drone");

        isSelectingUnitForPower = false;
        activePowerMode = null;
        tvTurnIndicator.setText("YOUR TURN - ATTACK!");
        updatePowerButtons();
    }

    private void cancelPowerMode() {
        isSelectingUnitForPower = false;
        activePowerMode = null;

        playerGardenSection.setVisibility(View.GONE);
        enemyGardenSection.setVisibility(View.VISIBLE);

        tvTurnIndicator.setText("YOUR TURN - ATTACK!");
    }

    private void savePowerUsageToDatabase(String powerName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                com.example.guerraentrevecinos.database.entities.PowerUsage powerUsage =
                        new com.example.guerraentrevecinos.database.entities.PowerUsage(
                                gameId,
                                playerId,
                                powerName,
                                currentRound
                        );
                database.powerUsageDao().insert(powerUsage);

                database.gameStatsDao().incrementPowersUsed(gameId, playerId);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}