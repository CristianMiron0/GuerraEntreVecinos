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
import androidx.appcompat.app.AppCompatActivity;

import com.example.guerraentrevecinos.database.AppDatabase;
import com.example.guerraentrevecinos.database.entities.Game;
import com.example.guerraentrevecinos.database.entities.GameStats;
import com.example.guerraentrevecinos.database.entities.GameUnit;
import com.example.guerraentrevecinos.database.entities.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SetupActivity extends AppCompatActivity {

    // UI Components
    private GridLayout gameGrid;
    private View unitSelectionMenu;
    private ImageButton btnSelectSunflower, btnSelectRose, btnSelectDog, btnSelectCat;
    private TextView tvSunflowerCount, tvRoseCount, tvDogCount, tvCatCount;

    // Game State
    private String gameMode;
    private String selectedUnitType = null;
    private int sunflowerCount = 3;
    private int roseCount = 2;
    private int dogCount = 1;
    private int catCount = 1;
    private int totalUnitsPlaced = 0;
    private static final int TOTAL_UNITS = 7;

    // Grid cells and unit positions
    private ImageView[][] gridCells = new ImageView[8][8];
    private List<UnitPosition> playerUnits = new ArrayList<>();

    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gameMode = getIntent().getStringExtra("GAME_MODE");

        // ✅ Route to multiplayer setup if multiplayer mode
        if ("MULTIPLAYER".equals(gameMode)) {
            Intent intent = new Intent(this, MultiplayerSetupActivity.class);
            intent.putExtras(getIntent().getExtras());
            startActivity(intent);
            finish();
            return;
        }

        // Continue with solo setup...
        setContentView(R.layout.activity_setup);
        database = AppDatabase.getDatabase(this);

        // Initialize views
        initializeViews();

        // Setup unit selection buttons
        setupUnitSelectionButtons();

        // Create the 8x8 grid
        createGameGrid();
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

        // Deselect all buttons
        btnSelectSunflower.setSelected(false);
        btnSelectRose.setSelected(false);
        btnSelectDog.setSelected(false);
        btnSelectCat.setSelected(false);

        // Select this button
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

        // Save unit position
        playerUnits.add(new UnitPosition(row, col, unitType, 2)); // 2 HP

        // Animate placement
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
            hideUnitSelectionMenu();
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

    private void hideUnitSelectionMenu() {
        unitSelectionMenu.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(500)
                .withEndAction(() -> {
                    unitSelectionMenu.setVisibility(View.GONE);
                    Toast.makeText(this, "Setup complete! Starting battle...", Toast.LENGTH_SHORT).show();

                    // Generate AI units and start battle
                    new Handler().postDelayed(this::startBattle, 1000);
                })
                .start();
    }

    private void startBattle() {
        // Generate AI unit positions
        List<UnitPosition> aiUnits = generateAIUnits();

        // ✅ Save game to database in background thread
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Get or create human player
                Player humanPlayer = database.playerDao().getPlayerById(1);
                if (humanPlayer == null) {
                    long humanPlayerId = database.playerDao().insert(new Player("You", false));
                    humanPlayer = database.playerDao().getPlayerById((int) humanPlayerId);
                }

                // 2. Get or create AI player
                Player aiPlayer = database.playerDao().getAIPlayer();
                if (aiPlayer == null) {
                    long aiPlayerId = database.playerDao().insert(new Player("Computer", true));
                    aiPlayer = database.playerDao().getPlayerById((int) aiPlayerId);
                }

                // 3. Create new game
                Game game = new Game(humanPlayer.getPlayerId(), aiPlayer.getPlayerId(), "solo_vs_ai");
                long gameId = database.gameDao().insert(game);

                // 4. Save player units
                for (UnitPosition unit : playerUnits) {
                    int unitTypeId = database.unitDao().getUnitIdByType(unit.type);
                    GameUnit gameUnit = new GameUnit(
                            (int) gameId,
                            unitTypeId,
                            humanPlayer.getPlayerId(),
                            unit.row,
                            unit.col,
                            2 // Initial health
                    );
                    database.gameUnitDao().insert(gameUnit);
                }

                // 5. Save AI units
                for (UnitPosition unit : aiUnits) {
                    int unitTypeId = database.unitDao().getUnitIdByType(unit.type);
                    GameUnit gameUnit = new GameUnit(
                            (int) gameId,
                            unitTypeId,
                            aiPlayer.getPlayerId(),
                            unit.row,
                            unit.col,
                            2 // Initial health
                    );
                    database.gameUnitDao().insert(gameUnit);
                }

                // 6. Initialize game stats for both players
                database.gameStatsDao().insert(new GameStats((int) gameId, humanPlayer.getPlayerId()));
                database.gameStatsDao().insert(new GameStats((int) gameId, aiPlayer.getPlayerId()));

                // 7. Update player game counts
                database.playerDao().incrementGamesPlayed(humanPlayer.getPlayerId());
                database.playerDao().incrementGamesPlayed(aiPlayer.getPlayerId());

                // 8. Launch battle activity
                Player finalHumanPlayer = humanPlayer;
                Player finalAiPlayer = aiPlayer;
                runOnUiThread(() -> {
                    Intent intent = new Intent(SetupActivity.this, BattleActivity.class);
                    intent.putExtra("GAME_MODE", gameMode);
                    intent.putExtra("GAME_ID", (int) gameId); // ✅ Pass game ID
                    intent.putExtra("PLAYER_ID", finalHumanPlayer.getPlayerId());
                    intent.putExtra("AI_PLAYER_ID", finalAiPlayer.getPlayerId());
                    intent.putExtra("SELECTED_POWER", getIntent().getStringExtra("SELECTED_POWER")); // ✅ Pass power
                    intent.putParcelableArrayListExtra("PLAYER_UNITS", new ArrayList<>(playerUnits));
                    intent.putParcelableArrayListExtra("AI_UNITS", new ArrayList<>(aiUnits));
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(SetupActivity.this,
                            "Error saving game to database",
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<UnitPosition> generateAIUnits() {
        List<UnitPosition> aiUnits = new ArrayList<>();
        Random random = new Random();

        String[] unitTypes = {"sunflower", "sunflower", "sunflower", "rose", "rose", "dog", "cat"};

        for (String unitType : unitTypes) {
            int row, col;
            boolean validPosition;

            do {
                row = random.nextInt(8);
                col = random.nextInt(8);

                // Check if position is already taken
                validPosition = true;
                for (UnitPosition unit : aiUnits) {
                    if (unit.row == row && unit.col == col) {
                        validPosition = false;
                        break;
                    }
                }
            } while (!validPosition);

            aiUnits.add(new UnitPosition(row, col, unitType, 2));
        }

        return aiUnits;
    }

    private int getUnitIcon(String unitType) {
        switch (unitType) {
            case "sunflower":
                return R.drawable.sunflower_icon;
            case "rose":
                return R.drawable.rose_icon;
            case "dog":
                return R.drawable.dog_icon;
            case "cat":
                return R.drawable.cat_icon;
            default:
                return R.drawable.sunflower_icon;
        }
    }

    // Inner class to store unit positions
    public static class UnitPosition implements android.os.Parcelable {
        public int row;
        public int col;
        public String type;
        public int health;

        // ✅ NEW: Ability tracking
        public boolean abilityUsed = false;
        public String roseColor = "red"; // For rose: red, blue, white, black
        public boolean dogFearActive = false; // For dog fear

        public UnitPosition(int row, int col, String type, int health) {
            this.row = row;
            this.col = col;
            this.type = type;
            this.health = health;
        }

        protected UnitPosition(android.os.Parcel in) {
            row = in.readInt();
            col = in.readInt();
            type = in.readString();
            health = in.readInt();
            abilityUsed = in.readByte() != 0;
            roseColor = in.readString();
            dogFearActive = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(row);
            dest.writeInt(col);
            dest.writeString(type);
            dest.writeInt(health);
            dest.writeByte((byte) (abilityUsed ? 1 : 0));
            dest.writeString(roseColor);
            dest.writeByte((byte) (dogFearActive ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<UnitPosition> CREATOR = new Creator<UnitPosition>() {
            @Override
            public UnitPosition createFromParcel(android.os.Parcel in) {
                return new UnitPosition(in);
            }

            @Override
            public UnitPosition[] newArray(int size) {
                return new UnitPosition[size];
            }
        };
    }
}