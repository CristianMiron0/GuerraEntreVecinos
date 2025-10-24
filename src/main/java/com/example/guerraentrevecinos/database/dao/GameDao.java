package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.guerraentrevecinos.database.entities.Game;
import java.util.List;

@Dao
public interface GameDao {

    @Insert
    long insert(Game game);

    @Update
    void update(Game game);

    @Query("SELECT * FROM games WHERE game_id = :gameId")
    Game getGameById(int gameId);

    @Query("SELECT * FROM games WHERE game_status = 'in_progress' LIMIT 1")
    Game getActiveGame();

    @Query("SELECT * FROM games WHERE game_status = 'finished' ORDER BY finished_at DESC")
    List<Game> getAllFinishedGames();

    @Query("SELECT * FROM games WHERE game_status = 'finished' ORDER BY finished_at DESC LIMIT 10")
    List<Game> getRecentGames();

    @Query("UPDATE games SET current_round = :round WHERE game_id = :gameId")
    void updateCurrentRound(int gameId, int round);

    @Query("UPDATE games SET game_status = 'finished', winner_id = :winnerId, finished_at = :finishedAt WHERE game_id = :gameId")
    void finishGame(int gameId, int winnerId, long finishedAt);

    // ✅ NEW: Get win streak
    @Query("SELECT COUNT(*) FROM (SELECT game_id, winner_id FROM games WHERE game_status = 'finished' AND (player1_id = :playerId OR player2_id = :playerId) ORDER BY finished_at DESC LIMIT :limit) WHERE winner_id = :playerId")
    int getCurrentWinStreak(int playerId, int limit);

    // ✅ NEW: Get total games by player
    @Query("SELECT COUNT(*) FROM games WHERE (player1_id = :playerId OR player2_id = :playerId) AND game_status = 'finished'")
    int getTotalGamesByPlayer(int playerId);

    // ✅ NEW: Get fastest win (fewest rounds)
    @Query("SELECT MIN(current_round) FROM games WHERE winner_id = :playerId AND game_status = 'finished'")
    Integer getFastestWinRounds(int playerId);
}