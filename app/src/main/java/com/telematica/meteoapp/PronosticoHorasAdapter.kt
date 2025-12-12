package com.telematica.meteoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PronosticoHorasAdapter(
    private val registros: List<RegistroPronostico>
) : RecyclerView.Adapter<PronosticoHorasAdapter.HoraViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HoraViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hora_pronostico, parent, false)
        return HoraViewHolder(view)
    }

    override fun onBindViewHolder(holder: HoraViewHolder, position: Int) {
        holder.bind(registros[position])
    }

    override fun getItemCount() = registros.size

    class HoraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHora: TextView = itemView.findViewById(R.id.tvHora)
        private val tvTemp: TextView = itemView.findViewById(R.id.tvTemp)
        private val imgIconoHora: ImageView = itemView.findViewById(R.id.imgIconoHora)
        private val tvViento: TextView = itemView.findViewById(R.id.tvViento)
        private val imgViento: ImageView = itemView.findViewById(R.id.imgViento)

        fun bind(registro: RegistroPronostico) {
            // Extraer hora del datetime
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val horaFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            try {
                val fecha = dateFormat.parse(registro.datetime)
                if (fecha != null) {
                    tvHora.text = horaFormat.format(fecha)
                }
            } catch (e: Exception) {
                tvHora.text = registro.datetime.substring(11, 16)
            }

            tvTemp.text = "${registro.temp.toInt()}°"
            tvViento.text = "${registro.windSpeed} km/h"

            // Asignar icono según descripción
            imgIconoHora.setImageResource(obtenerIconoClima(registro.description))

            // Rotar flecha del viento según dirección
            imgViento.rotation = registro.windDeg.toFloat()
        }

        private fun obtenerIconoClima(descripcion: String): Int {
            return when {
                descripcion.contains("despejado", ignoreCase = true) ||
                        descripcion.contains("clear", ignoreCase = true) ||
                        descripcion.contains("soleado", ignoreCase = true) ->
                    R.drawable.ic_sun

                descripcion.contains("nubes", ignoreCase = true) ||
                        descripcion.contains("cloud", ignoreCase = true) ||
                        descripcion.contains("nublado", ignoreCase = true) ->
                    R.drawable.ic_cloud

                descripcion.contains("parcialmente", ignoreCase = true) ||
                        descripcion.contains("partly", ignoreCase = true) ->
                    R.drawable.ic_cloud_sun

                descripcion.contains("lluvia", ignoreCase = true) ||
                        descripcion.contains("rain", ignoreCase = true) ||
                        descripcion.contains("ligera", ignoreCase = true) ->
                    R.drawable.ic_rain

                descripcion.contains("tormenta", ignoreCase = true) ||
                        descripcion.contains("storm", ignoreCase = true) ||
                        descripcion.contains("thunder", ignoreCase = true) ->
                    R.drawable.ic_storm

                descripcion.contains("nieve", ignoreCase = true) ||
                        descripcion.contains("snow", ignoreCase = true) ->
                    R.drawable.ic_snow

                descripcion.contains("niebla", ignoreCase = true) ||
                        descripcion.contains("fog", ignoreCase = true) ||
                        descripcion.contains("bruma", ignoreCase = true) ->
                    R.drawable.ic_fog

                else -> R.drawable.ic_cloud_sun
            }
        }
    }
}