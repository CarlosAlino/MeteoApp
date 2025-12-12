package com.telematica.meteoapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper
import com.google.firebase.firestore.FirebaseFirestoreSettings // ← AÑADIR ESTE IMPORT
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Deshabilitar caché de Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()

        Firebase.firestore.firestoreSettings = settings

        // Esperar 2 segundos antes de ir al formulario
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, FormActivity::class.java)
            startActivity(intent)
            finish()
        }, 2000)
    }
}