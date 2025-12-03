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
import java.util.Random;

public class MiniDuelActivity extends AppCompatActivity {

    private static final String TAG = "MiniDuelActivity";
    private ActivityMiniDuelBinding binding;
    private int playerChoice = -1;
    private int enemyChoice = -1;
    private boolean isPlayerAttacking;

    public static final String EXTRA_UNIT_TYPE = "UNIT_TYPE";
    public static final String EXTRA_TARGET_ROW = "TARGET_ROW";
    public static final String EXTRA_TARGET_COL = "TARGET_COL";
    public static final String EXTRA_WAS_HIT = "WAS_HIT";
    public static final String EXTRA_IS_MULTIPLAYER = "IS_MULTIPLAYER";
    public static final String EXTRA_ROOM_CODE = "ROOM_CODE"; // ✅ NEW

    private boolean isGardenHoseActive = false;
    private int firstChoice = -1;
    private int secondChoice = -1;

    // Multiplayer
    private boolean isMultiplayer = false;
    private String roomCode;
    private FirebaseManager firebaseManager;
    private ValueEventListener choiceListener;
    private boolean waitingForOpponent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMiniDuelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String unitType = getIntent().getStringExtra(EXTRA_UNIT_TYPE);
        isPlayerAttacking = getIntent().getBooleanExtra("IS_PLAYER_ATTACKING", true);
        isGardenHoseActive = getIntent().getBooleanExtra("GARDEN_HOSE_ACTIVE", false);
        isMultiplayer = getIntent().getBooleanExtra(EXTRA_IS_MULTIPLAYER, false);
        roomCode = getIntent().getStringExtra(EXTRA_ROOM_CODE); // ✅ NEW

        Log.d(TAG, "========================================");
        Log.d(TAG, "MiniDuel STARTED");
        Log.d(TAG, "Unit Type: " + unitType);
        Log.d(TAG, "Is Attacking: " + isPlayerAttacking);
        Log.d(TAG, "Is Multiplayer: " + isMultiplayer);
        Log.d(TAG, "Room Code: " + roomCode);
        Log.d(TAG, "========================================");

        if (isMultiplayer && roomCode != null) {
            firebaseManager = FirebaseManager.getInstance();
            startListeningForChoices();
        }

        if (unitType != null) {
            binding.unitImage.setImageResource(getUnitIcon(unitType));
        }

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

    private void startListeningForChoices() {
        Log.d(TAG, "Started listening for opponent's choice");

        choiceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // ✅ Always check, not just when waitingForOpponent

                DataSnapshot lastAction = snapshot.child("lastAction");
                Integer attackerChoice = lastAction.child("attackerChoice").getValue(Integer.class);
                Integer defenderChoice = lastAction.child("defenderChoice").getValue(Integer.class);

                Log.d(TAG, "Listener triggered - Attacker: " + attackerChoice +
                        ", Defender: " + defenderChoice +
                        ", MyChoice: " + playerChoice +
                        ", Waiting: " + waitingForOpponent);

                // Check if we have both choices AND we've made our choice
                if (attackerChoice != null && defenderChoice != null && playerChoice != -1 && waitingForOpponent) {
                    Log.d(TAG, "✅ Both choices available! Revealing result...");

                    // Set the enemy's choice
                    if (isPlayerAttacking) {
                        enemyChoice = defenderChoice;
                    } else {
                        enemyChoice = attackerChoice;
                    }

                    // Stop listening and waiting
                    waitingForOpponent = false;
                    firebaseManager.removeRoomListener(roomCode, choiceListener);

                    // Show result on UI thread
                    runOnUiThread(() -> {
                        Log.d(TAG, "Calling revealResult()");
                        revealResult();
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase listener error: " + error.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(MiniDuelActivity.this,
                            "Connection error. Returning to game...", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        };

        firebaseManager.listenToRoom(roomCode, choiceListener);
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

    private Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "⏰ TIMEOUT: Opponent didn't respond in 30 seconds");

            runOnUiThread(() -> {
                Toast.makeText(MiniDuelActivity.this,
                        "Opponent timeout. Returning to game...",
                        Toast.LENGTH_LONG).show();

                // Return with default result
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_WAS_HIT, false); // Default to miss
                resultIntent.putExtra(EXTRA_TARGET_ROW, getIntent().getIntExtra(EXTRA_TARGET_ROW, -1));
                resultIntent.putExtra(EXTRA_TARGET_COL, getIntent().getIntExtra(EXTRA_TARGET_COL, -1));
                resultIntent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }
    };

    private void selectNumber(int number) {
        if (playerChoice != -1) return;

        Log.d(TAG, "Player selected number: " + number +
                " (GardenHose: " + isGardenHoseActive +
                ", IsAttacking: " + isPlayerAttacking + ")");

        // Garden Hose logic - ONLY for attacker
        if (isGardenHoseActive && isPlayerAttacking && firstChoice == -1) {
            firstChoice = number;
            highlightButton(number);
            Toast.makeText(this, "First choice: " + number + ". Pick a second number!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (isGardenHoseActive && isPlayerAttacking && firstChoice != -1) {
            if (number == firstChoice) {
                Toast.makeText(this, "Pick a different number!", Toast.LENGTH_SHORT).show();
                return;
            }
            secondChoice = number;
            playerChoice = firstChoice; // Store first choice as main
        } else {
            // Normal selection (defender or no Garden Hose)
            playerChoice = number;
        }

        setButtonsEnabled(false);

        if (isMultiplayer) {
            waitingForOpponent = true;
            timeoutHandler.postDelayed(timeoutRunnable, 30000); // 30 second timeout

            Log.d(TAG, "Multiplayer - Player chose: " + playerChoice +
                    (isGardenHoseActive && isPlayerAttacking ?
                            " & " + secondChoice : "") +
                    " (Attacking: " + isPlayerAttacking + ")");

            binding.tvCountdown.setText("LOCKED IN!");
            binding.tvInstruction.setText("⏳ Waiting for opponent...");

            hideNumberButtons();

            Toast.makeText(this, "✅ Choice locked! Waiting for opponent...", Toast.LENGTH_LONG).show();

            saveChoiceToFirebase();

            // NEW: Add timeout in case opponent disconnects
            timeoutHandler.postDelayed(timeoutRunnable, 30000); // 30 second timeout

        } else {
            // Solo mode
            enemyChoice = new Random().nextInt(4) + 1;

            // If Garden Hose active and we're defending, enemy gets 2 choices
            if (isGardenHoseActive && !isPlayerAttacking) {
                firstChoice = enemyChoice;
                do {
                    secondChoice = new Random().nextInt(4) + 1;
                } while (secondChoice == firstChoice);

                Log.d(TAG, "Solo - AI using Garden Hose: " + firstChoice + " & " + secondChoice);
            }

            new Handler().postDelayed(this::revealResult, 800);
        }
    }


    // FIX: Save Garden Hose choices properly to Firebase
    private void saveChoiceToFirebase() {
        String choiceKey = isPlayerAttacking ? "attackerChoice" : "defenderChoice";

        firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Save main choice
                snapshot.getRef().child("lastAction").child(choiceKey).setValue(playerChoice)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "✅ " + choiceKey + " saved: " + playerChoice);

                            // ✅ If Garden Hose active and attacking, save second choice
                            if (isGardenHoseActive && isPlayerAttacking && secondChoice != -1) {
                                snapshot.getRef().child("lastAction")
                                        .child("attackerSecondChoice")
                                        .setValue(secondChoice)
                                        .addOnSuccessListener(a -> {
                                            Log.d(TAG, "✅ attackerSecondChoice saved: " + secondChoice);
                                        });
                            }

                            new Handler().postDelayed(() -> checkBothChoices(), 500);
                        });

                snapshot.getRef().removeEventListener(this);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // FIX: Read Garden Hose second choice from Firebase
    private void checkBothChoices() {
        Log.d(TAG, "Checking for both choices...");

        new Handler().postDelayed(() -> {
            firebaseManager.listenToRoom(roomCode, new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    DataSnapshot lastAction = snapshot.child("lastAction");
                    Integer attackerChoice = lastAction.child("attackerChoice").getValue(Integer.class);
                    Integer defenderChoice = lastAction.child("defenderChoice").getValue(Integer.class);

                    // ✅ NEW: Check for second choice if Garden Hose active
                    Integer attackerSecond = lastAction.child("attackerSecondChoice").getValue(Integer.class);

                    Log.d(TAG, "Check result - Attacker: " + attackerChoice +
                            (attackerSecond != null ? " & " + attackerSecond : "") +
                            ", Defender: " + defenderChoice +
                            ", MyChoice: " + playerChoice);

                    if (attackerChoice != null && defenderChoice != null) {
                        Log.d(TAG, "✅ BOTH CHOICES FOUND!");

                        // Set enemy choice based on role
                        if (isPlayerAttacking) {
                            enemyChoice = defenderChoice;
                        } else {
                            enemyChoice = attackerChoice;

                            // ✅ If defending against Garden Hose, get both attacker choices
                            if (isGardenHoseActive && attackerSecond != null) {
                                firstChoice = attackerChoice;
                                secondChoice = attackerSecond;
                                Log.d(TAG, "Defending against Garden Hose: " + firstChoice + " & " + secondChoice);
                            }
                        }

                        waitingForOpponent = false;

                        runOnUiThread(() -> {
                            Log.d(TAG, "Revealing result now!");
                            revealResult();
                        });
                    }

                    snapshot.getRef().removeEventListener(this);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Check error: " + error.getMessage());
                }
            });
        }, 300); // 300ms delay
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

    // ✅ FIX for Garden Hose in MiniDuelActivity
    // Replace the revealResult() method in MiniDuelActivity.java:

    private void revealResult() {
        boolean wasHit;

        // FIXED GARDEN HOSE LOGIC
        if (isGardenHoseActive) {
            // Garden Hose: Check if defender's choice matches EITHER of attacker's 2 choices
            wasHit = (firstChoice == enemyChoice || secondChoice == enemyChoice);

            Log.d(TAG, "Garden Hose Active - First: " + firstChoice +
                    ", Second: " + secondChoice +
                    ", Defender: " + enemyChoice +
                    ", Result: " + (wasHit ? "HIT" : "MISS"));
        } else {
            // Normal: Numbers must match exactly
            wasHit = (playerChoice == enemyChoice);

            Log.d(TAG, "Normal duel - Player: " + playerChoice +
                    ", Enemy: " + enemyChoice +
                    ", Result: " + (wasHit ? "HIT" : "MISS"));
        }

        Log.d(TAG, "Revealing result - wasHit: " + wasHit +
                ", isPlayerAttacking: " + isPlayerAttacking);

        binding.tvCountdown.animate().alpha(0f).setDuration(200).start();
        binding.tvInstruction.animate().alpha(0f).setDuration(200).start();

        new Handler().postDelayed(() -> {
            // Display choices
            if (isPlayerAttacking) {
                if (isGardenHoseActive) {
                    binding.tvChoices.setText("You: " + firstChoice + " & " + secondChoice +
                            " | Enemy Defense: " + enemyChoice);
                } else {
                    binding.tvChoices.setText("You: " + playerChoice + " | Enemy Defense: " + enemyChoice);
                }
            } else {
                if (isGardenHoseActive) {
                    // ✅ FIXED: Show defender that attacker has 2 choices
                    binding.tvChoices.setText("Your Defense: " + playerChoice +
                            " | Enemy Attack: " + firstChoice + " & " + secondChoice);
                } else {
                    binding.tvChoices.setText("Your Defense: " + playerChoice +
                            " | Enemy Attack: " + enemyChoice);
                }
            }

            binding.tvChoices.setVisibility(View.VISIBLE);
            binding.tvChoices.setAlpha(0f);
            binding.tvChoices.animate().alpha(1f).setDuration(300).start();

            // Display result
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

    private void setButtonsEnabled(boolean enabled) {
        binding.btnNumber1.setEnabled(enabled);
        binding.btnNumber2.setEnabled(enabled);
        binding.btnNumber3.setEnabled(enabled);
        binding.btnNumber4.setEnabled(enabled);
    }

    private void returnToGame(boolean wasHit) {
        Log.d(TAG, "Returning to game - wasHit: " + wasHit);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_WAS_HIT, wasHit);
        resultIntent.putExtra(EXTRA_TARGET_ROW, getIntent().getIntExtra(EXTRA_TARGET_ROW, -1));
        resultIntent.putExtra(EXTRA_TARGET_COL, getIntent().getIntExtra(EXTRA_TARGET_COL, -1));
        resultIntent.putExtra("IS_PLAYER_ATTACKING", isPlayerAttacking);
        resultIntent.putExtra("FINAL_RESULT", true); // ✅ Signal this is the final result

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacks(timeoutRunnable); // Cancel timeout
        if (choiceListener != null && firebaseManager != null && roomCode != null) {
            firebaseManager.removeRoomListener(roomCode, choiceListener);
        }
    }
}