"""
Azure Function App - Proxy para Tiles de Mapas Meteorológicos
==============================================================
Esta función implementa un proxy HTTP que actúa como intermediario entre
la aplicación cliente y la API de mapas de OpenWeather.

Funcionalidad principal:
- Recibe peticiones de tiles de mapa desde el cliente
- Gestiona timestamps para datos actuales y predicciones
- Optimiza conexiones mediante pooling HTTP
- Devuelve imágenes PNG de tiles meteorológicos
"""

import azure.functions as func
import requests
import os
import logging
from datetime import datetime

app = func.FunctionApp(http_auth_level=func.AuthLevel.ANONYMOUS)

# ===========================================================================
# OPTIMIZACIÓN DE CONEXIONES HTTP
# ===========================================================================
"""
Pool de conexiones para mejorar el rendimiento:
- Evita crear nuevas conexiones TCP para cada petición
- Reutiliza conexiones existentes (keep-alive)
- Reduce latencia significativamente en peticiones repetidas
"""

# Crear sesión global que se reutiliza entre todas las peticiones
session = requests.Session()
# Configurar adaptador HTTP con pool de conexiones optimizado
adapter = requests.adapters.HTTPAdapter(
    pool_connections=100, # Número de pools de conexión a mantener
    pool_maxsize=100,     # Máximo de conexiones por pool
    max_retries=2         # Reintentos automáticos en caso de fallo
)

# Montar el adaptador para protocolos HTTP y HTTPS
session.mount('https://', adapter)
session.mount('http://', adapter)

# ===========================================================================
# MAPEO DE CAPAS METEOROLÓGICAS
# ===========================================================================
LAYER_MAPPING = {
    'temp_new': 'TA2',          # Temperatura
    'clouds_new': 'CL',         # Nubes
    'precipitation_new': 'PR0', # Precipitación
    'pressure_new': 'APM',      # Presión atmosférica
    'wind_new': 'WND',          # Viento
}

# ===========================================================================
# PROXY DE MAPAS
# ===========================================================================
@app.route(route="get_map", methods=["GET"])
def get_map(req: func.HttpRequest) -> func.HttpResponse:
    logging.info("Solicitud recibida para obtener mapa OpenWeather")

    # Extraer parámetros de la URL
    op = req.params.get("op")   # Tipo de capa (layer)
    z = req.params.get("z")     # Zoom
    x = req.params.get("x")     # Coordenada X
    y = req.params.get("y")     # Coordenada Y
    timestamp = req.params.get("timestamp")

    # Validar que los parámetros obligatorios estén presentes
    if not (op and z and x and y):
        return func.HttpResponse(
            "Faltan parámetros. Usa: ?op={layer}&z={zoom}&x={x}&y={y}&timestamp={unix_time}",
            status_code=400
        )
    
    # Leer API key desde variables de entorno de Azure
    api_key = os.getenv("OPENWEATHER_API_KEY")
    if not api_key:
        return func.HttpResponse(
            "Falta la variable de entorno OPENWEATHER_API_KEY",
            status_code=500
        )

    # Convertir nombre de capa de API 1.0 a API 2.0
    # Si la capa no existe en el mapeo, usar TA2 (temperatura) como default
    layer_2_0 = LAYER_MAPPING.get(op, 'TA2')
    
    logging.info(f"📊 Capa solicitada: {op} → Capa API 2.0: {layer_2_0}")
    
    # ---  Determinar timestamp ---
    if timestamp:
        # Timestamp específico para pronóstico
        ts_int = int(timestamp)
    else:
        # Timestamp actual para datos actuales
        ts_int = int(datetime.now().timestamp())
    
    # Redondear timestamp a horas completas (OpenWeather usa datos cada hora)
    ts_rounded = (ts_int // 3600) * 3600
    
    # --- Construir URL con API 2.0 ---
    url = f"http://maps.openweathermap.org/maps/2.0/weather/{layer_2_0}/{z}/{x}/{y}?date={ts_rounded}&opacity=0.5&appid={api_key}"
    
    fecha_str = datetime.fromtimestamp(ts_rounded).strftime('%Y-%m-%d %H:%M:%S')
    tipo = "actual" if not timestamp else "pronóstico"
    
    logging.info(f"API 2.0 ({tipo}) - Layer: {layer_2_0}, Timestamp: {ts_rounded}")
    logging.info(f"Fecha/hora: {fecha_str}")
    logging.info(f"URL: {url}")

# -----------------------------------------------------------------------
# REALIZAR PETICIÓN HTTP A OPENWEATHER
# -----------------------------------------------------------------------    
    try:
        # Realizar petición GET usando la sesión con pool de conexiones
        # timeout=10: Esperar máximo 10 segundos por respuesta
        response = session.get(url, timeout=10)
        
        # Verificar si la petición fue exitosa
        if response.status_code != 200:
            logging.error(f"Error {response.status_code}: {response.text}")
            return func.HttpResponse(
                f"Error al obtener mapa: {response.status_code} - {response.text}",
                status_code=response.status_code
            )

# -----------------------------------------------------------------------
# DEVOLVER IMAGEN AL CLIENTE
# -----------------------------------------------------------------------
        
        # Devolver la imagen PNG con headers de optimización
        return func.HttpResponse(
            body=response.content,  # Contenido binario de la imagen PNG
            mimetype="image/png",   # Tipo MIME correcto
            status_code=200,
            headers={
                # Permitir caché del tile por 5 minutos (300 segundos)
                # Reduce carga del servidor para tiles repetidos
                'Cache-Control': 'public, max-age=300',
                # Mantener conexión abierta para peticiones
                'Connection': 'keep-alive'
            }
        )
        

 # -----------------------------------------------------------------------
# 8. MANEJO DE ERRORES
# -----------------------------------------------------------------------
            
    except requests.exceptions.Timeout:
        logging.error("Timeout en la petición a OpenWeather")
        return func.HttpResponse(
            "Timeout al obtener el mapa",
            status_code=504
        )
    except requests.exceptions.RequestException as e:
        logging.error(f"Error en la petición: {str(e)}")
        return func.HttpResponse(
            f"Error de conexión: {str(e)}",
            status_code=500
        )