package com.example.balance;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
    }

        public void collect (View v){
            Intent intent = new Intent(StartActivity.this, CollectActivity.class);
            startActivity(intent);
        }

        public void analyze (View v){
            Intent intent = new Intent(StartActivity.this, CollectActivity.class);
            startActivity(intent);
        }
    }