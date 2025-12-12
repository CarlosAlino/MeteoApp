package com.telematica.meteoapp

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest
import java.util.Base64

class CambiarPasswordActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private var userEmail: String? = null
    private var userId: String? = null

    private lateinit var etPasswordActual: EditText
    private lateinit var etPasswordNueva: EditText
    private lateinit var etPasswordConfirmar: EditText
    private lateinit var btnCambiar: Button
    private lateinit var btnCancelar: Button
    private lateinit var btnToggleActual: Button
    private lateinit var btnToggleNueva: Button
    private lateinit var btnToggleConfirmar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cambiar_password)

        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        userEmail = sharedPref.getString("userEmail", null)
        userId = sharedPref.getString("userId", null)

        if (userEmail == null) {
            Toast.makeText(this, "Error: no hay sesión activa", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        inicializarVistas()
        configurarBotones()
    }

    private fun inicializarVistas() {
        etPasswordActual = findViewById(R.id.etPasswordActual)
        etPasswordNueva = findViewById(R.id.etPasswordNueva)
        etPasswordConfirmar = findViewById(R.id.etPasswordConfirmar)
        btnCambiar = findViewById(R.id.btnCambiar)
        btnCancelar = findViewById(R.id.btnCancelar)
        btnToggleActual = findViewById(R.id.btnToggleActual)
        btnToggleNueva = findViewById(R.id.btnToggleNueva)
        btnToggleConfirmar = findViewById(R.id.btnToggleConfirmar)
    }

    private fun configurarBotones() {
        btnCambiar.setOnClickListener {
            cambiarPassword()
        }

        btnCancelar.setOnClickListener {
            finish()
        }

        // Botones para mostrar/ocultar contraseñas
        configurarTogglePassword(btnToggleActual, etPasswordActual)
        configurarTogglePassword(btnToggleNueva, etPasswordNueva)
        configurarTogglePassword(btnToggleConfirmar, etPasswordConfirmar)
    }

    private fun configurarTogglePassword(boton: Button, campo: EditText) {
        boton.setOnClickListener {
            if (campo.inputType == android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                campo.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                boton.text = "👁️"
            } else {
                campo.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                boton.text = "👁️‍🗨️"
            }
            campo.setSelection(campo.text.length)
        }
    }

    private fun cambiarPassword() {
        val passwordActual = etPasswordActual.text.toString().trim()
        val passwordNueva = etPasswordNueva.text.toString().trim()
        val passwordConfirmar = etPasswordConfirmar.text.toString().trim()

        // Validaciones
        if (passwordActual.isEmpty() || passwordNueva.isEmpty() || passwordConfirmar.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        if (passwordNueva != passwordConfirmar) {
            Toast.makeText(this, "Las contraseñas nuevas no coinciden", Toast.LENGTH_SHORT).show()
            return
        }

        if (passwordNueva.length < 6) {
            Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        // Verificar contraseña actual
        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    val storedHash = doc.getString("password") ?: ""
                    val salt = doc.getString("salt") ?: ""

                    val hashActual = hashPassword(passwordActual, salt)

                    if (hashActual != storedHash) {
                        Toast.makeText(this, "Contraseña actual incorrecta", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // Generar nuevo hash con el mismo salt
                    val nuevoHash = hashPassword(passwordNueva, salt)

                    // Actualizar en Firestore
                    doc.reference.update("password", nuevoHash)
                        .addOnSuccessListener {
                            Toast.makeText(this, "✅ Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al actualizar: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al verificar contraseña: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combinado = password + salt
        val hashBytes = md.digest(combinado.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}