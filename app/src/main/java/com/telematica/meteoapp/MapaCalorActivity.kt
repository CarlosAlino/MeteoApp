package com.telematica.meteoapp


import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.tileprovider.MapTileProviderBasic
import com.google.android.material.floatingactionbutton.FloatingActionButton
class MapaCalorActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var capaActual: TilesOverlay? = null
    private var tileProviderActual: MapTileProviderBasic? = null
    private lateinit var locationOverlay: MyLocationNewOverlay

    companion object {
        private const val TAG = "MapaCalorActivity"
        private const val LOCATION_PERMISSION_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar OSMDroid de forma óptima
        Configuration.getInstance().load(
            this,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        Configuration.getInstance().userAgentValue = packageName

        // Configuración ÓPTIMA para capas meteorológicas
        Configuration.getInstance().apply {
            // Sin caché para datos meteorológicos (siempre actuales)
            expirationExtendedDuration = 0L
            expirationOverrideDuration = 0L

            // Aumentar paralelismo para carga más rápida
            tileDownloadThreads = 12  // Más threads para descargas simultáneas
            tileDownloadMaxQueueSize = 60

            // Gestión eficiente de tiles
            tileFileSystemThreads = 4
            tileFileSystemMaxQueueSize = 40

            // Cache en memoria más grande para tiles meteorológicos
            cacheMapTileCount = 16  // Más tiles en memoria
        }

        setContentView(R.layout.activity_mapa_calor)

        mapView = findViewById(R.id.mapView)
        configurarMapa()

        solicitarPermisosUbicacion()

        // Configurar botones
        findViewById<Button>(R.id.btnNubes).setOnClickListener {
            cambiarCapa("clouds_new", "Nubes")
        }

        findViewById<Button>(R.id.btnTemperatura).setOnClickListener {
            cambiarCapa("temp_new", "Temperatura")
        }

        findViewById<Button>(R.id.btnPrecipitacion).setOnClickListener {
            cambiarCapa("precipitation_new", "Precipitación")
        }

        findViewById<Button>(R.id.btnPresion).setOnClickListener {
            cambiarCapa("pressure_new", "Presión")
        }

        findViewById<Button>(R.id.btnViento).setOnClickListener {
            cambiarCapa("wind_new", "Viento")
        }

        // Configurar botón de ubicación
        findViewById<FloatingActionButton>(R.id.btnUbicacion).setOnClickListener {
            centrarEnUbicacion()
        }

        // Cargar capa inicial
        cambiarCapa("temp_new", "Temperatura")
    }

    /**
     * Centra el mapa en la ubicación actual del usuario
     */
    private fun centrarEnUbicacion() {
        if (!::locationOverlay.isInitialized) {
            Toast.makeText(this, "Esperando ubicación GPS...", Toast.LENGTH_SHORT).show()
            return
        }

        val miPosicion = locationOverlay.myLocation
        if (miPosicion != null) {
            mapView.controller.animateTo(miPosicion)

            // Si el zoom es muy bajo, acercarse
            if (mapView.zoomLevelDouble < 12.0) {
                mapView.controller.setZoom(12.0)
            }

            Log.d(TAG, "Recentrado en ubicación: $miPosicion")
            Toast.makeText(this, "Centrado en tu ubicación", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No hay ubicación disponible")
        }
    }

    private fun configurarMapa() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        val mapController = mapView.controller
        mapController.setZoom(6.0)

        val startPoint = GeoPoint(40.4168, -3.7038)
        mapController.setCenter(startPoint)

        mapView.minZoomLevel = 2.0
        mapView.maxZoomLevel = 18.0
        mapView.setMultiTouchControls(true)

        // ===== NUEVO: Configurar límites del mapa =====

        // Establecer los límites del mundo (no scroll infinito)
        mapView.setScrollableAreaLimitDouble(
            org.osmdroid.util.BoundingBox(
                85.0,   // Latitud norte máxima
                180.0,  // Longitud este máxima
                -85.0,  // Latitud sur máxima
                -180.0  // Longitud oeste máxima
            )
        )

        // Habilitar el límite de área desplazable
        mapView.isHorizontalMapRepetitionEnabled = false  // No repetir mapa horizontalmente
        mapView.isVerticalMapRepetitionEnabled = false    // No repetir mapa verticalmente
        mapView.setScrollableAreaLimitLatitude(
            org.osmdroid.views.MapView.getTileSystem().maxLatitude,
            org.osmdroid.views.MapView.getTileSystem().minLatitude,
            0
        )
    }

    private fun solicitarPermisosUbicacion() {
        val permisosNecesarios = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permisosFaltantes = permisosNecesarios.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permisosFaltantes.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permisosFaltantes.toTypedArray(),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            configurarUbicacion()
        }
    }

    private fun configurarUbicacion() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()

        // Crear el punto de ubicación (círculo azul)
        val dotBitmap = crearIconoPunto()
        locationOverlay.setPersonIcon(dotBitmap)

        locationOverlay.setPersonHotspot(24.0f, 24.0f) // Centro del icono

        mapView.overlays.add(0, locationOverlay)

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                val miPosicion = locationOverlay.myLocation
                if (miPosicion != null) {
                    mapView.controller.animateTo(miPosicion)
                    mapView.controller.setZoom(12.0)
                    Log.d(TAG, "Mapa centrado en ubicación del usuario: $miPosicion")
                }
            }
        }

        Log.d(TAG, "Ubicación configurada correctamente")
    }

    /**
     * Crea el icono del punto azul
     */
    private fun crearIconoPunto(): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        paint.isAntiAlias = true

        // Círculo exterior (borde blanco)
        paint.color = android.graphics.Color.WHITE
        paint.style = android.graphics.Paint.Style.FILL
        canvas.drawCircle(24f, 24f, 22f, paint)

        // Círculo interior (azul Google Maps)
        paint.color = android.graphics.Color.parseColor("#4285F4")
        canvas.drawCircle(24f, 24f, 18f, paint)

        // Punto central (blanco)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(24f, 24f, 8f, paint)

        return bitmap
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                configurarUbicacion()
                Toast.makeText(this, "Permisos de ubicación concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Se necesitan permisos de ubicación para mostrar tu posición", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cambiarCapa(nuevaCapa: String, nombreCapa: String) {
        Log.d(TAG, "=== Cambiando a capa: $nuevaCapa ===")

        try {
            // PASO 1: Eliminar la capa anterior COMPLETAMENTE
            capaActual?.let { overlay ->
                Log.d(TAG, "Eliminando capa anterior del mapa")
                mapView.overlays.remove(overlay)
            }

            // PASO 2: Detener y limpiar el tile provider anterior
            tileProviderActual?.let { provider ->
                Log.d(TAG, "Limpiando tile provider anterior")
                provider.detach()
                provider.clearTileCache()

                // Limpiar TODA la caché de tiles
                provider.tileSource?.let { tileSource ->
                    org.osmdroid.tileprovider.modules.SqlTileWriter().purgeCache(tileSource.name())  // <- CAMBIO AQUÍ
                }
            }

            // PASO 3: Limpiar referencias
            capaActual = null
            tileProviderActual = null

            // PASO 4: Forzar limpieza de memoria
            System.gc()

            Log.d(TAG, "Overlays restantes después de limpieza: ${mapView.overlays.size}")

            // PASO 5: Crear NUEVA capa meteorológica
            val weatherTileSource = WeatherTileSource(nuevaCapa)

            // Crear tile provider con configuración optimizada
            val tileProvider = MapTileProviderBasic(
                applicationContext,
                weatherTileSource
            )

            // Asegurar que la caché está limpia
            tileProvider.clearTileCache()

            // PASO 6: Crear el overlay de tiles
            val tilesOverlay = TilesOverlay(tileProvider, this)
            tilesOverlay.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            tilesOverlay.setColorFilter(null)

            // PASO 7: Guardar referencias ANTES de añadir al mapa
            tileProviderActual = tileProvider
            capaActual = tilesOverlay

            // PASO 8: Añadir la capa AL FINAL (después del locationOverlay)
            mapView.overlays.add(tilesOverlay)

            Log.d(TAG, "Nueva capa añadida. Total overlays: ${mapView.overlays.size}")

            // PASO 9: Refrescar el mapa de forma agresiva
            mapView.invalidate()

            // Forzar redibujado inmediato
            mapView.postInvalidate()

            // Trigger para recargar tiles visibles
            mapView.postDelayed({
                mapView.invalidate()
            }, 100)

            Toast.makeText(this, "Capa: $nombreCapa", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "=== Cambio de capa completado ===")

        } catch (e: Exception) {
            Log.e(TAG, "ERROR al cambiar capa: ${e.message}", e)
            Toast.makeText(this, "Error al cargar capa: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        if (::locationOverlay.isInitialized) {
            locationOverlay.enableMyLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()

        if (::locationOverlay.isInitialized) {
            locationOverlay.disableMyLocation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Limpiar recursos
        capaActual?.let { overlay ->
            mapView.overlays.remove(overlay)
        }

        tileProviderActual?.let { provider ->
            provider.detach()
            provider.clearTileCache()
        }

        mapView.onDetach()
    }
}

class WeatherTileSource(private val operacion: String) :
    org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase(
        "WeatherTiles_$operacion",  // Nombre único por capa
        0, 18, 256, ".png",
        arrayOf("")
    ) {

    companion object {
        private const val TAG = "WeatherTileSource"
        private const val BASE_URL = "https://mapas-bqfqfpc7h6avb6bv.spaincentral-01.azurewebsites.net"
    }

    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
        val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
        val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)

        val url = "$BASE_URL/api/get_map?op=$operacion&z=$zoom&x=$x&y=$y"

        return url
    }
}