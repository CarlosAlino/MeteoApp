package com.telematica.meteoapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.ImageView
import android.widget.PopupMenu
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class MetereologiaBasicActivity : AppCompatActivity() {
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

    private lateinit var textSunriseLabel: TextView
    private lateinit var textSunsetLabel: TextView
    private lateinit var humidityCircle: CirculoHumedadActivity
    private lateinit var textViewViento: TextView
    private lateinit var textViewDireccion: TextView
    private lateinit var textViewPrecipitacion: TextView
    private lateinit var precipicationCircle: CirculoHumedadActivity

    // ⭐ IMPORTANTE: Se elimina la variable btnHacerPremium para evitar el crasheo,
    // ya que el botón solo existe en activity_perfil_usuario.xml.


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_metereologia_basic)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 🔹 Referencias a los TextViews
        textViewWelcome = findViewById(R.id.textViewWelcome)
        textViewTemperature = findViewById(R.id.textViewTemperature)
        textViewTemperatureMax = findViewById(R.id.textViewTemperatureMax)
        textViewTemperatureMin = findViewById(R.id.textViewTemperatureMin)
        menuIcon = findViewById<ImageView>(R.id.menuIcon)
        textSunsetLabel = findViewById<TextView>(R.id.textViewSunset)
        textSunriseLabel = findViewById<TextView>(R.id.textViewSunrise)
        textViewPrecipitacion = findViewById<TextView>(R.id.textViewValorPrecipitacion)
        precipicationCircle = findViewById(R.id.PrecipitacionCircle)
        textViewPresion = findViewById<TextView>(R.id.textViewValorPresion)

        // ⭐ IMPORTANTE: Se ELIMINA la línea findViewById para btn_hacer_premium
        // (Esto resuelve el crasheo).


        // 🔹 Recibir nombre del usuario desde FormActivity
        val userName = intent.getStringExtra("userName") ?: "Piloto"
        email = intent.getStringExtra("email") ?: ""
        municipio = intent.getStringExtra("municipio") ?: "Desconocido"
        pais = intent.getStringExtra("pais") ?: "Desconocido"
        localidad = intent.getStringExtra("localidad") ?: "Desconocido"
        comunidadAutonoma = intent.getStringExtra("comunidadAutonoma") ?: "Desconocido"

        textViewWelcome.text = "Bienvenido, $userName"

        // 🔹 Mostrar datos meteorológicos simulados
        solicitarDatosMetereológicos()

        // ⭐ IMPORTANTE: Se ELIMINA el setOnClickListener para btn_hacer_premium


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

                    val tempMin = document.getDouble("temp_min")?.toInt() ?: 0
                    val tempMax = document.getDouble("temp_max")?.toInt() ?: 0

                    val presion = document.getLong("pressure")?.toInt() ?: 0

                    val sunrise = document.getString("sunrise") ?: "--"
                    val sunset = document.getString("sunset") ?: "--"

                    // Mostrar valores en la UI
                    textViewTemperature.text = "${temperatura} °C"

                    textViewPresion.text = "${presion} hPa"
                    textViewTemperatureMin.text = "$tempMin °C"
                    textViewTemperatureMax.text = "$tempMax °C"

                    textSunriseLabel.text = sunrise
                    textSunsetLabel.text = sunset

                } else {
                    textViewTemperature.text = "-- °C"
                    textViewTemperatureMin.text = "-- °C"
                    textViewTemperatureMax.text = "-- °C"
                    textViewPresion.text = "-- hPa"
                }
            }
            .addOnFailureListener {
                textViewTemperature.text = "ERR"
                textViewPresion.text = "ERR"
            }
    }


    private fun showMenu() {
        val popup = PopupMenu(this, menuIcon)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.menu_perfil -> {
                    // El usuario va a Perfil, donde encontrará el botón de pago
                    startActivity(Intent(this, PerfilUsuarioActivity::class.java))
                    true
                }
                R.id.menu_cambio_region -> {
                    val intentCambioRegion = Intent(this, UbicacionUsuario::class.java)
                    intentCambioRegion.putExtra("email", email)
                    startActivity(intentCambioRegion)
                    true
                }
                R.id.menu_ex3 -> {
                    // Si el pronóstico es la función Premium, puedes aquí poner un Toast
                    // de "Solo para Premium" o redirigir al perfil.
                    val intent = Intent(this, PronosticoActivity::class.java)
                    intent.putExtra("municipio", municipio)
                    intent.putExtra("tipoUsuario", "Standard")
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

}
