import azure.functions as func
import requests
import os
import logging

app = func.FunctionApp(http_auth_level=func.AuthLevel.ANONYMOUS)

# Crear una sesión global para reutilizar conexiones
session = requests.Session()
adapter = requests.adapters.HTTPAdapter(
    pool_connections=100,
    pool_maxsize=100,
    max_retries=2
)
session.mount('https://', adapter)
session.mount('http://', adapter)

@app.route(route="get_map", methods=["GET"])
def get_map(req: func.HttpRequest) -> func.HttpResponse:
    logging.info("Solicitud recibida para obtener mapa OpenWeather")

    # --- 1. Leer parámetros ---
    op = req.params.get("op")
    z = req.params.get("z")
    x = req.params.get("x")
    y = req.params.get("y")

    if not (op and z and x and y):
        return func.HttpResponse(
            "Faltan parámetros. Usa: ?op={layer}&z={zoom}&x={x}&y={y}",
            status_code=400
        )

    # --- 2. Leer API KEY del entorno ---
    api_key = os.getenv("OPENWEATHER_API_KEY")
    if not api_key:
        return func.HttpResponse(
            "Falta la variable de entorno OPENWEATHER_API_KEY",
            status_code=500
        )

    # --- 3. Construir URL ---
    url = f"https://tile.openweathermap.org/map/{op}/{z}/{x}/{y}.png?appid={api_key}"
    logging.info(f"Llamando a la API de OpenWeather: {url}")

    # --- 4. Llamada HTTP optimizada ---
    try:
        # Usar la sesión global con connection pooling
        response = session.get(url, timeout=5)
        
        if response.status_code != 200:
            logging.error(f"Error {response.status_code}: {response.text}")
            return func.HttpResponse(
                f"Error al obtener mapa: {response.status_code}",
                status_code=response.status_code
            )

        # --- 5. Devolver imagen con headers optimizados ---
        return func.HttpResponse(
            body=response.content,
            mimetype="image/png",
            status_code=200,
            headers={
                'Cache-Control': 'public, max-age=300',  # Cachear 5 minutos
                'Connection': 'keep-alive'
            }
        )
        
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