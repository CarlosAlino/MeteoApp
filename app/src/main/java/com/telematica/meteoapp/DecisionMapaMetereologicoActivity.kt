package com.telematica.meteoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class DecisionMapaMetereologicoActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_decision_mapa_metereologico)

        val botonNubes = findViewById<Button>(R.id.bottonMapaNubes)
        val botonCalor = findViewById<Button>(R.id.bottonMapaCalor)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        botonNubes.setOnClickListener {
            val intent = Intent(this, MapaSateliteActivity::class.java)
            startActivity(intent)
        }

        botonCalor.setOnClickListener {
            val intent = Intent(this, MapaCalorActivity::class.java)
            startActivity(intent)
        }
    }
}