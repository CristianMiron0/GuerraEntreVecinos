package com.example.guerraentrevecinos;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.example.guerraentrevecinos.databinding.ActivityShopBinding;

public class ShopActivity extends AppCompatActivity {

    private ActivityShopBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShopBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(SkinManager.PREFS_NAME, MODE_PRIVATE);

        setupCard("sunflower", binding.btnSunflower);
        setupCard("dog", binding.btnDog);
        setupCard("cat", binding.btnCat);

        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupCard(String unit, MaterialButton button) {
        refreshButton(unit, button);
        button.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            boolean owned = prefs.getBoolean("owned_" + unit, false);
            boolean active = prefs.getBoolean("active_" + unit, false);

            if (!owned) {
                editor.putBoolean("owned_" + unit, true);
                editor.putBoolean("active_" + unit, false);
            } else if (!active) {
                editor.putBoolean("active_" + unit, true);
            } else {
                editor.putBoolean("active_" + unit, false);
            }
            editor.apply();
            refreshButton(unit, button);
        });
    }

    private void refreshButton(String unit, MaterialButton button) {
        boolean owned = prefs.getBoolean("owned_" + unit, false);
        boolean active = prefs.getBoolean("active_" + unit, false);

        if (!owned) {
            button.setText("BUY");
            button.setBackgroundTintList(ColorStateList.valueOf(0xFF4CAF50));
        } else if (!active) {
            button.setText("ACTIVATE");
            button.setBackgroundTintList(ColorStateList.valueOf(0xFF2196F3));
        } else {
            button.setText("DEACTIVATE");
            button.setBackgroundTintList(ColorStateList.valueOf(0xFF757575));
        }
    }
}
