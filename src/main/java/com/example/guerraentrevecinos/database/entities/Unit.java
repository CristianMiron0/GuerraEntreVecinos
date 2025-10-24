package com.example.guerraentrevecinos.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "units")
public class Unit {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "unit_id")
    private int unitId;

    @ColumnInfo(name = "unit_type")
    private String unitType; // "sunflower", "rose", "dog", "cat"

    @ColumnInfo(name = "unit_name")
    private String unitName;

    @ColumnInfo(name = "base_health")
    private int baseHealth;

    @ColumnInfo(name = "has_special_ability")
    private boolean hasSpecialAbility;

    @ColumnInfo(name = "ability_description")
    private String abilityDescription;

    // Constructor
    public Unit(String unitType, String unitName, int baseHealth,
                boolean hasSpecialAbility, String abilityDescription) {
        this.unitType = unitType;
        this.unitName = unitName;
        this.baseHealth = baseHealth;
        this.hasSpecialAbility = hasSpecialAbility;
        this.abilityDescription = abilityDescription;
    }

    // Getters and Setters
    public int getUnitId() { return unitId; }
    public void setUnitId(int unitId) { this.unitId = unitId; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public String getUnitName() { return unitName; }
    public void setUnitName(String unitName) { this.unitName = unitName; }

    public int getBaseHealth() { return baseHealth; }
    public void setBaseHealth(int baseHealth) { this.baseHealth = baseHealth; }

    public boolean isHasSpecialAbility() { return hasSpecialAbility; }
    public void setHasSpecialAbility(boolean hasSpecialAbility) { this.hasSpecialAbility = hasSpecialAbility; }

    public String getAbilityDescription() { return abilityDescription; }
    public void setAbilityDescription(String abilityDescription) { this.abilityDescription = abilityDescription; }
}