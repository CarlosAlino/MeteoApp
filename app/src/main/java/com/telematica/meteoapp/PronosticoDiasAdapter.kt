package com.telematica.meteoapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PronosticoDiasAdapter(
    private val dias: List<DiaPronostico>
) : RecyclerView.Adapter<PronosticoDiasAdapter.DiaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dia_pronostico, parent, false)
        return DiaViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiaViewHolder, position: Int) {
        holder.bind(dias[position])
    }

    override fun getItemCount() = dias.size

    class DiaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvNombreDia: TextView = itemView.findViewById(R.id.tvNombreDia)
        private val tvTempMax: TextView = itemView.findViewById(R.id.tvTempMax)
        private val tvTempMin: TextView = itemView.findViewById(R.id.tvTempMin)
        private val imgIconoDia: ImageView = itemView.findViewById(R.id.imgIconoDia)
        private val recyclerViewHoras: RecyclerView = itemView.findViewById(R.id.recyclerViewHoras)

        fun bind(dia: DiaPronostico) {
            tvNombreDia.text = dia.nombreDia
            tvTempMax.text = "${dia.tempMax.toInt()}°"
            tvTempMin.text = "${dia.tempMin.toInt()}°"

            // Asignar icono según descripción
            imgIconoDia.setImageResource(obtenerIconoClima(dia.descripcionGeneral))

            // Configurar RecyclerView horizontal de horas
            val adapterHoras = PronosticoHorasAdapter(dia.registrosPorHora)
            recyclerViewHoras.layoutManager = LinearLayoutManager(
                itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            recyclerViewHoras.adapter = adapterHoras
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