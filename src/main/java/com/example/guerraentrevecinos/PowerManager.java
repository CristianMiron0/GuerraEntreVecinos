package com.example.guerraentrevecinos;

public class PowerManager {

    // Tier 1 Powers (always available)
    private int gardenHoseCooldown = 0;
    private int nighttimeRelocationCooldown = 0;

    // Tier 2 Power (selected one)
    private String tier2Power;
    private int tier2PowerCooldown = 0;

    // Cooldown constants
    private static final int GARDEN_HOSE_COOLDOWN = 3;
    private static final int NIGHTTIME_RELOCATION_COOLDOWN = 5;
    private static final int SPY_DRONE_COOLDOWN = 4;
    private static final int FENCE_SHIELD_COOLDOWN = 6;
    private static final int FERTILIZER_COOLDOWN = 7;

    // Active effects
    private boolean gardenHoseActive = false;
    private SetupActivity.UnitPosition fenceProtectedUnit = null;

    public PowerManager(String tier2Power) {
        this.tier2Power = tier2Power;
    }

    // ✅ FIX: Garden Hose - activate and set cooldown
    public boolean canUseGardenHose() {
        return gardenHoseCooldown <= 0 && !gardenHoseActive;
    }

    public void activateGardenHose() {
        if (canUseGardenHose()) {
            gardenHoseActive = true;
            gardenHoseCooldown = GARDEN_HOSE_COOLDOWN;
        }
    }

    public boolean isGardenHoseActive() {
        return gardenHoseActive;
    }

    // ✅ FIX: Deactivate after use (called after mini-duel)
    public void deactivateGardenHose() {
        gardenHoseActive = false;
    }

    // Nighttime Relocation (Move unit)
    public boolean canUseNighttimeRelocation() {
        return nighttimeRelocationCooldown <= 0;
    }

    public void useNighttimeRelocation() {
        nighttimeRelocationCooldown = NIGHTTIME_RELOCATION_COOLDOWN;
    }

    // Tier 2 Power
    public boolean canUseTier2Power() {
        return tier2PowerCooldown <= 0;
    }

    public void useTier2Power() {
        switch (tier2Power) {
            case "spy_drone":
                tier2PowerCooldown = SPY_DRONE_COOLDOWN;
                break;
            case "fence_shield":
                tier2PowerCooldown = FENCE_SHIELD_COOLDOWN;
                break;
            case "fertilizer":
                tier2PowerCooldown = FERTILIZER_COOLDOWN;
                break;
        }
    }

    // Fence Shield
    public void setFenceProtectedUnit(SetupActivity.UnitPosition unit) {
        this.fenceProtectedUnit = unit;
    }

    public boolean isUnitProtected(SetupActivity.UnitPosition unit) {
        return fenceProtectedUnit != null &&
                fenceProtectedUnit.row == unit.row &&
                fenceProtectedUnit.col == unit.col;
    }

    public void removeFenceProtection() {
        fenceProtectedUnit = null;
    }

    // ✅ FIX: Reduce cooldowns each round
    public void decrementCooldowns() {
        if (gardenHoseCooldown > 0) gardenHoseCooldown--;
        if (nighttimeRelocationCooldown > 0) nighttimeRelocationCooldown--;
        if (tier2PowerCooldown > 0) tier2PowerCooldown--;
    }

    // Getters for UI
    public int getGardenHoseCooldown() {
        return gardenHoseCooldown;
    }

    public int getNighttimeRelocationCooldown() {
        return nighttimeRelocationCooldown;
    }

    public int getTier2PowerCooldown() {
        return tier2PowerCooldown;
    }

    public String getTier2Power() {
        return tier2Power;
    }
}