"""
Azure Function App para recopilación automática de datos meteorológicos
=========================================================================
Este módulo implementa dos funciones principales:
Timer Trigger: Ejecuta consultas periódicas a OpenWeather API cada 60 minutos
"""

import azure.functions as func
import logging
import os
import json
from google.cloud import firestore
from google.oauth2 import service_account
import requests
from datetime import datetime, timezone, timedelta


# ===========================================================================
# INICIALIZACIÓN DE FIRESTORE
# ===========================================================================

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
# 🌤️ OBTENCIÓN DE DATOS METEOROLÓGICOS(OpenWeather)
# -------------------------------------------------------------------

def obtenerDatos(city):
    # Obtener la API key desde variables de entorno
    API_KEY = os.environ["OPENWEATHER_API_KEY"]

    # Construir URL de la petición con parámetros:
    # - q: nombre de la ciudad
    # - appid: clave de API
    # - units=metric: para obtener temperaturas en Celsius
    url = f"https://api.openweathermap.org/data/2.5/weather?q={city}&appid={API_KEY}&units=metric"

    # Realizar petición GET a la API
    r = requests.get(url)

    # Verificar si la petición fue exitosa
    if r.status_code != 200:
        raise Exception("OpenWeather error: " + r.text)
    
    # Definir zona horaria UTC+1 (hora española peninsular)
    utc_plus_1 = timezone(timedelta(hours=1))

    # Parsear respuesta JSON
    data = r.json()
    
    # Extraer y formatear datos relevantes de la respuesta
    return {
        "city": data.get("name", city),
        "temperature": data["main"]["temp"],
        "temp_min": data["main"]["temp_min"],
        "temp_max": data["main"]["temp_max"],
        "pressure": data["main"]["pressure"],
        "humidity": data["main"]["humidity"],
        "wind_speed": data["wind"]["speed"],
        "wind_deg": data["wind"].get("deg"),
        "description": data["weather"][0]["description"],
        "icon": data["weather"][0]["icon"],
        "sunrise": datetime.fromtimestamp(data["sys"]["sunrise"],tz=utc_plus_1).strftime("%H:%M"),
        "sunset": datetime.fromtimestamp(data["sys"]["sunset"],tz=utc_plus_1).strftime("%H:%M")
    }


# -------------------------------------------------------------------
# ⏱️ AZURE FUNCTION APP TIMER TRIGGER (Cada 60 minutos automáticamente)
# -------------------------------------------------------------------

app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

@app.timer_trigger(
    schedule="0 */60 * * * *", # Expresión CRON: cada 60 minutos (en el minuto 0 de cada hora)
    arg_name="myTimer",
    run_on_startup=False,   # No ejecutar al iniciar la función
    use_monitor=False       # No usar monitor de estado (simplifica el despliegue)
)
def timer_trigger(myTimer: func.TimerRequest) -> None:

    logging.info("⏱️ Timer trigger ejecutado")

    # Lista de ciudades a monitorear
    cities = ["Madrid", "Pollensa", "Palma", "Inca",
             "Manacor", "Campos", "Soller","Helsinki", "Melbourne"]

    try:
        # Iterar sobre cada ciudad
        for city in cities:
            # Obtener datos meteorológicos actuales
            weather = obtenerDatos(city)

            # Guardar en Firestore:
            # - Colección: nombre de la ciudad
            # - Documento: "Actual" (siempre sobrescribe con datos más recientes)
            # - Datos: todos los campos del clima + timestamp del servidor
            db.collection(city).document("Actual").set({
                **weather,  # Desempaquetar todos los campos del diccionario weather
                "timestamp": firestore.SERVER_TIMESTAMP # Añadir timestamp del servidor
            })

            logging.info(f"✔ Datos guardados para {city}")

    except Exception as e:
        # Capturar cualquier error durante el proceso
        logging.error(f"❌ Error: {e}")
