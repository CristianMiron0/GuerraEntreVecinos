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

    private ImageView neighborSprite;

    // Ability manager
    private AbilityManager abilityManager;
    private int aiAttackCount = 0; // Track number of attacks
    private static final int AI_HIT_PATTERN = 3; // Hit every 3rd attack
    private boolean gameEnded = false;

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
        neighborSprite = findViewById(R.id.neighborSprite);

        updateRoundCounter();
    }

    private void playAttackAnimation() {
        if (neighborSprite == null) return;
        Handler h = new Handler();
        int[] frames = {
            R.drawable.neighbour_alert,
            R.drawable.neighbour_attack,
            R.drawable.neighbour_alert,
            R.drawable.neighbour_attack
        };
        int frameMs = 120;
        for (int i = 0; i < frames.length; i++) {
            final int res = frames[i];
            h.postDelayed(() -> neighborSprite.setImageResource(res), (long) i * frameMs);
        }
    }

    private void resetNeighbourToIdle() {
        if (neighborSprite != null) {
            neighborSprite.setImageResource(R.drawable.neighbour_idle);
        }
    }

    private void showDeathEffect(ImageView cell) {
        cell.setImageResource(R.drawable.rip);
        cell.setScaleX(0f);
        cell.setScaleY(0f);
        cell.setAlpha(0f);
        cell.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(new OvershootInterpolator(2f))
                .start();
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

                cell.setBackgroundColor(Color.parseColor("#608FBC8F"));
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
                cell.setBackgroundColor(Color.parseColor("#70999999"));
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
        resetNeighbourToIdle();

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
            return;
        }

        // Block attacks while selecting power
        if (isSelectingUnitForPower) {
            return;
        }

        if (hasAttackedThisTurn) {
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
            playAttackAnimation();
            launchMiniDuel(row, col, hitUnit.type, true);
        } else {
            // MISS - empty cell
            playAttackAnimation();
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

            Log.d("BattleActivity", "Enemy unit damaged. HP: 2 → " + unit.health);

            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

            // Check if unit died from partial hit
            if (unit.health <= 0) {
                Log.d("BattleActivity", "Enemy unit died from partial hit!");

                // CAT TELEPORT on death
                if (unit.type.equals("cat") && !unit.abilityUsed) {
                    Log.d("BattleActivity", "🐱 AI Cat teleporting after partial hit death!");

                    boolean teleported = abilityManager.activateCatTeleport(unit, aiUnits);

                    if (teleported) {
                        final int newRow = unit.row;
                        final int newCol = unit.col;
                        unit.health = 1;

                        // Clear old position
                        cell.setBackgroundColor(Color.parseColor("#C5E1A5"));
                        cell.setImageDrawable(null);
                        cell.setAlpha(1f);

                        // Show at new position
                        ImageView newCell = enemyCells[newRow][newCol];
                        enemyRevealedCells[newRow][newCol] = true;
                        newCell.setBackgroundColor(Color.parseColor("#FFA500"));
                        newCell.setImageResource(R.drawable.cat_enemy);
                        newCell.setAlpha(1f);

                        newCell.setScaleX(0.3f);
                        newCell.setScaleY(0.3f);
                        newCell.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .rotation(720f)
                                .setDuration(600)
                                .start();

                        return; // Don't show destruction
                    }
                }

                // Regular death
                unit.health = 0;
                cell.setBackgroundColor(Color.parseColor("#FF0000"));
                showDeathEffect(cell);

                checkWinCondition();
                return;
            }

            // Survived with 1 HP
            if (unit.type.equals("rose") && !unit.abilityUsed) {
                abilityManager.activateRoseColorChange(unit, cell);
            }

            cell.setBackgroundColor(Color.parseColor("#FFA500")); // Orange

            if (unit.type.equals("rose")) {
                cell.setImageResource(abilityManager.getRoseIcon(unit));
            } else {
                cell.setImageResource(getUnitIcon(unit.type, true));
            }
            cell.setAlpha(1f);
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
            // CAT TELEPORT
            if (unit.type.equals("cat") && !unit.abilityUsed) {
                Log.d("BattleActivity", "🐱 AI Cat teleporting!");

                final int oldRow = unit.row;
                final int oldCol = unit.col;

                boolean teleported = abilityManager.activateCatTeleport(unit, aiUnits);

                if (teleported) {
                    final int newRow = unit.row;
                    final int newCol = unit.col;
                    unit.health = 1;

                    Log.d("BattleActivity", "✅ Cat teleported: (" + oldRow + "," + oldCol + ") → (" +
                            newRow + "," + newCol + ")");

                    // Clear old position
                    ImageView oldCell = enemyCells[oldRow][oldCol];
                    enemyRevealedCells[oldRow][oldCol] = true;
                    oldCell.setBackgroundColor(Color.parseColor("#C5E1A5")); // Light green (empty)
                    oldCell.setImageDrawable(null);
                    oldCell.setAlpha(1f);

                    // Show at new position
                    ImageView newCell = enemyCells[newRow][newCol];
                    enemyRevealedCells[newRow][newCol] = true;
                    newCell.setBackgroundColor(Color.parseColor("#FFA500")); // Orange
                    newCell.setImageResource(R.drawable.cat_enemy);
                    newCell.setAlpha(1f);

                    // Teleport animation
                    newCell.setScaleX(0.3f);
                    newCell.setScaleY(0.3f);
                    newCell.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(720f)
                            .setDuration(600)
                            .start();

                    return; // Don't destroy - cat survived
                }
            }

            // Unit destroyed
            unit.health = 0;

            ImageView cell = enemyCells[row][col];
            enemyRevealedCells[row][col] = true;

            cell.setBackgroundColor(Color.parseColor("#FF0000"));
            showDeathEffect(cell);

            checkWinCondition();
        }
    }

    private void damagePlayerUnit(SetupActivity.UnitPosition unit) {
        if (powerManager.isUnitProtected(unit)) {
            ImageView cell = playerCells[unit.row][unit.col];
            cell.setBackgroundColor(Color.parseColor("#608FBC8F"));

            ImageView finalCell = cell;
            cell.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        finalCell.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                    })
                    .start();

            powerManager.removeFenceProtection();
            return;
        }

        unit.health--;

        Log.d("BattleActivity", "Player unit damaged. HP: 2 → " + unit.health);

        ImageView cell = playerCells[unit.row][unit.col];

        // Check if died from partial hit
        if (unit.health <= 0) {
            Log.d("BattleActivity", "Player unit died from partial hit!");

            // CAT TELEPORT
            if (unit.type.equals("cat") && !unit.abilityUsed) {
                Log.d("BattleActivity", "🐱 Player Cat teleporting!");

                final int oldRow = unit.row;
                final int oldCol = unit.col;

                boolean teleported = abilityManager.activateCatTeleport(unit, playerUnits);

                if (teleported) {
                    final int newRow = unit.row;
                    final int newCol = unit.col;
                    unit.health = 1;

                    Log.d("BattleActivity", "✅ Player cat teleported: (" + oldRow + "," + oldCol +
                            ") → (" + newRow + "," + newCol + ")");

                    // Clear old cell
                    ImageView oldCell = playerCells[oldRow][oldCol];
                    oldCell.setImageDrawable(null);
                    oldCell.setTag(null);
                    oldCell.setBackgroundColor(Color.parseColor("#608FBC8F")); // Green

                    // Show at new position
                    ImageView newCell = playerCells[newRow][newCol];
                    newCell.setImageResource(R.drawable.cat_icon);
                    newCell.setTag(unit);
                    newCell.setBackgroundColor(Color.parseColor("#4CAF50")); // Bright green

                    // Animation
                    newCell.setScaleX(0.3f);
                    newCell.setScaleY(0.3f);
                    newCell.setAlpha(0f);
                    newCell.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .rotation(720f)
                            .setDuration(600)
                            .start();

                    return; // Don't destroy
                }
            }

            // Regular death
            unit.health = 0;
            cell.setBackgroundColor(Color.parseColor("#8B4513"));
            showDeathEffect(cell);

            checkWinCondition();
            return;
        }

        // Survived with 1 HP
        if (unit.type.equals("rose") && !unit.abilityUsed) {
            abilityManager.activateRoseColorChange(unit, cell);
        }

        cell.setBackgroundColor(Color.parseColor("#FFA500"));

        if (unit.type.equals("rose")) {
            cell.setImageResource(abilityManager.getRoseIcon(unit));
        } else {
            cell.setImageResource(getUnitIcon(unit.type));
        }

        if (unit.type.equals("dog") && !unit.abilityUsed) {
            abilityManager.activateDogFear(unit, cell);
        } else {
            cell.animate()
                    .alpha(0.5f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        cell.animate().alpha(1f).setDuration(200).start();
                    })
                    .start();
        }
    }

    private void destroyPlayerUnit(SetupActivity.UnitPosition unit) {
        if (powerManager.isUnitProtected(unit)) {
            ImageView cell = playerCells[unit.row][unit.col];
            cell.setBackgroundColor(Color.parseColor("#608FBC8F"));

            ImageView finalCell = cell;
            cell.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        finalCell.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
                    })
                    .start();

            powerManager.removeFenceProtection();
            return;
        }

        // CAT TELEPORT (direct hit)
        if (unit.type.equals("cat") && !unit.abilityUsed) {
            Log.d("BattleActivity", "🐱 Player Cat teleporting (direct hit)!");

            final int oldRow = unit.row;
            final int oldCol = unit.col;

            boolean teleported = abilityManager.activateCatTeleport(unit, playerUnits);

            if (teleported) {
                final int newRow = unit.row;
                final int newCol = unit.col;
                unit.health = 1;

                // Clear old cell
                ImageView oldCell = playerCells[oldRow][oldCol];
                oldCell.setImageDrawable(null);
                oldCell.setTag(null);
                oldCell.setBackgroundColor(Color.parseColor("#608FBC8F"));

                // Show at new position
                ImageView newCell = playerCells[newRow][newCol];
                newCell.setImageResource(R.drawable.cat_icon);
                newCell.setTag(unit);
                newCell.setBackgroundColor(Color.parseColor("#4CAF50"));

                newCell.setScaleX(0.3f);
                newCell.setScaleY(0.3f);
                newCell.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(720f)
                        .setDuration(600)
                        .start();

                return;
            }
        }

        unit.health = 0;

        ImageView cell = playerCells[unit.row][unit.col];
        cell.setBackgroundColor(Color.parseColor("#8B4513"));
        showDeathEffect(cell);

        checkWinCondition();
    }

    private void showMissOnEnemyGrid(int row, int col) {
        ImageView cell = enemyCells[row][col];
        enemyRevealedCells[row][col] = true;

        cell.setBackgroundColor(Color.parseColor("#2196F3"));
        cell.setImageResource(R.drawable.splash_icon);
        cell.setAlpha(1f);
    }

    private void endPlayerTurn() {
        if (gameEnded) return;
        isPlayerTurn = false;
        resetNeighbourToIdle();
        hasAttackedThisTurn = false;

        setEnemyGridClickable(false);

        tvTurnIndicator.setText("ENEMY TURN - DEFENDING!");
        tvTurnIndicator.setTextColor(getColor(android.R.color.holo_red_dark));

        enemyGardenSection.setVisibility(View.GONE);
        playerGardenSection.setVisibility(View.VISIBLE);

        new Handler().postDelayed(this::aiTakeTurn, 2000);
    }

    private void aiTakeTurn() {
        if (gameEnded) return;
        List<SetupActivity.UnitPosition> aliveUnits = new ArrayList<>();
        SetupActivity.UnitPosition fearedDog = null;
        for (SetupActivity.UnitPosition unit : playerUnits) {
            if (unit.health > 0) {
                if (unit.type.equals("dog") && unit.dogFearActive) {
                    fearedDog = unit;
                    continue;
                }
                aliveUnits.add(unit);
            }
        }

        // Clear fear before any early return so it lasts exactly one AI turn
        if (fearedDog != null) {
            fearedDog.dogFearActive = false;
            ImageView fearCell = playerCells[fearedDog.row][fearedDog.col];
            fearCell.setBackgroundColor(fearedDog.health == 1 ?
                    Color.parseColor("#FFA500") : Color.parseColor("#608FBC8F"));
        }

        if (aliveUnits.isEmpty()) {
            for (SetupActivity.UnitPosition unit : playerUnits) {
                if (unit.health > 0) aliveUnits.add(unit);
            }
            if (aliveUnits.isEmpty()) {
                endGame(false);
                return;
            }
        }

        Random random = new Random();

        // ========================================
        // SMART AI: Decide whether to hit or miss
        // Pattern: Miss, Miss, Hit, Miss, Miss, Hit...
        // ========================================
        aiAttackCount++;
        boolean shouldHit = (aiAttackCount % AI_HIT_PATTERN == 0);

        Log.d("BattleActivity", "AI Attack #" + aiAttackCount + ", Should hit: " + shouldHit);

        if (shouldHit) {
            // HIT: Target a real unit
            currentAITarget = aliveUnits.get(random.nextInt(aliveUnits.size()));

            Log.d("BattleActivity", "AI targeting unit at (" + currentAITarget.row + "," + currentAITarget.col + ")");

        } else {
            // MISS: Pick an empty cell
            List<int[]> emptyCells = new ArrayList<>();

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    boolean isEmpty = true;

                    for (SetupActivity.UnitPosition unit : playerUnits) {
                        if (unit.row == row && unit.col == col && unit.health > 0) {
                            isEmpty = false;
                            break;
                        }
                    }

                    if (isEmpty) {
                        emptyCells.add(new int[]{row, col});
                    }
                }
            }

            if (!emptyCells.isEmpty()) {
                // Pick random empty cell
                int[] emptyCell = emptyCells.get(random.nextInt(emptyCells.size()));

                Log.d("BattleActivity", "AI intentionally missing at (" + emptyCell[0] + "," + emptyCell[1] + ")");

                // Create fake "target" for miss
                currentAITarget = null; // No actual target

                playAttackAnimation();
                showRockFalling(emptyCell[0], emptyCell[1]);

                // Just show splash and continue
                new Handler().postDelayed(() -> {
                    showSplashOnPlayerGrid(emptyCell[0], emptyCell[1]);

                    // Save miss to database
                    saveMoveToDatabase(emptyCell[0], emptyCell[1], false, -1, -1, "miss");

                    new Handler().postDelayed(this::startNextRound, 1500);
                }, 1500);

                return; // Exit early - no duel needed

            } else {
                // No empty cells - forced to hit
                currentAITarget = aliveUnits.get(random.nextInt(aliveUnits.size()));
                Log.d("BattleActivity", "No empty cells - AI forced to hit");
            }
        }

        playAttackAnimation();
        showRockFalling(currentAITarget.row, currentAITarget.col);

        new Handler().postDelayed(() -> {
            launchMiniDuel(currentAITarget.row, currentAITarget.col,
                    currentAITarget.type, false);
        }, 1500);
    }

    private void showSplashOnPlayerGrid(int row, int col) {
        ImageView cell = playerCells[row][col];

        cell.setBackgroundColor(Color.parseColor("#2196F3")); // Blue
        cell.setImageResource(R.drawable.splash_icon);

        Log.d("BattleActivity", "Showed miss splash at (" + row + "," + col + ")");
    }

    private void showRockFalling(int row, int col) {
        ImageView cell = playerCells[row][col];

        int originalColor = Color.parseColor("#608FBC8F");
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
        if (gameEnded) return;
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
        if (gameEnded) return;
        gameEnded = true;

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

    private int getUnitIcon(String unitType, boolean isEnemy) {
        switch (unitType) {
            case "sunflower":
                return R.drawable.sunflower_icon;
            case "rose":
                return R.drawable.rose_red;
            case "dog":
                return R.drawable.dog_icon;
            case "cat":
                return isEnemy ? R.drawable.cat_enemy : R.drawable.cat_icon;
            default:
                return R.drawable.sunflower_icon;
        }
    }

    private int getUnitIcon(String unitType) {
        return getUnitIcon(unitType, false);
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

        // Set power icon based on selection
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

        // FIX: Garden Hose button
        btnGardenHose.setOnClickListener(v -> {
            if (powerManager.canUseGardenHose() && isPlayerTurn && !hasAttackedThisTurn) {
                powerManager.activateGardenHose();
                updatePowerButtons();
            }
        });

        // FIX: Nighttime Relocation button
        btnNighttimeRelocation.setOnClickListener(v -> {
            if (powerManager.canUseNighttimeRelocation() && isPlayerTurn && !hasAttackedThisTurn) {
                activateUnitSelectionMode("move");
            }
        });

        // FIX: Tier 2 Power button
        btnTier2Power.setOnClickListener(v -> {
            if (powerManager.canUseTier2Power() && isPlayerTurn && !hasAttackedThisTurn) {
                handleTier2PowerClick();
            }
        });

        // Initialize button states
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

        tvTurnIndicator.setText(message);
    }

    private void updatePowerButtons() {
        // ✅ Garden Hose
        if (powerManager.isGardenHoseActive()) {
            btnGardenHose.setEnabled(false);
            btnGardenHose.setText("💧\nActive");
            btnGardenHose.setAlpha(0.7f);
        } else if (powerManager.canUseGardenHose() && !hasAttackedThisTurn) {
            btnGardenHose.setEnabled(true);
            btnGardenHose.setText("💧\nHose");
            btnGardenHose.setAlpha(1.0f);
        } else {
            btnGardenHose.setEnabled(false);
            int cooldown = powerManager.getGardenHoseCooldown();
            btnGardenHose.setText(cooldown > 0 ? "💧\n" + cooldown : "💧\nHose");
            btnGardenHose.setAlpha(0.5f);
        }

        // ✅ Nighttime Relocation
        if (powerManager.canUseNighttimeRelocation() && !hasAttackedThisTurn) {
            btnNighttimeRelocation.setEnabled(true);
            btnNighttimeRelocation.setText("🌙\nMove");
            btnNighttimeRelocation.setAlpha(1.0f);
        } else {
            btnNighttimeRelocation.setEnabled(false);
            int cooldown = powerManager.getNighttimeRelocationCooldown();
            btnNighttimeRelocation.setText(cooldown > 0 ? "🌙\n" + cooldown : "🌙\nMove");
            btnNighttimeRelocation.setAlpha(0.5f);
        }

        // ✅ Tier 2 Power
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

        if (powerManager.canUseTier2Power() && !hasAttackedThisTurn) {
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
        // FIX: Create a proper dialog with 4 direction buttons
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌙 Move " + unit.type)
                .setMessage("Choose direction:")
                .setPositiveButton("⬆️ Up", (dialog, which) -> moveUnit(unit, -1, 0))
                .setNegativeButton("⬇️ Down", (dialog, which) -> moveUnit(unit, 1, 0))
                .setNeutralButton("Cancel", (dialog, which) -> cancelPowerMode())
                .show();

        // Show second dialog for left/right
        new Handler().postDelayed(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("🌙 Move " + unit.type)
                    .setMessage("Or choose:")
                    .setPositiveButton("⬅️ Left", (dialog, which) -> moveUnit(unit, 0, -1))
                    .setNegativeButton("➡️ Right", (dialog, which) -> moveUnit(unit, 0, 1))
                    .setNeutralButton("Cancel", (dialog, which) -> cancelPowerMode())
                    .show();
        }, 100);
    }

    private void moveUnit(SetupActivity.UnitPosition unit, int rowOffset, int colOffset) {
        int newRow = unit.row + rowOffset;
        int newCol = unit.col + colOffset;

        // Validate move
        if (newRow < 0 || newRow >= 8 || newCol < 0 || newCol >= 8) {
            cancelPowerMode();
            return;
        }

        // Check if new position is occupied
        for (SetupActivity.UnitPosition u : playerUnits) {
            if (u.row == newRow && u.col == newCol && u.health > 0) {
                cancelPowerMode();
                return;
            }
        }

        // FIX: Actually move the unit!
        ImageView oldCell = playerCells[unit.row][unit.col];

        // Clear old cell
        oldCell.setImageDrawable(null);
        oldCell.setTag(null);
        oldCell.setBackgroundColor(Color.parseColor("#608FBC8F"));

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

        // FIX: Use the power and update cooldown
        powerManager.useNighttimeRelocation();
        savePowerUsageToDatabase("nighttime_relocation");

        // Exit power mode
        cancelPowerMode();
        updatePowerButtons();
    }

    private void handleFenceShield(SetupActivity.UnitPosition unit) {
        // ✅ Set fence protection
        powerManager.setFenceProtectedUnit(unit);
        powerManager.useTier2Power();

        ImageView cell = playerCells[unit.row][unit.col];

        // ✅ Visual indicator - teal/cyan color
        cell.setBackgroundColor(Color.parseColor("#4DB6AC"));

        // Shield animation
        cell.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction(() -> {
                    cell.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        savePowerUsageToDatabase("fence_shield");

        cancelPowerMode();
        updatePowerButtons();
    }

    private void handleFertilizer(SetupActivity.UnitPosition unit) {
        // Check if unit needs healing
        if (unit.health >= 2) {
            cancelPowerMode();
            return;
        }

        // Heal the unit
        unit.health = 2;

        ImageView cell = playerCells[unit.row][unit.col];

        // Restore green background (full health)
        cell.setBackgroundColor(Color.parseColor("#608FBC8F"));

        // Update icon
        if (unit.type.equals("rose")) {
            cell.setImageResource(abilityManager.getRoseIcon(unit));
        } else {
            cell.setImageResource(getUnitIcon(unit.type));
        }

        // Healing animation
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

        // Use power
        powerManager.useTier2Power();
        savePowerUsageToDatabase("fertilizer");

        cancelPowerMode();
        updatePowerButtons();
    }

    private void onEnemyCellClickedForPower(int row, int col) {
        if (!isSelectingUnitForPower || !activePowerMode.equals("spy")) return;
        revealAreaWithSpyDrone(row, col);
    }

    private void revealAreaWithSpyDrone(int centerRow, int centerCol) {
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
                                cell.setImageResource(getUnitIcon(unit.type, true));
                                cell.setAlpha(1f);
                                hasUnit = true;
                                break;
                            }
                        }

                        if (!hasUnit) {
                            cell.setBackgroundColor(Color.parseColor("#C5E1A5"));
                            cell.setAlpha(1f);
                        }
                    }
                }
            }
        }

        powerManager.useTier2Power();
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