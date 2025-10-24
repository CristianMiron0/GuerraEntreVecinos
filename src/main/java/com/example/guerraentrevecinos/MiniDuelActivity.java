package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityMiniDuelBinding;
import java.util.Random;

public class MiniDuelActivity extends AppCompatActivity {

    private ActivityMiniDuelBinding binding;
    private int playerChoice = -1;
    private int enemyChoice = -1;
    private boolean isPlayerAttacking;

    public static final String EXTRA_UNIT_TYPE = "UNIT_TYPE";
    public static final String EXTRA_TARGET_ROW = "TARGET_ROW";
    public static final String EXTRA_TARGET_COL = "TARGET_COL";
    public static final String EXTRA_WAS_HIT = "WAS_HIT";

    private boolean isGardenHoseActive = false;
    private int firstChoice = -1;
    private int secondChoice = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniDuelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String unitType = getIntent().getStringExtra(EXTRA_UNIT_TYPE);
        isPlayerAttacking = getIntent().getBooleanExtra("IS_PLAYER_ATTACKING", true);
        isGardenHoseActive = getIntent().getBooleanExtra("GARDEN_HOSE_ACTIVE", false); // ✅ NEW

        if (unitType != null) {
            binding.unitImage.setImageResource(getUnitIcon(unitType));
        }

        // Update title based on role and power
        if (isPlayerAttacking) {
            if (isGardenHoseActive) {
                binding.tvTitle.setText("⚔️💧 DOUBLE ATTACK!");
                binding.tvTitle.setTextColor(getColor(android.R.color.holo_blue_dark));
            } else {
                binding.tvTitle.setText("⚔️ YOU ATTACK!");
                binding.tvTitle.setTextColor(getColor(android.R.color.holo_red_dark));
            }
        } else {
            binding.tvTitle.setText("🛡️ YOU DEFEND!");
            binding.tvTitle.setTextColor(getColor(android.R.color.holo_blue_dark));
        }

        setupNumberButtons();
        startCountdown();
    }

    private void setupNumberButtons() {
        binding.btnNumber1.setOnClickListener(v -> selectNumber(1));
        binding.btnNumber2.setOnClickListener(v -> selectNumber(2));
        binding.btnNumber3.setOnClickListener(v -> selectNumber(3));
        binding.btnNumber4.setOnClickListener(v -> selectNumber(4));
    }

    private void startCountdown() {
        // Animate unit entrance
        binding.unitImage.setScaleX(0.3f);
        binding.unitImage.setScaleY(0.3f);
        binding.unitImage.setAlpha(0f);
        binding.unitImage.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new OvershootInterpolator())
                .start();

        // Countdown: 3
        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("3");
            animateCountdownNumber();
        }, 500);

        // Countdown: 2
        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("2");
            animateCountdownNumber();
        }, 1500);

        // Countdown: 1
        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("1");
            animateCountdownNumber();
        }, 2500);

        // CHOOSE!
        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("CHOOSE!");
            animateCountdownNumberLarge();

            // Update instruction based on role
            if (isPlayerAttacking) {
                binding.tvInstruction.setText("Pick your attack number!");
            } else {
                binding.tvInstruction.setText("Pick your defense number!");
            }

            binding.tvInstruction.setVisibility(View.VISIBLE);
            binding.tvInstruction.setAlpha(0f);
            binding.tvInstruction.animate().alpha(1f).setDuration(300).start();

            showNumberButtons();
        }, 3500);
    }

    private void animateCountdownNumber() {
        binding.tvCountdown.setScaleX(0.3f);
        binding.tvCountdown.setScaleY(0.3f);
        binding.tvCountdown.setAlpha(0f);

        binding.tvCountdown.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator())
                .withEndAction(() -> {
                    binding.tvCountdown.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start();
                })
                .start();
    }

    private void animateCountdownNumberLarge() {
        binding.tvCountdown.setScaleX(0.2f);
        binding.tvCountdown.setScaleY(0.2f);
        binding.tvCountdown.setAlpha(0f);

        binding.tvCountdown.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new BounceInterpolator())
                .start();
    }

    private void showNumberButtons() {
        binding.numberButtonsLayout.setVisibility(View.VISIBLE);

        View[] buttons = {
                binding.btnNumber1,
                binding.btnNumber2,
                binding.btnNumber3,
                binding.btnNumber4
        };

        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            button.setAlpha(0f);
            button.setTranslationY(100f);
            button.setScaleX(0.5f);
            button.setScaleY(0.5f);

            button.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(i * 100)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .start();
        }
    }

    private void selectNumber(int number) {
        if (playerChoice != -1) return; // Already selected

        if (isGardenHoseActive && firstChoice == -1) {
            // First choice with Garden Hose
            firstChoice = number;

            // Highlight selected button
            highlightButton(number);

            Toast.makeText(this, "First choice: " + number + ". Pick a second number!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (isGardenHoseActive && firstChoice != -1) {
            // Second choice with Garden Hose
            if (number == firstChoice) {
                Toast.makeText(this, "Pick a different number!", Toast.LENGTH_SHORT).show();
                return;
            }
            secondChoice = number;
            playerChoice = firstChoice; // For return intent
        } else {
            // Normal single choice
            playerChoice = number;
        }

        // Disable all buttons
        setButtonsEnabled(false);

        // Generate enemy choice
        enemyChoice = new Random().nextInt(4) + 1; // 1-4

        // Wait for suspense
        new Handler().postDelayed(this::revealResult, 800);
    }

    private void highlightButton(int number) {
        View button = getButtonByNumber(number);
        if (button != null) {
            button.setAlpha(0.5f);
        }
    }

    private View getButtonByNumber(int number) {
        switch (number) {
            case 1: return binding.btnNumber1;
            case 2: return binding.btnNumber2;
            case 3: return binding.btnNumber3;
            case 4: return binding.btnNumber4;
            default: return null;
        }
    }

    private void revealResult() {
        boolean wasHit;

        if (isGardenHoseActive) {
            // Check if either choice matches
            wasHit = (firstChoice == enemyChoice || secondChoice == enemyChoice);
        } else {
            wasHit = (playerChoice == enemyChoice);
        }

        // Hide countdown and instruction
        binding.tvCountdown.animate().alpha(0f).setDuration(200).start();
        binding.tvInstruction.animate().alpha(0f).setDuration(200).start();

        // Hide buttons
        hideNumberButtons();

        // Show result
        new Handler().postDelayed(() -> {
            // Show choices
            if (isPlayerAttacking) {
                if (isGardenHoseActive) {
                    binding.tvChoices.setText("You: " + firstChoice + " & " + secondChoice +
                            " | Enemy Defense: " + enemyChoice);
                } else {
                    binding.tvChoices.setText("You: " + playerChoice + " | Enemy Defense: " + enemyChoice);
                }
            } else {
                binding.tvChoices.setText("Your Defense: " + playerChoice + " | Enemy Attack: " + enemyChoice);
            }
            binding.tvChoices.setVisibility(View.VISIBLE);
            binding.tvChoices.setAlpha(0f);
            binding.tvChoices.animate().alpha(1f).setDuration(300).start();

            // Show result text
            if (wasHit) {
                if (isPlayerAttacking) {
                    binding.tvResult.setText("💥 DIRECT HIT!\nUnit DESTROYED!");
                } else {
                    binding.tvResult.setText("💀 FAILED DEFENSE!\nUnit DESTROYED!");
                }
                binding.tvResult.setTextColor(getColor(android.R.color.holo_red_dark));
                animateUnitDestroyed();
            } else {
                if (isPlayerAttacking) {
                    binding.tvResult.setText("⚡ PARTIAL HIT!\nUnit DAMAGED (-1 HP)");
                } else {
                    binding.tvResult.setText("🛡️ DEFENDED!\nBut DAMAGED (-1 HP)");
                }
                binding.tvResult.setTextColor(getColor(android.R.color.holo_orange_dark));
                animateUnitDamaged();
            }

            binding.tvResult.setVisibility(View.VISIBLE);
            binding.tvResult.setScaleX(0f);
            binding.tvResult.setScaleY(0f);
            binding.tvResult.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(new BounceInterpolator())
                    .start();

            // Return to battle
            new Handler().postDelayed(() -> returnToGame(wasHit), 2500);

        }, 500);
    }

    private void hideNumberButtons() {
        View[] buttons = {
                binding.btnNumber1,
                binding.btnNumber2,
                binding.btnNumber3,
                binding.btnNumber4
        };

        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            button.animate()
                    .alpha(0f)
                    .translationY(-100f)
                    .scaleX(0.5f)
                    .scaleY(0.5f)
                    .setStartDelay(i * 50)
                    .setDuration(300)
                    .start();
        }
    }

    private void animateUnitDestroyed() {
        // Explosion animation - violent shake and fade
        binding.unitImage.animate()
                .translationX(-20f)
                .setDuration(50)
                .withEndAction(() -> {
                    binding.unitImage.animate().translationX(20f).setDuration(50)
                            .withEndAction(() -> {
                                binding.unitImage.animate().translationX(-20f).setDuration(50)
                                        .withEndAction(() -> {
                                            binding.unitImage.animate().translationX(20f).setDuration(50)
                                                    .withEndAction(() -> {
                                                        binding.unitImage.animate()
                                                                .translationX(0f)
                                                                .scaleX(1.5f)
                                                                .scaleY(1.5f)
                                                                .alpha(0f)
                                                                .setDuration(400)
                                                                .start();
                                                    }).start();
                                        }).start();
                            }).start();
                }).start();
    }

    private void animateUnitDamaged() {
        // Damage animation - shake and flash
        binding.unitImage.animate()
                .translationX(-10f)
                .setDuration(50)
                .withEndAction(() -> {
                    binding.unitImage.animate().translationX(10f).setDuration(50)
                            .withEndAction(() -> {
                                binding.unitImage.animate().translationX(-10f).setDuration(50)
                                        .withEndAction(() -> {
                                            binding.unitImage.animate().translationX(0f).setDuration(50).start();
                                        }).start();
                            }).start();
                }).start();

        // Flash red
        binding.unitImage.animate()
                .alpha(0.3f)
                .setDuration(100)
                .withEndAction(() -> {
                    binding.unitImage.animate()
                            .alpha(1f)
                            .setDuration(100)
                            .withEndAction(() -> {
                                binding.unitImage.animate().alpha(0.3f).setDuration(100)
                                        .withEndAction(() -> {
                                            binding.unitImage.animate().alpha(1f).setDuration(100).start();
                                        }).start();
                            }).start();
                }).start();
    }

    private void setButtonsEnabled(boolean enabled) {
        binding.btnNumber1.setEnabled(enabled);
        binding.btnNumber2.setEnabled(enabled);
        binding.btnNumber3.setEnabled(enabled);
        binding.btnNumber4.setEnabled(enabled);
    }

    private void returnToGame(boolean wasHit) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_WAS_HIT, wasHit);
        resultIntent.putExtra(EXTRA_TARGET_ROW, getIntent().getIntExtra(EXTRA_TARGET_ROW, -1));
        resultIntent.putExtra(EXTRA_TARGET_COL, getIntent().getIntExtra(EXTRA_TARGET_COL, -1));
        resultIntent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);

        // ✅ Pass choices back
        resultIntent.putExtra("ATTACKER_CHOICE", playerChoice);
        resultIntent.putExtra("DEFENDER_CHOICE", enemyChoice);

        setResult(RESULT_OK, resultIntent);
        finish();
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
}