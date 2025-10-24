package com.example.guerraentrevecinos.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.guerraentrevecinos.database.entities.Unit;
import java.util.List;

@Dao
public interface UnitDao {

    @Insert
    long insert(Unit unit);

    @Query("SELECT * FROM units")
    List<Unit> getAllUnits();

    @Query("SELECT * FROM units WHERE unit_type = :unitType LIMIT 1")
    Unit getUnitByType(String unitType);

    @Query("SELECT unit_id FROM units WHERE unit_type = :unitType LIMIT 1")
    int getUnitIdByType(String unitType);
}