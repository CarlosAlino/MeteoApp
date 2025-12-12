package com.telematica.meteoapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

class PronosticoPremiumActivity : AppCompatActivity() {

    private lateinit var recyclerViewDias: RecyclerView
    private lateinit var adapterDias: PronosticoDiasAdapter
    private val db = Firebase.firestore
    private val pronosticosPorDia = mutableListOf<DiaPronostico>()

    private var nombreMunicipio: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pronostico)

        // Obtener municipio del intent
        nombreMunicipio = intent.getStringExtra("municipio") ?: ""

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
        findViewById<View>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        cargarPronosticosPremium()
    }

    private fun cargarPronosticosPremium() {
        println("🌟 Cargando pronóstico PREMIUM de 5 días para: $nombreMunicipio")

        val registrosCombinados = mutableListOf<RegistroPronostico>()
        var cargasCompletadas = 0

        // Cargar primero la colección Standard (documentos 0-16, primeros 3 días)
        db.collection(nombreMunicipio)
            .document("Predicción")
            .collection("Standard")
            .get()
            .addOnSuccessListener { documentsStandard ->
                println("✅ Standard recibidos: ${documentsStandard.size()} documentos")

                for (doc in documentsStandard) {
                    val registro = crearRegistroDesdeDocumento(doc)
                    if (registro != null) {
                        registrosCombinados.add(registro)
                    }
                }

                cargasCompletadas++
                verificarYProcesar(cargasCompletadas, registrosCombinados)
            }
            .addOnFailureListener { e ->
                println("❌ Error cargando Standard: ${e.message}")
                cargasCompletadas++
                verificarYProcesar(cargasCompletadas, registrosCombinados)
            }

        // Cargar después la colección Premium (documentos 17-39, siguientes 2 días)
        db.collection(nombreMunicipio)
            .document("Predicción")
            .collection("Premium")
            .get()
            .addOnSuccessListener { documentsPremium ->
                println("✅ Premium recibidos: ${documentsPremium.size()} documentos")

                for (doc in documentsPremium) {
                    val registro = crearRegistroDesdeDocumento(doc)
                    if (registro != null) {
                        registrosCombinados.add(registro)
                    }
                }

                cargasCompletadas++
                verificarYProcesar(cargasCompletadas, registrosCombinados)
            }
            .addOnFailureListener { e ->
                println("❌ Error cargando Premium: ${e.message}")
                cargasCompletadas++
                verificarYProcesar(cargasCompletadas, registrosCombinados)
            }
    }

    private fun crearRegistroDesdeDocumento(doc: com.google.firebase.firestore.DocumentSnapshot): RegistroPronostico? {
        return try {
            val datetime = doc.getString("datetime") ?: ""
            val description = doc.getString("description") ?: ""
            val temp = doc.getDouble("temp") ?: 0.0

            if (datetime.isEmpty()) {
                println("⚠️ Documento ${doc.id} sin datetime válido")
                return null
            }

            RegistroPronostico(
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
        } catch (e: Exception) {
            println("⚠️ Error procesando documento ${doc.id}: ${e.message}")
            null
        }
    }

    private fun verificarYProcesar(cargasCompletadas: Int, registros: MutableList<RegistroPronostico>) {
        // Esperar a que ambas colecciones se hayan cargado
        if (cargasCompletadas >= 2) {
            println("📊 Total registros combinados: ${registros.size}")

            if (registros.isEmpty()) {
                Toast.makeText(
                    this,
                    "No hay pronósticos disponibles para $nombreMunicipio",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Ordenar todos los registros por fecha
            registros.sortBy { it.datetime }

            println("🗓️ Procesando ${registros.size} registros (5 días)...")
            agruparPorDias(registros)
        }
    }

    private fun agruparPorDias(registros: List<RegistroPronostico>) {
        println("🗓️ Agrupando ${registros.size} registros por días...")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE d/MM", Locale("es", "ES"))

        val mapaPronosticos = LinkedHashMap<String, MutableList<RegistroPronostico>>()

        for (registro in registros) {
            try {
                val fecha = dateFormat.parse(registro.datetime)
                if (fecha != null) {
                    val nombreDia = dayFormat.format(fecha)

                    if (!mapaPronosticos.containsKey(nombreDia)) {
                        mapaPronosticos[nombreDia] = mutableListOf()
                    }
                    mapaPronosticos[nombreDia]?.add(registro)
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
                Toast.makeText(this, "No se pudieron procesar los pronósticos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "✨ Pronóstico Premium de ${pronosticosPorDia.size} días cargado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}