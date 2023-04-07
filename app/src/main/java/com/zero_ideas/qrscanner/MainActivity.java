package com.zero_ideas.qrscanner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private Set<String> targetTexts;

    private SQLiteDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        targetTexts = new HashSet<>();
        // Open the database for read and write operations
        db = openOrCreateDatabase("qr_codes.db", Context.MODE_PRIVATE, null);
        // Create the table if it does not exist
        db.execSQL("CREATE TABLE IF NOT EXISTS qr_codes (id INTEGER PRIMARY KEY, code TEXT);");
        readTargetTexts();
        Button scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> startScan());
    }

    private void readTargetTexts() {
        Cursor cursor = db.rawQuery("SELECT * FROM qr_codes", null);
        if (cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String code = cursor.getString(cursor.getColumnIndex("code"));
                targetTexts.add(code);
            } while (cursor.moveToNext());
        } else {
            try {
                InputStream inputStream = getAssets().open("target_texts.txt");
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    targetTexts.add(line);
                    // Insert the code into the database
                    db.execSQL("INSERT INTO qr_codes (code) VALUES ('" + line + "');");
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        cursor.close();
    }

    private void startScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Scan a QR code");
        integrator.setOrientationLocked(false);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setBeepEnabled(false);
        integrator.initiateScan();
    }

    private void analyzeCode(String scannedText) {
        String decodedText = decodeBase64(scannedText);
        if (decodedText == null) {
            Toast.makeText(getApplicationContext(), "Error decoding QR code!", Toast.LENGTH_LONG).show();
            return;
        }
        if (targetTexts.contains(decodedText)) {
            Toast.makeText(getApplicationContext(), "Code belongs to target texts!", Toast.LENGTH_LONG).show();
            // Display green screen
            getWindow().getDecorView().setBackgroundColor(Color.GREEN);
            // Remove code from target texts so it can't be scanned again
            targetTexts.remove(decodedText);
            // Delete the code from the database
            db.execSQL("DELETE FROM qr_codes WHERE code='" + decodedText + "';");
        } else {
            Toast.makeText(getApplicationContext(), "Code does not belong to target texts.", Toast.LENGTH_LONG).show();
            // Display red screen
            getWindow().getDecorView().setBackgroundColor(Color.RED);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String scannedText = result.getContents();
            if (scannedText != null) {
                analyzeCode(scannedText);
            } else {
                Toast.makeText(getApplicationContext(), "No QR code scanned!", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private String decodeBase64(String encodedText) {
        try {
            byte[] data = Base64.decode(encodedText, Base64.DEFAULT);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the database when the activity is destroyed
        if (db != null) {
            db.close();
        }
    }
}