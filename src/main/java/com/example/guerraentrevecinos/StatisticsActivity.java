package com.example.guerraentrevecinos;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.guerraentrevecinos.database.AppDatabase;
import com.example.guerraentrevecinos.database.entities.Game;
import com.example.guerraentrevecinos.database.entities.GameStats;
import com.example.guerraentrevecinos.database.entities.Player;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {

    // UI Components
    private TextView tvTotalGames, tvTotalWins, tvTotalLosses, tvWinRate;
    private TextView tvBestAccuracy, tvMostUnitsDestroyed, tvTotalAttacks;
    private TextView tvNoGames;
    private RecyclerView rvRecentGames;
    private MaterialButton btnBack;

    // Database
    private AppDatabase database;

    // Data
    private List<GameHistoryItem> gameHistoryList = new ArrayList<>();
    private GameHistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // Initialize database
        database = AppDatabase.getDatabase(this);

        // Initialize views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Load statistics
        loadStatistics();

        // Back button
        btnBack.setOnClickListener(v -> finish());
    }

    private void initializeViews() {
        tvTotalGames = findViewById(R.id.tvTotalGames);
        tvTotalWins = findViewById(R.id.tvTotalWins);
        tvTotalLosses = findViewById(R.id.tvTotalLosses);
        tvWinRate = findViewById(R.id.tvWinRate);
        tvBestAccuracy = findViewById(R.id.tvBestAccuracy);
        tvMostUnitsDestroyed = findViewById(R.id.tvMostUnitsDestroyed);
        tvTotalAttacks = findViewById(R.id.tvTotalAttacks);
        tvNoGames = findViewById(R.id.tvNoGames);
        rvRecentGames = findViewById(R.id.rvRecentGames);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupRecyclerView() {
        adapter = new GameHistoryAdapter(gameHistoryList);
        rvRecentGames.setLayoutManager(new LinearLayoutManager(this));
        rvRecentGames.setAdapter(adapter);
    }

    private void loadStatistics() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // Get human player
                Player player = database.playerDao().getPlayerById(1);
                if (player == null) {
                    List<Player> humanPlayers = database.playerDao().getAllHumanPlayers();
                    if (!humanPlayers.isEmpty()) {
                        player = humanPlayers.get(0);
                    }
                }

                if (player == null) {
                    runOnUiThread(() -> showNoGamesState());
                    return;
                }

                final Player finalPlayer = player;

                // Get overall stats
                int totalGames = player.getTotalGames();
                int totalWins = player.getTotalWins();
                int totalLosses = player.getTotalLosses();
                float winRate = totalGames > 0 ? (totalWins * 100f / totalGames) : 0f;

                // Get win streak
                int winStreak = database.gameDao().getCurrentWinStreak(player.getPlayerId(), 10);

                // Get fastest win
                Integer fastestWin = database.gameDao().getFastestWinRounds(player.getPlayerId());
                int fastestWinRounds = (fastestWin != null) ? fastestWin : 0;

                // Calculate stats properly
                List<Game> finishedGames = database.gameDao().getAllFinishedGames();
                float bestAccuracy = 0f;
                int mostUnitsDestroyed = 0;
                int totalAttacks = 0;
                int totalSuccessfulHits = 0;

                for (Game game : finishedGames) {
                    // Only count games where player participated
                    if (game.getPlayer1Id() == player.getPlayerId() ||
                            game.getPlayer2Id() == player.getPlayerId()) {

                        GameStats stats = database.gameStatsDao()
                                .getStatsByGameAndPlayer(game.getGameId(), player.getPlayerId());

                        if (stats != null) {
                            // Calculate accuracy if not set
                            if (stats.getTotalAttacks() > 0) {
                                float gameAccuracy = (stats.getSuccessfulHits() * 100f) / stats.getTotalAttacks();
                                stats.setAccuracyPercentage(gameAccuracy);

                                // Update in database
                                database.gameStatsDao().update(stats);

                                // Track best
                                if (gameAccuracy > bestAccuracy) {
                                    bestAccuracy = gameAccuracy;
                                }
                            }

                            if (stats.getUnitsDestroyed() > mostUnitsDestroyed) {
                                mostUnitsDestroyed = stats.getUnitsDestroyed();
                            }

                            totalAttacks += stats.getTotalAttacks();
                            totalSuccessfulHits += stats.getSuccessfulHits();
                        }
                    }
                }

                // Calculate overall accuracy
                float overallAccuracy = totalAttacks > 0 ?
                        (totalSuccessfulHits * 100f) / totalAttacks : 0f;

                // Get recent games
                List<Game> recentGames = database.gameDao().getRecentGames();
                gameHistoryList.clear();

                for (Game game : recentGames) {
                    if (game.getPlayer1Id() == player.getPlayerId() ||
                            game.getPlayer2Id() == player.getPlayerId()) {

                        boolean isWin = (game.getWinnerId() != null &&
                                game.getWinnerId() == player.getPlayerId());

                        GameStats stats = database.gameStatsDao()
                                .getStatsByGameAndPlayer(game.getGameId(), player.getPlayerId());

                        float accuracy = 0f;
                        int unitsDestroyed = 0;

                        if (stats != null) {
                            // Recalculate accuracy for display
                            if (stats.getTotalAttacks() > 0) {
                                accuracy = (stats.getSuccessfulHits() * 100f) / stats.getTotalAttacks();
                            }
                            unitsDestroyed = stats.getUnitsDestroyed();
                        }

                        String date = formatDate(game.getFinishedAt());

                        gameHistoryList.add(new GameHistoryItem(
                                game.getGameId(),
                                isWin,
                                accuracy,
                                unitsDestroyed,
                                game.getCurrentRound(),
                                date
                        ));
                    }
                }

                // Update UI
                final int finalTotalGames = totalGames;
                final int finalTotalWins = totalWins;
                final int finalTotalLosses = totalLosses;
                final float finalWinRate = winRate;
                final float finalBestAccuracy = bestAccuracy;
                final int finalMostUnitsDestroyed = mostUnitsDestroyed;
                final int finalTotalAttacks = totalAttacks;
                final int finalWinStreak = winStreak;
                final int finalFastestWin = fastestWinRounds;
                final float finalOverallAccuracy = overallAccuracy;

                runOnUiThread(() -> {
                    if (finalTotalGames == 0) {
                        showNoGamesState();
                    } else {
                        showStatistics(finalTotalGames, finalTotalWins, finalTotalLosses,
                                finalWinRate, finalBestAccuracy, finalOverallAccuracy,
                                finalMostUnitsDestroyed, finalTotalAttacks,
                                finalWinStreak, finalFastestWin);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showNoGamesState());
            }
        });
    }

    private void showStatistics(int totalGames, int totalWins, int totalLosses,
                                float winRate, float bestAccuracy, float overallAccuracy,
                                int mostUnitsDestroyed, int totalAttacks,
                                int winStreak, int fastestWin) {
        // Overall stats
        tvTotalGames.setText(String.valueOf(totalGames));
        tvTotalWins.setText(String.valueOf(totalWins));
        tvTotalLosses.setText(String.valueOf(totalLosses));
        tvWinRate.setText(String.format(Locale.getDefault(), "%.0f%%", winRate));

        // Show BEST accuracy
        tvBestAccuracy.setText(String.format(Locale.getDefault(), "%.0f%%", bestAccuracy));
        tvMostUnitsDestroyed.setText(String.valueOf(mostUnitsDestroyed));
        tvTotalAttacks.setText(String.valueOf(totalAttacks));

        // Win streak
        TextView tvWinStreak = findViewById(R.id.tvWinStreak);
        if (tvWinStreak != null) {
            tvWinStreak.setText(String.valueOf(winStreak));
        }

        // Show/hide views
        tvNoGames.setVisibility(View.GONE);
        rvRecentGames.setVisibility(View.VISIBLE);

        // Update adapter
        adapter.notifyDataSetChanged();
    }

    private void showNoGamesState() {
        tvTotalGames.setText("0");
        tvTotalWins.setText("0");
        tvTotalLosses.setText("0");
        tvWinRate.setText("0%");
        tvBestAccuracy.setText("0%");
        tvMostUnitsDestroyed.setText("0");
        tvTotalAttacks.setText("0");

        tvNoGames.setVisibility(View.VISIBLE);
        rvRecentGames.setVisibility(View.GONE);
    }

    private String formatDate(Long timestamp) {
        if (timestamp == null) return "Unknown";

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
        Date date = new Date(timestamp);

        // Check if today
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 24 * 60 * 60 * 1000) {
            return "Today";
        } else if (diff < 48 * 60 * 60 * 1000) {
            return "Yesterday";
        } else {
            return sdf.format(date);
        }
    }
}