package com.telematica.meteoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.auth.FirebaseAuth // ⭐ 1. IMPORTACIÓN NECESARIA
import java.security.MessageDigest
import java.util.Base64

class FormActivity : AppCompatActivity() {

    private val db = Firebase.firestore
    private lateinit var auth: FirebaseAuth // ⭐ 2. VARIABLE DE AUTH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance() // ⭐ 3. INICIALIZACIÓN DE AUTH

        // Verificar si ya hay una sesión activa
        verificarSesionActiva()

        setContentView(R.layout.activity_form)

        val email = findViewById<EditText>(R.id.editTextEmail)
        val password = findViewById<EditText>(R.id.editTextPassword)
        val button = findViewById<Button>(R.id.buttonSubmit)
        val botonRegistro = findViewById<Button>(R.id.bottonRegistro)
        val btnToggle = findViewById<Button>(R.id.btnTogglePassword)

        btnToggle.setOnClickListener {
            if (password.inputType == android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                // 🔹 Mostrar texto normal
                password.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnToggle.text = "Ocultar"
            } else {
                // 🔹 Volver a puntos
                password.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnToggle.text = "Mostrar"
            }

            // Mantener el cursor al final del texto
            password.setSelection(password.text.length)
        }

        // LOGIN
        button.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Rellena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            button.isEnabled = false // Deshabilitar durante la verificación

            db.collection("usuarios")
                .whereEqualTo("email", emailText)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Toast.makeText(this, "Usuario no existe", Toast.LENGTH_SHORT).show()
                        button.isEnabled = true
                    } else {
                        val user = result.documents[0]

                        // 🔹 Recuperar hash y salt almacenados
                        val storedHash = user.getString("password")
                        val storedSalt = user.getString("salt")

                        if (storedHash == null || storedSalt == null) {
                            Toast.makeText(this, "Error: datos de usuario corruptos", Toast.LENGTH_SHORT).show()
                            button.isEnabled = true
                            return@addOnSuccessListener
                        }

                        // 🔹 Recalcular hash con el salt almacenado
                        val recalculatedHash = hashPassword(passwordText, storedSalt)

                        // 🔹 Comparar hashes
                        if (storedHash == recalculatedHash) {

                            // ⭐⭐ PUENTE: SI EL HASH ES CORRECTO, AUTENTICAR EN FIREBASE AUTH ⭐⭐
                            auth.signInWithEmailAndPassword(emailText, passwordText)
                                .addOnCompleteListener(this) { task ->
                                    if (task.isSuccessful) {

                                        // 4. Login exitoso en Hashing y Auth
                                        Toast.makeText(this, "Inicio de sesión correcto", Toast.LENGTH_SHORT).show()

                                        // GUARDAR SESIÓN EN SharedPreferences
                                        guardarSesion(
                                            userId = user.id,
                                            userEmail = user.getString("email") ?: "",
                                            userName = user.getString("nombre") ?: "",
                                            premium = user.getBoolean("premium") ?: false,
                                            pais = user.getString("pais") ?: "",
                                            comunidad = user.getString("comunidadAutonoma") ?: "",
                                            localidad = user.getString("localidad") ?: "",
                                            municipio = user.getString("municipio") ?: ""
                                        )

                                        val Usuariopremium = user.getBoolean("premium")

                                        val targetActivity = if (Usuariopremium == true) MetereologiaPremiumActivity::class.java else MetereologiaBasicActivity::class.java
                                        val intent = Intent(this, targetActivity)

                                        // Pasar datos a la siguiente actividad
                                        intent.putExtra("userName", user.getString("nombre"))
                                        intent.putExtra("email", user.getString("email"))
                                        intent.putExtra("pais", user.getString("pais"))
                                        intent.putExtra("comunidadAutonoma", user.getString("comunidadAutonoma"))
                                        intent.putExtra("localidad", user.getString("localidad"))
                                        intent.putExtra("municipio", user.getString("municipio"))

                                        startActivity(intent)
                                        finish()

                                    } else {
                                        // Falla la creación de la sesión de Firebase Auth (posiblemente la contraseña de Auth no coincide con la de Firestore)
                                        Toast.makeText(this, "Contraseña o usuario no coinciden en el sistema de autenticación.", Toast.LENGTH_SHORT).show()
                                        button.isEnabled = true
                                    }
                                }
                            // -------------------------------------------------------------

                        } else {
                            Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
                            button.isEnabled = true
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al iniciar sesión: ${e.message}", Toast.LENGTH_LONG).show()
                    button.isEnabled = true
                }
        }

        botonRegistro.setOnClickListener {
            val intent = Intent(this, RegistroActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Verificar si hay una sesión activa al abrir la app
     */
    private fun verificarSesionActiva() {
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        val userEmail = sharedPref.getString("userEmail", null)
        val isPremium = sharedPref.getBoolean("premium", false)

        // ⭐ VERIFICACIÓN ADICIONAL DE AUTH: Sólo redirigimos si la sesión de Auth TAMBIÉN está activa
        val firebaseAuthUser = auth.currentUser

        if (userEmail != null && firebaseAuthUser != null) {
            // Ya hay sesión activa, redirigir directamente
            val intent = if (isPremium) {
                Intent(this, MetereologiaPremiumActivity::class.java)
            } else {
                Intent(this, MetereologiaBasicActivity::class.java)
            }

            // Pasar datos desde SharedPreferences
            intent.putExtra("userName", sharedPref.getString("userName", ""))
            intent.putExtra("email", sharedPref.getString("userEmail", ""))
            intent.putExtra("pais", sharedPref.getString("pais", ""))
            intent.putExtra("comunidadAutonoma", sharedPref.getString("comunidad", ""))
            intent.putExtra("localidad", sharedPref.getString("localidad", ""))
            intent.putExtra("municipio", sharedPref.getString("municipio", ""))

            startActivity(intent)
            finish()
        }
    }

    /**
     * Guardar datos de sesión en SharedPreferences
     */
    private fun guardarSesion(
        userId: String,
        userEmail: String,
        userName: String,
        premium: Boolean,
        pais: String,
        comunidad: String,
        localidad: String,
        municipio: String
    ) {
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putString("userId", userId)
            putString("userEmail", userEmail)
            putString("userName", userName)
            putBoolean("premium", premium)
            putString("pais", pais)
            putString("comunidad", comunidad)
            putString("localidad", localidad)
            putString("municipio", municipio)
            apply()
        }
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combinado = password + salt
        val hashBytes = md.digest(combinado.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}