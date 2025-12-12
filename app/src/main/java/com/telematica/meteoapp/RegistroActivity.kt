package com.telematica.meteoapp

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

class RegistroActivity : AppCompatActivity() {


    val ubicaciones = mapOf(
        "España" to mapOf(
            "Islas Baleares" to mapOf(
                "Mallorca" to listOf("Palma", "Soller", "Inca", "Manacor", "Campos", "Pollença"),
                "Menorca" to listOf("Mahón", "Ciutadella"),
                "Ibiza"   to listOf("Ibiza ciudad", "Santa Eulària", "Sant Antoni")
            ),
            "Cataluña" to mapOf(
                "Barcelona" to listOf("Barcelona ciudad"),
                "Girona" to listOf("Girona ciudad")
            ),
            "Andalucía" to mapOf(
                "Sevilla" to listOf("Sevilla ciudad"),
                "Málaga" to listOf("Málaga ciudad")
            )
        ),
        "Francia" to mapOf(
            "París" to mapOf(
                "París" to listOf("Distrito 1", "Distrito 2", "Distrito 3")
            )
        )
    )
    lateinit var spinnerPais: Spinner
    lateinit var spinnerComunidad: Spinner
    lateinit var spinnerRegion: Spinner
    lateinit var spinnerCiudad: Spinner
    private val db = Firebase.firestore   // Firestore listo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)


        spinnerPais = findViewById(R.id.spinnerPais)
        spinnerComunidad = findViewById(R.id.spinnerComunidad)
        spinnerRegion = findViewById(R.id.spinnerRegion)
        spinnerCiudad = findViewById(R.id.spinnerCiudad)
        val name = findViewById<EditText>(R.id.editTextName)
        val email = findViewById<EditText>(R.id.editTextEmail)
        val password = findViewById<EditText>(R.id.editTextPassword)
        val registerButton = findViewById<Button>(R.id.buttonRegisterConfirm)
        val premium: Boolean = false

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

        cargarPaises()

        registerButton.setOnClickListener {

            val nameText = name.text.toString().trim()
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()
            val pais = spinnerPais.selectedItem?.toString().orEmpty().let { if (it == "PAÍS") "" else it }
            val comunidad = spinnerComunidad.selectedItem?.toString().orEmpty().let { if (it == "COMUNIDAD") "" else it }
            val localidad = spinnerRegion.selectedItem?.toString().orEmpty().let { if (it == "LOCALIDAD") "" else it }
            val municipio = spinnerCiudad.selectedItem?.toString().orEmpty().let { if (it == "MUNICIPIO") "" else it }


            if (nameText.isEmpty() || emailText.isEmpty() || passwordText.isEmpty() || pais.isEmpty() || comunidad.isEmpty() || localidad.isEmpty() || municipio.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val salt = generarSalt()
            val hashedPassword = hashPassword(passwordText, salt)

            // Datos del usuario a guardar
            val userData = hashMapOf(
                "nombre" to nameText,
                "email" to emailText,
                "password" to hashedPassword,   // hash irreversible
                "salt" to salt,
                "premium" to premium,
                "pais" to pais,
                "comunidadAutonoma" to comunidad,
                "localidad" to localidad,
                "municipio" to municipio
            )

            // Guardar en Firestore
            db.collection("usuarios")
                .add(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Usuario registrado con éxito", Toast.LENGTH_SHORT).show()
                    finish() // Volver a la pantalla anterior (FormActivity)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error al registrar: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

    }
    private fun cargarPaises() {
        val paises = mutableListOf("País")
        paises.addAll(ubicaciones.keys)

        spinnerPais.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paises)

        spinnerPais.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    // placeholder seleccionado -> no hay país
                    cargarComunidades(null)
                    return
                }
                val paisSeleccionado = paises[position]
                cargarComunidades(paisSeleccionado)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarComunidades(pais: String?) {
        val comunidades = mutableListOf("Comunidad")

        if (pais != null) {
            // safe: ubicaciones[pais] existe porque viene de keys
            comunidades.addAll(ubicaciones[pais]!!.keys)
        }

        spinnerComunidad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, comunidades)

        spinnerComunidad.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0 || pais == null) {
                    cargarRegiones(null, null)
                    return
                }
                val comunidadSeleccionada = comunidades[position]
                cargarRegiones(pais, comunidadSeleccionada)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarRegiones(pais: String?, comunidad: String?) {
        val regiones = mutableListOf("Localidad")

        if (pais != null && comunidad != null) {
            regiones.addAll(ubicaciones[pais]!![comunidad]!!.keys)
        }

        spinnerRegion.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, regiones)

        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0 || pais == null || comunidad == null) {
                    cargarCiudades(null, null, null)
                    return
                }
                val regionSeleccionada = regiones[position]
                cargarCiudades(pais, comunidad, regionSeleccionada)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarCiudades(pais: String?, comunidad: String?, region: String?) {
        val ciudades = mutableListOf("Municipio")

        if (pais != null && comunidad != null && region != null) {
            ciudades.addAll(ubicaciones[pais]!![comunidad]!![region]!!)
        }

        spinnerCiudad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ciudades)
    }

    fun generarSalt(longitud: Int = 16): String {
        val bytes = Random.nextBytes(longitud)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hashPassword(password: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val combinado = password + salt
        val hashBytes = md.digest(combinado.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

}
