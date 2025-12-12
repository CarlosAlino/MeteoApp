package com.telematica.meteoapp

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import okhttp3.*
import java.io.IOException


class MapaSateliteActivity : AppCompatActivity() {
    private lateinit var imgMapa: ImageView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapa_prueba)

        imgMapa = findViewById(R.id.imgMapa)

        cargarMapaDesdeAzure(
            op = "CL",   // nubes, por ejemplo
            z = 0,
            x = 0,
            y = 0
        )
    }

    private fun cargarMapaDesdeAzure(op: String, z: Int, x: Int, y: Int) {
        val url = "https://mapas-bqfqfpc7h6avb6bv.spaincentral-01.azurewebsites.net/api/get_map" +
                "?op=$op&z=$z&x=$x&y=$y"


        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    println("Error servidor: ${response.code}")
                    return
                }

                val body = response.body
                if (body == null) {
                    println("Body es null")
                    return
                }

                val bytes = body.bytes()

                // Debug para confirmar que hay contenido
                println("Bytes recibidos: ${bytes.size}")

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bitmap == null) {
                    println("No se pudo decodificar la imagen")
                    return
                }

                runOnUiThread {
                    imgMapa.setImageBitmap(bitmap)
                }
            }

        })
    }
}