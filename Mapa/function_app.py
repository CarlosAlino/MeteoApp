import azure.functions as func
import requests
import os
import logging

app = func.FunctionApp(http_auth_level=func.AuthLevel.ANONYMOUS)

# EJEMPLO DE URL A LLAMAR DESDE AZURE:
# https://{tu-funcion}.azurewebsites.net/api/get_map?op=CL&z=3&x=4&y=2

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
    url = f"http://maps.openweathermap.org/maps/2.0/weather/{op}/{z}/{x}/{y}?appid={api_key}"
    logging.info(f"Llamando a la API de OpenWeather: {url}")

    # --- 4. Llamada HTTP ---
    response = requests.get(url)

    if response.status_code != 200:
        return func.HttpResponse(
            f"Error al obtener mapa: {response.status_code} - {response.text}",
            status_code=response.status_code
        )

    # --- 5. Devolver imagen ---
    return func.HttpResponse(
        body=response.content,
        mimetype="image/png",
        status_code=200
    )
