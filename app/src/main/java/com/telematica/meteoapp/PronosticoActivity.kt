package com.telematica.meteoapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class PronosticoActivity : AppCompatActivity() {

    private lateinit var recyclerViewDias: RecyclerView
    private lateinit var adapterDias: PronosticoDiasAdapter
    private val db = Firebase.firestore
    private val pronosticosPorDia = mutableListOf<DiaPronostico>()

    // Datos del municipio
    private var nombreMunicipio: String = ""
    private var tipoUsuario: String = "Standard"  // "Standard" o "Premium"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pronostico)

        // Obtener datos del intent
        nombreMunicipio = intent.getStringExtra("municipio") ?: ""
        tipoUsuario = intent.getStringExtra("tipoUsuario") ?: "Standard"

        if (nombreMunicipio.isEmpty()) {
            Toast.makeText(this, "Error: No se especificó el municipio", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        recyclerViewDias = findViewById(R.id.recyclerViewDias)
        recyclerViewDias.layoutManager = LinearLayoutManager(this)

        adapterDias = PronosticoDiasAdapter(pronosticosPorDia)
        recyclerViewDias.adapter = adapterDias

        // Botón volver
        //findViewById<View>(R.id.btnVolver).setOnClickListener {
        //    finish()
        //}

        cargarPronosticos()
    }

    private fun cargarPronosticos() {
        println("🔍 Buscando pronósticos en BD...")
        println("   Municipio: $nombreMunicipio")
        println("   Tipo: $tipoUsuario")

        // ← FORZAR LECTURA SOLO DEL SERVIDOR (ignora caché)
        db.collection(nombreMunicipio)
            .document("Predicción")
            .collection(tipoUsuario)
            .get(com.google.firebase.firestore.Source.SERVER) // ← ESTO ES CLAVE
            .addOnSuccessListener { documents ->
                println("✅ Documentos recibidos DEL SERVIDOR: ${documents.size()}")

                if (documents.isEmpty) {
                    println("⚠️ No hay documentos en $nombreMunicipio/Predicción/$tipoUsuario")
                    Toast.makeText(
                        this,
                        "No hay pronósticos disponibles para $nombreMunicipio",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val registros = mutableListOf<RegistroPronostico>()

                for (doc in documents) {
                    println("📄 Documento index: ${doc.id}")

                    val datetime = doc.getString("datetime") ?: ""
                    val description = doc.getString("description") ?: ""
                    val temp = doc.getDouble("temp") ?: 0.0

                    println("   datetime: $datetime")
                    println("   description: $description")
                    println("   temp: $temp")

                    val registro = RegistroPronostico(
                        datetime = datetime,
                        description = description,
                        icon = doc.getString("icon") ?: "",
                        temp = temp,
                        humidity = doc.getLong("humidity")?.toInt() ?: 0,
                        windSpeed = doc.getDouble("wind_speed") ?: 0.0,
                        windDeg = doc.getLong("wind_deg")?.toInt() ?: 0,
                        pop = doc.getLong("pop")?.toInt() ?: 0,
                        pressure = doc.getLong("pressure")?.toInt() ?: 0
                    )
                    registros.add(registro)
                }

                // Ordenar por datetime
                registros.sortBy { it.datetime }

                println("📊 Total registros procesados: ${registros.size}")
                agruparPorDias(registros)
            }
            .addOnFailureListener { e ->
                println("❌ Error al cargar: ${e.message}")
                println("   Ruta intentada: $nombreMunicipio/Predicción/$tipoUsuario")
                Toast.makeText(this, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
    }

    private fun agruparPorDias(registros: List<RegistroPronostico>) {
        println("🗓️ Agrupando ${registros.size} registros por días...")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE d/MM", Locale("es", "ES"))

        // ← OBTENER LA FECHA DE HOY A LAS 00:00
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val hoyInicio = calendar.time

        println("📅 Filtrando registros desde: ${dateFormat.format(hoyInicio)}")

        val mapaPronosticos = mutableMapOf<String, MutableList<RegistroPronostico>>()

        for (registro in registros) {
            try {
                val fecha = dateFormat.parse(registro.datetime)
                if (fecha != null) {
                    // ← FILTRAR: Solo incluir si la fecha es HOY o posterior
                    if (fecha.before(hoyInicio)) {
                        println("   ⏭️ Saltando registro antiguo: ${registro.datetime}")
                        continue
                    }

                    val nombreDia = dayFormat.format(fecha)

                    if (!mapaPronosticos.containsKey(nombreDia)) {
                        mapaPronosticos[nombreDia] = mutableListOf()
                    }
                    mapaPronosticos[nombreDia]?.add(registro)
                    println("   ➕ Añadido a: $nombreDia")
                }
            } catch (e: Exception) {
                println("⚠️ Error parseando fecha: ${registro.datetime}")
                e.printStackTrace()
            }
        }

        pronosticosPorDia.clear()

        println("📅 Días encontrados: ${mapaPronosticos.size}")

        for ((dia, registrosDia) in mapaPronosticos) {
            val tempMax = registrosDia.maxOfOrNull { it.temp } ?: 0.0
            val tempMin = registrosDia.minOfOrNull { it.temp } ?: 0.0

            val iconoMasFrecuente = registrosDia
                .groupingBy { it.description }
                .eachCount()
                .maxByOrNull { it.value }?.key ?: ""

            println("   📆 $dia: Max=${tempMax}°, Min=${tempMin}°, ${registrosDia.size} horas")

            pronosticosPorDia.add(
                DiaPronostico(
                    nombreDia = dia,
                    tempMax = tempMax,
                    tempMin = tempMin,
                    descripcionGeneral = iconoMasFrecuente,
                    registrosPorHora = registrosDia
                )
            )
        }

        println("✅ Total días en lista: ${pronosticosPorDia.size}")

        runOnUiThread {
            adapterDias.notifyDataSetChanged()

            if (pronosticosPorDia.isEmpty()) {
                Toast.makeText(this, "No hay pronósticos disponibles", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Modelo de datos para un día completo
data class DiaPronostico(
    val nombreDia: String,
    val tempMax: Double,
    val tempMin: Double,
    val descripcionGeneral: String,
    val registrosPorHora: List<RegistroPronostico>
)

// Modelo de datos para cada registro de 3 horas
data class RegistroPronostico(
    val datetime: String,
    val description: String,
    val icon: String,
    val temp: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windDeg: Int,
    val pop: Int,
    val pressure: Int
)