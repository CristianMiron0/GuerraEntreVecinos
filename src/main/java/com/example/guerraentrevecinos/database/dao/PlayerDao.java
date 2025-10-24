package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.guerraentrevecinos.database.entities.Player;
import java.util.List;

@Dao
public interface PlayerDao {

    @Insert
    long insert(Player player);

    @Update
    void update(Player player);

    @Query("SELECT * FROM players WHERE player_id = :playerId")
    Player getPlayerById(int playerId);

    @Query("SELECT * FROM players WHERE is_ai = 0")
    List<Player> getAllHumanPlayers();

    @Query("SELECT * FROM players WHERE is_ai = 1 LIMIT 1")
    Player getAIPlayer();

    @Query("UPDATE players SET total_games = total_games + 1 WHERE player_id = :playerId")
    void incrementGamesPlayed(int playerId);

    @Query("UPDATE players SET total_wins = total_wins + 1 WHERE player_id = :playerId")
    void incrementWins(int playerId);

    @Query("UPDATE players SET total_losses = total_losses + 1 WHERE player_id = :playerId")
    void incrementLosses(int playerId);
}