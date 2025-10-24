package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.guerraentrevecinos.database.entities.GameStats;

@Dao
public interface GameStatsDao {

    @Insert
    long insert(GameStats gameStats);

    @Update
    void update(GameStats gameStats);

    @Query("SELECT * FROM game_stats WHERE game_id = :gameId AND player_id = :playerId LIMIT 1")
    GameStats getStatsByGameAndPlayer(int gameId, int playerId);

    @Query("UPDATE game_stats SET total_attacks = total_attacks + 1 WHERE game_id = :gameId AND player_id = :playerId")
    void incrementTotalAttacks(int gameId, int playerId);

    @Query("UPDATE game_stats SET successful_hits = successful_hits + 1 WHERE game_id = :gameId AND player_id = :playerId")
    void incrementSuccessfulHits(int gameId, int playerId);

    @Query("UPDATE game_stats SET units_destroyed = units_destroyed + 1 WHERE game_id = :gameId AND player_id = :playerId")
    void incrementUnitsDestroyed(int gameId, int playerId);

    @Query("UPDATE game_stats SET powers_used = powers_used + 1 WHERE game_id = :gameId AND player_id = :playerId")
    void incrementPowersUsed(int gameId, int playerId);
}