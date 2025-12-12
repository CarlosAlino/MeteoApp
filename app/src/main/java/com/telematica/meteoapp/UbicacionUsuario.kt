package com.telematica.meteoapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class UbicacionUsuario : AppCompatActivity() {

    val ubicaciones = mapOf(
        "Pais" to mapOf(
            "Comunidad Autonoma" to mapOf(
                "Localidad" to listOf("Municipio"),
            ),
        ),
        "España" to mapOf(
            "Islas Baleares" to mapOf(
                "Menorca" to listOf("Mahón", "Ciutadella"),
                "Mallorca" to listOf("Palma", "Soller", "Inca", "Manacor", "Campos", "Pollença"),
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

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ubicacion_usuario)

        val btnGuardar = findViewById<Button>(R.id.btnGuardar)
        spinnerPais = findViewById(R.id.spinnerPais)
        spinnerComunidad = findViewById(R.id.spinnerComunidad)
        spinnerRegion = findViewById(R.id.spinnerRegion)
        spinnerCiudad = findViewById(R.id.spinnerCiudad)

        // 🔹 Obtener datos actuales del usuario
        val paisActual = intent.getStringExtra("pais") ?: "España"
        val comunidadActual = intent.getStringExtra("comunidadAutonoma") ?: "Islas Baleares"
        val localidadActual = intent.getStringExtra("localidad") ?: "Mallorca"
        val municipioActual = intent.getStringExtra("municipio") ?: "Palma"

        cargarPaises()

        btnGuardar.setOnClickListener {
            guardarUbicacion()
        }
    }

    private fun cargarPaises() {
        val paises = ubicaciones.keys.toList()
        spinnerPais.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paises)

        spinnerPais.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val paisSeleccionado = paises[position]
                cargarComunidades(paisSeleccionado)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarComunidades(pais: String) {
        val comunidades = ubicaciones[pais]!!.keys.toList()
        spinnerComunidad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, comunidades)

        spinnerComunidad.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val comunidadSeleccionada = comunidades[position]
                cargarRegiones(pais, comunidadSeleccionada)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarRegiones(pais: String, comunidad: String) {
        val regiones = ubicaciones[pais]!![comunidad]!!.keys.toList()
        spinnerRegion.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, regiones)

        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val regionSeleccionada = regiones[position]
                cargarCiudades(pais, comunidad, regionSeleccionada)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun cargarCiudades(pais: String, comunidad: String, region: String) {
        val ciudades = ubicaciones[pais]!![comunidad]!![region]!!
        spinnerCiudad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ciudades)
    }
    private fun guardarUbicacion() {
        val pais = spinnerPais.selectedItem.toString()
        val comunidad = spinnerComunidad.selectedItem.toString()
        val localidad = spinnerRegion.selectedItem.toString()
        val municipio = spinnerCiudad.selectedItem.toString()

        val db = FirebaseFirestore.getInstance()

        // Suponemos que pasaste el email del usuario al abrir esta activity
        val userEmail = intent.getStringExtra("email") ?: return

        // Buscamos el documento del usuario por email
        db.collection("usuarios")
            .whereEqualTo("email", userEmail)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    // Obtenemos el ID del documento
                    val docId = result.documents[0].id

                    // Creamos los datos a actualizar
                    val userData = hashMapOf(
                        "pais" to pais,
                        "comunidadAutonoma" to comunidad,
                        "localidad" to localidad,
                        "municipio" to municipio
                    )
                    val user = result.documents[0]
                    // Actualizamos el documento existente
                    db.collection("usuarios").document(docId)
                        .update(userData as Map<String, Any>)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Ubicación actualizada con éxito", Toast.LENGTH_SHORT).show()
                            val Usuariopremium = user.getBoolean("premium")

                            if (Usuariopremium == true) {
                                val intent = Intent(this, MetereologiaPremiumActivity::class.java)
                                intent.putExtra("userName", user.getString("nombre"))
                                intent.putExtra("email", user.getString("email"))
                                intent.putExtra("pais", pais)
                                intent.putExtra("comunidadAutonoma", comunidad)
                                intent.putExtra("localidad", localidad)
                                intent.putExtra("municipio", municipio)
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this, MetereologiaBasicActivity::class.java)
                                intent.putExtra("userName", user.getString("nombre"))
                                intent.putExtra("email", user.getString("email"))
                                intent.putExtra("pais", pais)
                                intent.putExtra("comunidadAutonoma", comunidad)
                                intent.putExtra("localidad", localidad)
                                intent.putExtra("municipio", municipio)
                                startActivity(intent)
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(this, "Usuario no encontrado", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al buscar usuario: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


}