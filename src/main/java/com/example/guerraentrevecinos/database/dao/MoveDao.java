package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.guerraentrevecinos.database.entities.Move;
import java.util.List;

@Dao
public interface MoveDao {

    @Insert
    long insert(Move move);

    @Query("SELECT * FROM moves WHERE game_id = :gameId ORDER BY round_number ASC")
    List<Move> getMovesByGameId(int gameId);

    @Query("SELECT * FROM moves WHERE game_id = :gameId AND round_number = :round")
    List<Move> getMovesByRound(int gameId, int round);

    @Query("SELECT COUNT(*) FROM moves WHERE game_id = :gameId AND attacking_player_id = :playerId")
    int getTotalAttacksByPlayer(int gameId, int playerId);

    @Query("SELECT COUNT(*) FROM moves WHERE game_id = :gameId AND attacking_player_id = :playerId AND was_hit = 1")
    int getSuccessfulHitsByPlayer(int gameId, int playerId);
}