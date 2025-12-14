"""
Azure Function App para recopilación de predicciones meteorológicas
=====================================================================
Esta función implementa un sistema de consulta automática de predicciones
meteorológicas a 5 días vista (en intervalos de 3 horas) desde OpenWeather API.

Las predicciones se organizan en dos niveles:
- Standard: Primeras 17 predicciones (aprox. primeros 2 días)
- Premium: Predicciones restantes (días 3-5)

"""

import azure.functions as func
import logging
import os
import json
from google.cloud import firestore
from google.oauth2 import service_account
import requests


# -------------------------------------------------------------------
# 🔥INICIALIZACIÓN DE FIRESTORE
# -------------------------------------------------------------------

def init_firestore():

    # Recuperar credenciales de las variables de entorno
    # El replace es necesario porque Azure almacena saltos de línea como \\n
    private_key = os.environ["FIREBASE_PRIVATE_KEY"].replace("\\n", "\n")
    client_email = os.environ["FIREBASE_CLIENT_EMAIL"]
    project_id = os.environ["FIREBASE_PROJECT_ID"]

    # Construir diccionario de credenciales según el formato de Google Cloud
    credentials_dict = {
        "type": "service_account",
        "project_id": project_id,
        "private_key_id": "dummy",
        "private_key": private_key,
        "client_email": client_email,
        "client_id": "dummy",
        "auth_uri": "https://accounts.google.com/o/oauth2/auth",
        "token_uri": "https://oauth2.googleapis.com/token",
        "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
        "client_x509_cert_url": ""
    }

    # Crear objeto de credenciales desde el diccionario
    credentials = service_account.Credentials.from_service_account_info(credentials_dict)
    
    # Retornar cliente de Firestore autenticado
    return firestore.Client(project=project_id, credentials=credentials)

# Inicializar la base de datos globalmente (se ejecuta una sola vez al cargar la función)
db = init_firestore()


# -------------------------------------------------------------------
# 📆 OBTENCIÓN DE PREDICCIONES METEOROLÓGICAS (5 días · 3h)
# -------------------------------------------------------------------

def ObtenerPrediccion_3h(city):
    # Obtener la API key desde variables de entorno
    API_KEY = os.environ["OPENWEATHER_API_KEY"]

    # Construir URL de la petición con parámetros:
    # - q: nombre de la ciudad
    # - appid: clave de API
    # - units=metric: temperaturas en Celsius, velocidad en m/s
    # - lang=es: descripciones del clima en español
    url = f"https://api.openweathermap.org/data/2.5/forecast?q={city}&appid={API_KEY}&units=metric&lang=es"

    # Realizar petición GET a la API
    r = requests.get(url)

    # Verificar si la petición fue exitosa
    if r.status_code != 200:
        raise Exception("OpenWeather forecast error: " + r.text)

    # Retornar la lista de 40 predicciones
    return r.json()["list"] 


# ===========================================================================
# ALMACENAMIENTO EN FIRESTORE
# ===========================================================================
def sobreescribirDatos(city: str, forecast: list):
    """
    Sobrescribe SIEMPRE los mismos documentos:
    {city}/forecast/items/0..39
    """

    # Iterar sobre cada predicción en la lista
    for idx, entry in enumerate(forecast):

        # El ID del documento es el índice como string
        doc_id = str(idx)

        # Extraer y estructurar los datos relevantes de cada predicción
        data = {
            "index": idx,
            "datetime": entry.get("dt_txt"),
            "temp": entry["main"].get("temp"),
            "pressure": entry["main"].get("pressure"),
            "humidity": entry["main"].get("humidity"),
            "description": entry["weather"][0].get("description"),
            "icon": entry["weather"][0].get("icon"),
            "wind_speed": entry["wind"].get("speed"),
            "wind_deg": entry["wind"].get("deg"),
            "pop": entry.get("pop", 0),  # Probabilitat real (0–1)
            "timestamp_request": firestore.SERVER_TIMESTAMP,
        }

        # Decisión de almacenamiento según el índice:
        # Índices 0-16: Predicciones de corto plazo (Standard)
        # Índices 17+: Predicciones de medio plazo (Premium)
        if int(doc_id)<=16:
            # Guardar en colección Standard (primeros ~2 días)
            db.collection(city).document("Predicción").collection("Standard").document(doc_id).set(data)
        else:
            # Guardar en colección Premium (días 3-5)
            db.collection(city).document("Predicción").collection("Premium").document(doc_id).set(data)


app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

# -------------------------------------------------------------------
# ⏱️ TIMER TRIGGER: Ejecución automática cada 3 horas
# -------------------------------------------------------------------
@app.timer_trigger(
    schedule="0 0 */3 * * *",
    arg_name="myTimer",
    run_on_startup=False,
    use_monitor=False
)
def timer_trigger(myTimer: func.TimerRequest) -> None:
    logging.info("⏱️ Timer trigger ejecutado")

    # Lista de ciudades a monitorear
    cities = ["Madrid", "Pollensa", "Palma", "Inca", "Manacor", "Campos", "Soller", "Helsinki", "Melbourne"]

    try:
        # Iterar sobre cada ciudad
        for city in cities:
            # Obtener predicción meteorológica a 5 días (40 puntos de datos)
            forecast = ObtenerPrediccion_3h(city)
            # Guardar predicciones en Firestore sobreescribiendo los anteriores
            sobreescribirDatos(city, forecast)

            logging.info(f"✔ Guardados weather + forecast para {city}")

    except Exception as e:
        # Capturar cualquier error durante el proceso
        logging.error(f"❌ Error: {e}")



