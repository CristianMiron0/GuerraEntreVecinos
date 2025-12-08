package com.example.guerraentrevecinos;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AbilityManager {

    private Context context;
    private Random random;

    public AbilityManager(Context context) {
        this.context = context;
        this.random = new Random();
    }

    // ✅ Rose: Change color after hit
    public void activateRoseColorChange(SetupActivity.UnitPosition rose, ImageView cellView) {
        if (rose.abilityUsed) return;

        // Random color (exclude red since that's the starting color)
        String[] colors = {"blue", "white", "black"};
        rose.roseColor = colors[random.nextInt(colors.length)];
        rose.abilityUsed = true;

        // Update visual
        int newIcon = getRoseIconByColor(rose.roseColor);

        // Spin animation
        cellView.animate()
                .rotation(360f)
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(500)
                .withEndAction(() -> {
                    cellView.setImageResource(newIcon);
                    cellView.setRotation(0f);
                    cellView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        String colorName = rose.roseColor.substring(0, 1).toUpperCase() + rose.roseColor.substring(1);
        Toast.makeText(context, "🌹 Rose changed to " + colorName + "!", Toast.LENGTH_SHORT).show();
    }

    // ✅ Dog: Fear ability (can't be attacked twice in row)
    public void activateDogFear(SetupActivity.UnitPosition dog, ImageView cellView) {
        if (dog.abilityUsed) return;

        dog.dogFearActive = true;
        dog.abilityUsed = true;

        // Visual: Yellow glow + shake
        cellView.setBackgroundColor(android.graphics.Color.parseColor("#FFD54F")); // Yellow

        // Bark animation (shake)
        cellView.animate()
                .translationX(-15f)
                .setDuration(50)
                .withEndAction(() -> {
                    cellView.animate().translationX(15f).setDuration(50)
                            .withEndAction(() -> {
                                cellView.animate().translationX(-15f).setDuration(50)
                                        .withEndAction(() -> {
                                            cellView.animate().translationX(15f).setDuration(50)
                                                    .withEndAction(() -> {
                                                        cellView.animate().translationX(0f).setDuration(50).start();
                                                    }).start();
                                        }).start();
                            }).start();
                })
                .start();

        // Scale pulse
        cellView.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .setDuration(300)
                .withEndAction(() -> {
                    cellView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start();
                })
                .start();

        Toast.makeText(context, "🐕 Dog activated FEAR! Enemy can't attack it next turn!",
                Toast.LENGTH_LONG).show();
    }

    // Cat: Teleport to random empty cell
    public boolean activateCatTeleport(
            SetupActivity.UnitPosition cat,
            List<SetupActivity.UnitPosition> allUnits) {

        Log.d("AbilityManager", "====================================");
        Log.d("AbilityManager", "CAT TELEPORT ACTIVATED");
        Log.d("AbilityManager", "Current position: (" + cat.row + "," + cat.col + ")");
        Log.d("AbilityManager", "Current health: " + cat.health);
        Log.d("AbilityManager", "Ability used before: " + cat.abilityUsed);
        Log.d("AbilityManager", "====================================");

        if (cat.abilityUsed) {
            Log.w("AbilityManager", "❌ Cat ability already used!");
            return false;
        }

        // Find all empty cells
        List<int[]> emptyCells = new ArrayList<>();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                // Check if cell is empty (no unit at this position)
                boolean isEmpty = true;

                for (SetupActivity.UnitPosition unit : allUnits) {
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

        Log.d("AbilityManager", "Found " + emptyCells.size() + " empty cells");

        if (emptyCells.isEmpty()) {
            Log.e("AbilityManager", "❌ CAT TELEPORT FAILED - No empty cells available!");
            return false;
        }

        // Pick random empty cell
        Random random = new Random();
        int[] newPosition = emptyCells.get(random.nextInt(emptyCells.size()));
        int newRow = newPosition[0];
        int newCol = newPosition[1];

        Log.d("AbilityManager", "Selected teleport destination: (" + newRow + "," + newCol + ")");

        // Update cat's position
        cat.row = newRow;
        cat.col = newCol;
        cat.abilityUsed = true;
        cat.health = 1; // Cat survives with 1 HP

        Log.d("AbilityManager", "✅ CAT TELEPORTED SUCCESSFULLY");
        Log.d("AbilityManager", "   New position: (" + cat.row + "," + cat.col + ")");
        Log.d("AbilityManager", "   New health: " + cat.health);
        Log.d("AbilityManager", "   Ability now used: " + cat.abilityUsed);
        Log.d("AbilityManager", "====================================");

        return true;
    }

    // Helper: Get rose icon by color
    private int getRoseIconByColor(String color) {
        switch (color) {
            case "red":
                return R.drawable.rose_red;
            case "blue":
                return R.drawable.rose_blue;
            case "white":
                return R.drawable.rose_white;
            case "black":
                return R.drawable.rose_black;
            default:
                return R.drawable.rose_icon;
        }
    }

    // Helper: Get current rose icon
    public int getRoseIcon(SetupActivity.UnitPosition rose) {
        return getRoseIconByColor(rose.roseColor);
    }
}