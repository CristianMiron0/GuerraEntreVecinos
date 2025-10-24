package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.guerraentrevecinos.database.entities.GameUnit;
import java.util.List;

@Dao
public interface GameUnitDao {

    @Insert
    long insert(GameUnit gameUnit);

    @Update
    void update(GameUnit gameUnit);

    @Query("SELECT * FROM game_units WHERE game_id = :gameId")
    List<GameUnit> getGameUnitsByGameId(int gameId);

    @Query("SELECT * FROM game_units WHERE game_id = :gameId AND owner_player_id = :playerId")
    List<GameUnit> getGameUnitsByPlayer(int gameId, int playerId);

    @Query("UPDATE game_units SET current_health = :health, is_destroyed = :isDestroyed WHERE game_unit_id = :gameUnitId")
    void updateUnitHealth(int gameUnitId, int health, boolean isDestroyed);

    @Query("SELECT COUNT(*) FROM game_units WHERE game_id = :gameId AND owner_player_id = :playerId AND is_destroyed = 0")
    int countAliveUnits(int gameId, int playerId);
}