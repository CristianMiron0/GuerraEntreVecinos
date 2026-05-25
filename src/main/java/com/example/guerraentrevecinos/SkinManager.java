package com.example.guerraentrevecinos;

import android.content.Context;
import android.content.SharedPreferences;

public class SkinManager {

    static final String PREFS_NAME = "shop_prefs";

    public static boolean isSkinOwned(Context context, String unit) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("owned_" + unit, false);
    }

    public static boolean isSkinActive(Context context, String unit) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("active_" + unit, false);
    }

    public static boolean isSunflowerSkinActive(Context context) {
        return isSkinActive(context, "sunflower");
    }

    public static boolean isDogSkinActive(Context context) {
        return isSkinActive(context, "dog");
    }

    public static boolean isCatSkinActive(Context context) {
        return isSkinActive(context, "cat");
    }
}
