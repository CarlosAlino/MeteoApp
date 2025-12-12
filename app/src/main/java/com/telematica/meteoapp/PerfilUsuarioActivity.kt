package com.telematica.meteoapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class PerfilUsuarioActivity : AppCompatActivity() {
    private val db = Firebase.firestore
    private var userEmail: String? = null
    private var userId: String? = null

    private lateinit var tvNombre: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvUbicacion: TextView
    private lateinit var tvEstadoPremium: TextView
    private lateinit var imgPremiumBadge: ImageView
    private lateinit var btnCambiarPassword: Button
    private lateinit var btnHacersePremium: Button
    private lateinit var btnEliminarCuenta: Button
    private lateinit var btnCerrarSesion: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil_usuario)

        // Obtener email del usuario desde SharedPreferences
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        userEmail = sharedPref.getString("userEmail", null)
        userId = sharedPref.getString("userId", null)

        if (userEmail == null) {
            Toast.makeText(this, "No hay sesión activa", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        inicializarVistas()
        cargarDatosUsuario()
        configurarBotones()
    }

    private fun inicializarVistas() {
        tvNombre = findViewById(R.id.tvNombre)
        tvEmail = findViewById(R.id.tvEmail)
        tvUbicacion = findViewById(R.id.tvUbicacion)
        tvEstadoPremium = findViewById(R.id.tvEstadoPremium)
        imgPremiumBadge = findViewById(R.id.imgPremiumBadge)
        btnCambiarPassword = findViewById(R.id.btnCambiarPassword)
        btnHacersePremium = findViewById(R.id.btnHacersePremium)
        btnEliminarCuenta = findViewById(R.id.btnEliminarCuenta)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)
    }

    private fun cargarDatosUsuario() {
        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    userId = doc.id

                    val nombre = doc.getString("nombre") ?: "Usuario"
                    val email = doc.getString("email") ?: ""
                    val premium = doc.getBoolean("premium") ?: false
                    val pais = doc.getString("pais") ?: ""
                    val comunidad = doc.getString("comunidadAutonoma") ?: ""
                    val localidad = doc.getString("localidad") ?: ""
                    val municipio = doc.getString("municipio") ?: ""

                    // Actualizar UI
                    tvNombre.text = nombre
                    tvEmail.text = email

                    val ubicacionCompleta = "$municipio, $localidad, $comunidad, $pais"
                    tvUbicacion.text = ubicacionCompleta

                    if (premium) {
                        tvEstadoPremium.text = "✨ Usuario Premium"
                        tvEstadoPremium.setTextColor(getColor(R.color.gold))
                        imgPremiumBadge.visibility = ImageView.VISIBLE
                        btnHacersePremium.visibility = Button.GONE
                    } else {
                        tvEstadoPremium.text = "Usuario Gratuito"
                        tvEstadoPremium.setTextColor(getColor(R.color.gray))
                        imgPremiumBadge.visibility = ImageView.GONE
                        btnHacersePremium.visibility = Button.VISIBLE
                    }

                    // Guardar userId en SharedPreferences
                    val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                    sharedPref.edit().putString("userId", userId).apply()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun configurarBotones() {
        btnCambiarPassword.setOnClickListener {
            val intent = Intent(this, CambiarPasswordActivity::class.java)
            startActivity(intent)
        }

        btnHacersePremium.setOnClickListener {
            val intent = Intent(this, HacerPremiumActivity::class.java)
            startActivity(intent)
        }

        btnEliminarCuenta.setOnClickListener {
            mostrarDialogoEliminarCuenta()
        }

        btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun mostrarDialogoEliminarCuenta() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar Cuenta")
            .setMessage("¿Estás seguro de que quieres eliminar tu cuenta? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarCuenta()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarCuenta() {
        if (userId == null) {
            Toast.makeText(this, "Error: ID de usuario no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("usuarios")
            .document(userId!!)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Cuenta eliminada correctamente", Toast.LENGTH_SHORT).show()
                cerrarSesion()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al eliminar cuenta: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cerrarSesion() {
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()

        // Redirigir a la pantalla de login (ajusta según tu FormActivity)
        val intent = Intent(this, FormActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Recargar datos cuando volvemos de cambiar password o hacerse premium
        cargarDatosUsuario()
    }
}