package com.example.guerraentrevecinos;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.guerraentrevecinos.databinding.ActivityMiniDuelBinding;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MiniDuelActivity extends AppCompatActivity {

    private static final String TAG = "MiniDuelActivity";
    private ActivityMiniDuelBinding binding;
    private int playerChoice = -1;
    private int playerSecondChoice = -1; // For Garden Hose
    private int enemyChoice = -1;
    private int enemySecondChoice = -1; // For Garden Hose
    private boolean isPlayerAttacking;
    private boolean isGardenHoseActive = false;

    public static final String EXTRA_UNIT_TYPE = "UNIT_TYPE";
    public static final String EXTRA_TARGET_ROW = "TARGET_ROW";
    public static final String EXTRA_TARGET_COL = "TARGET_COL";
    public static final String EXTRA_WAS_HIT = "WAS_HIT";
    public static final String EXTRA_IS_MULTIPLAYER = "IS_MULTIPLAYER";
    public static final String EXTRA_ROOM_CODE = "ROOM_CODE";

    // Multiplayer
    private boolean isMultiplayer = false;
    private String roomCode;
    private FirebaseManager firebaseManager;
    private ValueEventListener duelListener;
    private boolean choiceLocked = false;
    private Handler timeoutHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniDuelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String unitType = getIntent().getStringExtra(EXTRA_UNIT_TYPE);
        isPlayerAttacking = getIntent().getBooleanExtra("IS_PLAYER_ATTACKING", true);
        isGardenHoseActive = getIntent().getBooleanExtra("GARDEN_HOSE_ACTIVE", false);
        isMultiplayer = getIntent().getBooleanExtra(EXTRA_IS_MULTIPLAYER, false);
        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE);

        Log.d(TAG, "=== MINI DUEL STARTED ===");
        Log.d(TAG, "Unit: " + unitType);
        Log.d(TAG, "Attacking: " + isPlayerAttacking);
        Log.d(TAG, "Garden Hose: " + isGardenHoseActive);
        Log.d(TAG, "Multiplayer: " + isMultiplayer);
        Log.d(TAG, "Room: " + roomCode);

        if (unitType != null) {
            binding.unitImage.setImageResource(getUnitIcon(unitType));
        }

        // Set title
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

        if (isMultiplayer && roomCode != null) {
            firebaseManager = FirebaseManager.getInstance();
            setupMultiplayerDuel();
        }

        setupNumberButtons();
        startCountdown();
    }

    private void setupMultiplayerDuel() {
        // Listen for opponent's choice
        duelListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DataSnapshot duel = snapshot.child("currentDuel");

                if (!duel.exists() || !choiceLocked) return;

                Integer attackChoice = duel.child("attackerChoice").getValue(Integer.class);
                Integer defendChoice = duel.child("defenderChoice").getValue(Integer.class);
                Integer attackSecond = duel.child("attackerSecondChoice").getValue(Integer.class);
                Boolean gardenHose = duel.child("gardenHoseActive").getValue(Boolean.class);

                Log.d(TAG, "Duel data: attack=" + attackChoice +
                        ", defend=" + defendChoice +
                        ", attackSecond=" + attackSecond +
                        ", hose=" + gardenHose);

                // Both choices ready?
                if (attackChoice != null && defendChoice != null) {
                    Log.d(TAG, "✅ Both choices available!");

                    // Set enemy choices based on our role
                    if (isPlayerAttacking) {
                        enemyChoice = defendChoice;
                    } else {
                        enemyChoice = attackChoice;
                        if (gardenHose != null && gardenHose && attackSecond != null) {
                            enemySecondChoice = attackSecond;
                        }
                    }

                    // Stop listening and reveal result
                    firebaseManager.removeRoomListener(roomCode, duelListener);

                    runOnUiThread(() -> {
                        timeoutHandler.removeCallbacksAndMessages(null);
                        revealResult();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Duel listener error: " + error.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(MiniDuelActivity.this,
                            "Connection error", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        };

        firebaseManager.listenToRoom(roomCode, duelListener);

        // Timeout after 30 seconds
        timeoutHandler.postDelayed(() -> {
            if (choiceLocked) {
                Log.w(TAG, "⏰ TIMEOUT - opponent didn't respond");
                Toast.makeText(this, "Opponent timeout", Toast.LENGTH_SHORT).show();

                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_WAS_HIT, false);
                resultIntent.putExtra(EXTRA_TARGET_ROW,
                        getIntent().getIntExtra(EXTRA_TARGET_ROW, -1));
                resultIntent.putExtra(EXTRA_TARGET_COL,
                        getIntent().getIntExtra(EXTRA_TARGET_COL, -1));
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        }, 30000);
    }

    private void setupNumberButtons() {
        binding.btnNumber1.setOnClickListener(v -> selectNumber(1));
        binding.btnNumber2.setOnClickListener(v -> selectNumber(2));
        binding.btnNumber3.setOnClickListener(v -> selectNumber(3));
        binding.btnNumber4.setOnClickListener(v -> selectNumber(4));
    }

    private void startCountdown() {
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

        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("3");
            animateCountdownNumber();
        }, 500);

        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("2");
            animateCountdownNumber();
        }, 1500);

        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("1");
            animateCountdownNumber();
        }, 2500);

        new Handler().postDelayed(() -> {
            binding.tvCountdown.setText("CHOOSE!");
            animateCountdownNumberLarge();

            binding.tvInstruction.setText(isPlayerAttacking ?
                    "Pick your attack number!" : "Pick your defense number!");
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
        if (choiceLocked) return;

        Log.d(TAG, "Selected: " + number +
                " (GardenHose=" + isGardenHoseActive +
                ", Attacking=" + isPlayerAttacking + ")");

        // Garden Hose: Attacker picks 2 numbers
        if (isGardenHoseActive && isPlayerAttacking) {
            if (playerChoice == -1) {
                // First choice
                playerChoice = number;
                highlightButton(number);
                Toast.makeText(this, "First: " + number + ". Pick second!",
                        Toast.LENGTH_SHORT).show();
                return;
            } else {
                // Second choice
                if (number == playerChoice) {
                    Toast.makeText(this, "Pick different number!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                playerSecondChoice = number;
            }
        } else {
            // Normal: single choice
            playerChoice = number;
        }

        // Lock choice
        choiceLocked = true;
        setButtonsEnabled(false);
        hideNumberButtons();

        binding.tvCountdown.setText("LOCKED IN!");
        binding.tvInstruction.setText(isMultiplayer ?
                "⏳ Waiting for opponent..." : "");

        if (isMultiplayer) {
            saveChoiceToFirebase();
        } else {
            // Solo mode
            enemyChoice = new Random().nextInt(4) + 1;

            // Enemy uses Garden Hose if defending against it
            if (isGardenHoseActive && !isPlayerAttacking) {
                enemySecondChoice = new Random().nextInt(4) + 1;
                while (enemySecondChoice == enemyChoice) {
                    enemySecondChoice = new Random().nextInt(4) + 1;
                }
            }

            new Handler().postDelayed(this::revealResult, 1000);
        }
    }

    private void saveChoiceToFirebase() {
        Map<String, Object> duelData = new HashMap<>();

        if (isPlayerAttacking) {
            duelData.put("attackerChoice", playerChoice);
            if (isGardenHoseActive && playerSecondChoice != -1) {
                duelData.put("attackerSecondChoice", playerSecondChoice);
                duelData.put("gardenHoseActive", true);
            }
        } else {
            duelData.put("defenderChoice", playerChoice);
        }

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                snapshot.getRef().child("currentDuel").updateChildren(duelData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ Choice saved: " + duelData);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "❌ Save failed: " + e.getMessage());
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error: " + error.getMessage());
            }
        });
    }

    private void revealResult() {
        boolean wasHit;

        // Calculate result
        if (isGardenHoseActive) {
            if (isPlayerAttacking) {
                // Attacker with hose: hit if defender matches either choice
                wasHit = (enemyChoice == playerChoice ||
                        enemyChoice == playerSecondChoice);
            } else {
                // Defender against hose: hit if matches either attacker choice
                wasHit = (playerChoice == enemyChoice ||
                        playerChoice == enemySecondChoice);
            }
        } else {
            // Normal: exact match
            wasHit = (playerChoice == enemyChoice);
        }

        Log.d(TAG, "=== RESULT ===");
        Log.d(TAG, "Player: " + playerChoice +
                (playerSecondChoice != -1 ? " & " + playerSecondChoice : ""));
        Log.d(TAG, "Enemy: " + enemyChoice +
                (enemySecondChoice != -1 ? " & " + enemySecondChoice : ""));
        Log.d(TAG, "Hit: " + wasHit);

        // Hide countdown
        binding.tvCountdown.animate().alpha(0f).setDuration(200).start();
        binding.tvInstruction.animate().alpha(0f).setDuration(200).start();

        new Handler().postDelayed(() -> {
            // Show choices
            String choicesText;
            if (isPlayerAttacking) {
                if (isGardenHoseActive) {
                    choicesText = "You: " + playerChoice + " & " + playerSecondChoice +
                            " | Defense: " + enemyChoice;
                } else {
                    choicesText = "You: " + playerChoice + " | Defense: " + enemyChoice;
                }
            } else {
                if (isGardenHoseActive) {
                    choicesText = "Your Defense: " + playerChoice +
                            " | Attack: " + enemyChoice + " & " + enemySecondChoice;
                } else {
                    choicesText = "Your Defense: " + playerChoice +
                            " | Attack: " + enemyChoice;
                }
            }

            binding.tvChoices.setText(choicesText);
            binding.tvChoices.setVisibility(View.VISIBLE);
            binding.tvChoices.setAlpha(0f);
            binding.tvChoices.animate().alpha(1f).setDuration(300).start();

            // Show result
            if (wasHit) {
                binding.tvResult.setText(isPlayerAttacking ?
                        "💥 DIRECT HIT!\nUnit DESTROYED!" :
                        "💀 FAILED DEFENSE!\nUnit DESTROYED!");
                binding.tvResult.setTextColor(getColor(android.R.color.holo_red_dark));
                animateUnitDestroyed();
            } else {
                binding.tvResult.setText(isPlayerAttacking ?
                        "⚡ PARTIAL HIT!\nUnit DAMAGED (-1 HP)" :
                        "🛡️ DEFENDED!\nBut DAMAGED (-1 HP)");
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

    private void setButtonsEnabled(boolean enabled) {
        binding.btnNumber1.setEnabled(enabled);
        binding.btnNumber2.setEnabled(enabled);
        binding.btnNumber3.setEnabled(enabled);
        binding.btnNumber4.setEnabled(enabled);
    }

    private void returnToGame(boolean wasHit) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_WAS_HIT, wasHit);
        resultIntent.putExtra(EXTRA_TARGET_ROW,
                getIntent().getIntExtra(EXTRA_TARGET_ROW, -1));
        resultIntent.putExtra(EXTRA_TARGET_COL,
                getIntent().getIntExtra(EXTRA_TARGET_COL, -1));
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private int getUnitIcon(String unitType) {
        switch (unitType) {
            case "sunflower": return R.drawable.sunflower_icon;
            case "rose": return R.drawable.rose_icon;
            case "dog": return R.drawable.dog_icon;
            case "cat": return R.drawable.cat_icon;
            default: return R.drawable.sunflower_icon;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacksAndMessages(null);
        if (duelListener != null && firebaseManager != null && roomCode != null) {
            firebaseManager.removeRoomListener(roomCode, duelListener);
        }
    }
}