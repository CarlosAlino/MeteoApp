package com.telematica.meteoapp

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*


class MetereologiaPremiumActivity : AppCompatActivity() {
    private var email: String = ""
    private var municipio: String = ""
    private var pais: String = ""
    private var localidad: String = ""
    private var comunidadAutonoma = ""

    private lateinit var menuIcon: ImageView
    private lateinit var textViewWelcome: TextView
    private lateinit var textViewTemperature: TextView
    private lateinit var textViewTemperatureMax: TextView
    private lateinit var textViewTemperatureMin: TextView
    private lateinit var textViewHumedad: TextView
    private lateinit var textViewPresion: TextView
    private  var textViewDescripcionDelTiempo: String = "despejado"

    private lateinit var textSunriseLabel: TextView
    private lateinit var textSunsetLabel: TextView
    private lateinit var humidityCircle: CirculoHumedadActivity
    private lateinit var textViewViento: TextView
    private lateinit var textViewDireccion: TextView
    private lateinit var textViewPrecipitacion: TextView
    private lateinit var precipicationCircle: CirculoHumedadActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_metereologia_premium)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        humidityCircle = findViewById(R.id.humidityCircle)

        // 🔹 Referencias a los TextViews
        textViewWelcome = findViewById(R.id.textViewWelcome)
        textViewTemperature = findViewById(R.id.textViewTemperature)
        textViewTemperatureMax = findViewById(R.id.textViewTemperatureMax)
        textViewTemperatureMin = findViewById(R.id.textViewTemperatureMin)
        textViewHumedad = findViewById(R.id.textViewValorHumedad)
        textViewPresion = findViewById(R.id.textViewValorPresion)
        menuIcon = findViewById<ImageView>(R.id.menuIcon)
        textSunsetLabel = findViewById<TextView>(R.id.textViewSunset)
        textSunriseLabel = findViewById<TextView>(R.id.textViewSunrise)
        textViewViento = findViewById<TextView>(R.id.textViewValorViento)
        textViewDireccion = findViewById<TextView>(R.id.textViewDireccionViento)
        textViewPrecipitacion = findViewById<TextView>(R.id.textViewValorPrecipitacion)
        precipicationCircle = findViewById(R.id.PrecipitacionCircle)
        //textViewQNH = findViewById(R.id.textViewQNH)

        // 🔹 Recibir nombre del usuario desde FormActivity
        val userName = intent.getStringExtra("userName") ?: "Piloto"
        email = intent.getStringExtra("email") ?: ""
        municipio = intent.getStringExtra("municipio") ?: "Desconocido"
        pais = intent.getStringExtra("pais") ?: "Desconocido"

        textViewWelcome.text = "Bienvenido, $userName"

        // 🔹 Mostrar datos meteorológicos simulados
        solicitarDatosMetereológicos()

        menuIcon.setOnClickListener { showMenu() }
    }

    private fun solicitarDatosMetereológicos() {

        // Instancia de Firestore
        val db = Firebase.firestore

        db.collection(municipio)
            .document("Predicción")
            .collection("Standard")
            .document("0")
            .get()
            .addOnSuccessListener { document ->

                if (document != null && document.exists()) {

                    val precipitacion =(document.getDouble("pop")?.toInt() ?: 0)*100

                    precipicationCircle.setPercentage(precipitacion)
                    textViewPrecipitacion.text = "${precipitacion} %"
                }
            }

        // Referencia: /municipio/Actual
        db.collection(municipio)
            .document("Actual")
            .get()
            .addOnSuccessListener { document ->

                if (document != null && document.exists()) {

                    val temperatura = document.getDouble("temperature")?.toInt() ?: 0
                    val humedad = document.getLong("humidity")?.toInt() ?: 0
                    val tempMin = document.getDouble("temp_min")?.toInt() ?: 0
                    val tempMax = document.getDouble("temp_max")?.toInt() ?: 0

                    val descripcion = document.getString("description") ?: "despejado"
                    textViewDescripcionDelTiempo = descripcion
                    CambioDeFondo()
                    val presion = document.getLong("pressure")?.toInt() ?: 0

                    val viento = document.getDouble("wind_speed") ?: 0.0
                    val gradosDireccion = document.getDouble("wind_direction") ?: 0.0
                    val direccionCardinal = obtenerDireccionCardinal(gradosDireccion)



                    val sunrise = document.getString("sunrise") ?: "--"
                    val sunset = document.getString("sunset") ?: "--"

                    // Mostrar valores en la UI
                    textViewTemperature.text = "${temperatura} °C"
                    textViewHumedad.text = "$humedad %"
                    humidityCircle.setPercentage(humedad)   // Actualiza tu círculo

                    textViewPresion.text = "$presion hPa"
                    textViewTemperatureMin.text = "$tempMin °C"
                    textViewTemperatureMax.text = "$tempMax °C"

                    textViewViento.text = "$viento km/h"
                    textViewDireccion.text = direccionCardinal

                    textSunriseLabel.text = sunrise
                    textSunsetLabel.text = sunset

                } else {
                    textViewTemperature.text = "-- °C"
                    textViewTemperatureMin.text = "-- °C"
                    textViewTemperatureMax.text = "-- °C"
                    textViewHumedad.text = "-- %"
                    textViewPresion.text = "-- hPa"
                }
            }
            .addOnFailureListener {
                textViewTemperature.text = "ERR"
                textViewHumedad.text = "ERR"
                textViewPresion.text = "ERR"
            }
    }


    private fun showMenu() {
        val popup = PopupMenu(this, menuIcon)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.menu_perfil -> {
                    startActivity(Intent(this, PerfilUsuarioActivity::class.java))
                    true
                }
                R.id.menu_cambio_region -> {
                    val intentCambioRegion = Intent(this, UbicacionUsuario::class.java)
                    intentCambioRegion.putExtra("email", email) // 'email' es la variable de clase que recibiste de FormActivity
                    intentCambioRegion.putExtra("municipio",municipio)
                    intentCambioRegion.putExtra("pais",pais)
                    intentCambioRegion.putExtra("comunidadAutonoma",comunidadAutonoma)
                    intentCambioRegion.putExtra("localidad",localidad)
                    startActivity(intentCambioRegion)
                    true
                }
                R.id.menu_mapa_metereologico -> {
                    startActivity(Intent(this, MapaCalorActivity::class.java))
                    true
                }
                R.id.menu_ex3 -> {
                    // En tu MetereologiaBasicActivity o MetereologiaPremiumActivity
                    println("📍 Municipio actual: '$municipio'")
                    val intent = Intent(this, PronosticoPremiumActivity::class.java)  // ← Cambiar aquí
                    intent.putExtra("municipio", municipio)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun obtenerDireccionCardinal(grados: Double): String {
        return when (grados) {
            in 337.5..360.0, in 0.0..22.5 -> "N"
            in 22.5..67.5 -> "NE"
            in 67.5..112.5 -> "E"
            in 112.5..157.5 -> "SE"
            in 157.5..202.5 -> "S"
            in 202.5..247.5 -> "SO"
            in 247.5..292.5 -> "O"
            in 292.5..337.5 -> "NO"
            else -> "--"
        }
    }

    private fun CambioDeFondo() {
        val layoutMain = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)
        val horaActual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val descripcion = textViewDescripcionDelTiempo.lowercase() // YA es un String correcto

        val esDespejado = descripcion.contains("despejado") || descripcion.contains("clear")
        val esNublado = descripcion.contains("nubes") || descripcion.contains("cloud")
        val esLluvia = descripcion.contains("rain") || descripcion.contains("lluvia")
        val esNieve = descripcion.contains("snow") || descripcion.contains("nieve")
        val esNiebla = descripcion.contains("fog") || descripcion.contains("niebla")
        when {
            //NIEVE
            esNieve -> {
                when (horaActual) {
                    in 6..15 -> {
                        layoutMain.setBackgroundResource(R.drawable.nievediatarde)
                        cambiarColorTextos(android.graphics.Color.BLACK)
                    }
                    in 16..19 -> layoutMain.setBackgroundResource(R.drawable.nievediatarde)
                    in 20..23, in 0..5 -> layoutMain.setBackgroundResource(R.drawable.nievenoche)
                }
            }

            // LLUVIA
            esLluvia -> {
                when (horaActual) {
                    in 6..15 -> layoutMain.setBackgroundResource(R.drawable.cielonubladomanana)
                    in 16..19 -> layoutMain.setBackgroundResource(R.drawable.cielonubladotarde)
                    in 20..23, in 0..5 -> layoutMain.setBackgroundResource(R.drawable.cielooscurodespejado)
                }
            }

            //  NIEBLA
            esNiebla -> {
                when (horaActual) {
                    in 6..15 -> layoutMain.setBackgroundResource(R.drawable.niebladia)
                    in 16..19 -> layoutMain.setBackgroundResource(R.drawable.nieblatarde)
                    in 20..23, in 0..5 -> layoutMain.setBackgroundResource(R.drawable.nieblanoche)
                }
            }

            //NUBLADO porble cielo nublado mañana
            esNublado -> {
                when (horaActual) {
                    in 6..15 -> layoutMain.setBackgroundResource(R.drawable.cielonubladomanana)
                    in 16..19 -> layoutMain.setBackgroundResource(R.drawable.cielonubladotarde)
                    in 20..23, in 0..5 -> layoutMain.setBackgroundResource(R.drawable.nieblanoche)
                }
            }

            // DESPEJADO (último)
            esDespejado -> {
                when (horaActual) {
                    in 6..15 -> layoutMain.setBackgroundResource(R.drawable.cieloazuldespejado)
                    in 16..19 -> layoutMain.setBackgroundResource(R.drawable.cielonubladotarde)
                    in 20..23, in 0..5 -> layoutMain.setBackgroundResource(R.drawable.despejadonoche)
                }
            }

            // Por defecto
            else -> {
                layoutMain.setBackgroundResource(R.drawable.nieblanoche)
            }
        }

    }

    private fun cambiarColorTextos(color: Int) {
        textViewWelcome.setTextColor(color)
        textViewTemperature.setTextColor(color)
        textViewTemperatureMax.setTextColor(color)
        textViewTemperatureMin.setTextColor(color)
        textViewHumedad.setTextColor(color)
        textViewPresion.setTextColor(color)
        textSunriseLabel.setTextColor(color)
        textSunsetLabel.setTextColor(color)
        textViewViento.setTextColor(color)
        textViewDireccion.setTextColor(color)
        textViewPrecipitacion.setTextColor(color)
    }
}


