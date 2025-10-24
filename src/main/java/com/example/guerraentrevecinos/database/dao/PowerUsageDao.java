package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.guerraentrevecinos.database.entities.PowerUsage;
import java.util.List;

@Dao
public interface PowerUsageDao {

    @Insert
    long insert(PowerUsage powerUsage);

    @Query("SELECT * FROM power_usages WHERE game_id = :gameId")
    List<PowerUsage> getPowerUsagesByGameId(int gameId);

    @Query("SELECT COUNT(*) FROM power_usages WHERE game_id = :gameId AND player_id = :playerId")
    int getTotalPowersUsedByPlayer(int gameId, int playerId);
}