package com.example.guerraentrevecinos;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
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

    // ✅ Cat: Teleport to random empty cell
    public boolean activateCatTeleport(SetupActivity.UnitPosition cat,
                                       ImageView[][] gridCells,
                                       java.util.List<SetupActivity.UnitPosition> allUnits,
                                       boolean isPlayerGrid) {
        if (cat.abilityUsed) return false;

        // Find empty cells
        java.util.List<int[]> emptyCells = new java.util.ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                boolean isEmpty = true;
                for (SetupActivity.UnitPosition unit : allUnits) {
                    if (unit.row == row && unit.col == col && unit.health > 0) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty && !(row == cat.row && col == cat.col)) {
                    emptyCells.add(new int[]{row, col});
                }
            }
        }

        if (emptyCells.isEmpty()) {
            Toast.makeText(context, "🐱 No space to teleport!", Toast.LENGTH_SHORT).show();
            return false;
        }

        // Pick random empty cell
        int[] newPos = emptyCells.get(random.nextInt(emptyCells.size()));
        int oldRow = cat.row;
        int oldCol = cat.col;
        int newRow = newPos[0];
        int newCol = newPos[1];

        ImageView oldCell = gridCells[oldRow][oldCol];
        ImageView newCell = gridCells[newRow][newCol];

        // Fade out animation
        oldCell.animate()
                .alpha(0f)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .rotation(180f)
                .setDuration(400)
                .withEndAction(() -> {
                    // Clear old cell
                    oldCell.setImageDrawable(null);
                    oldCell.setTag(null);
                    oldCell.setAlpha(1f);
                    oldCell.setScaleX(1f);
                    oldCell.setScaleY(1f);
                    oldCell.setRotation(0f);

                    // Update position
                    cat.row = newRow;
                    cat.col = newCol;
                    cat.abilityUsed = true;

                    // Update new cell
                    newCell.setImageResource(R.drawable.cat_icon);
                    newCell.setTag(cat);
                    newCell.setAlpha(0f);
                    newCell.setScaleX(0.3f);
                    newCell.setScaleY(0.3f);
                    newCell.setRotation(180f);

                    // Fade in animation
                    newCell.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(360f)
                            .setDuration(400)
                            .start();

                    Toast.makeText(context, "🐱 Cat teleported to (" + newRow + "," + newCol + ")!",
                            Toast.LENGTH_LONG).show();
                })
                .start();

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