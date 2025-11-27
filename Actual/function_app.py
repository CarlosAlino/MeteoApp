import azure.functions as func
import logging
import os
import json
from google.cloud import firestore
from google.oauth2 import service_account
import requests


# -------------------------------------------------------------------
# FIRESTORE INIT
# -------------------------------------------------------------------

def init_firestore():

    private_key = os.environ["FIREBASE_PRIVATE_KEY"].replace("\\n", "\n")
    client_email = os.environ["FIREBASE_CLIENT_EMAIL"]
    project_id = os.environ["FIREBASE_PROJECT_ID"]

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

    credentials = service_account.Credentials.from_service_account_info(credentials_dict)
    return firestore.Client(project=project_id, credentials=credentials)


db = init_firestore()


# -------------------------------------------------------------------
# 🌤️ WEATHER FETCHER (OpenWeather)
# -------------------------------------------------------------------

def get_weather_openweather(city):
    API_KEY = os.environ["OPENWEATHER_API_KEY"]

    url = f"https://api.openweathermap.org/data/2.5/weather?q={city}&appid={API_KEY}&units=metric"

    r = requests.get(url)

    if r.status_code != 200:
        raise Exception("OpenWeather error: " + r.text)

    data = r.json()
    #return data
    # Formateamos a una estructura limpia
    return {
        "city": city,
        "temperature": data["main"]["temp"],
        "humidity": data["main"]["humidity"],
        "description": data["weather"][0]["description"],
        "wind_speed": data["wind"]["speed"]
    }


# -------------------------------------------------------------------
# ⏱️ TIMER TRIGGER (Cada X minutos)
# -------------------------------------------------------------------

app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

@app.timer_trigger(
    schedule="0 */60 * * * *",
    arg_name="myTimer",
    run_on_startup=False,
    use_monitor=False
)
def timer_trigger(myTimer: func.TimerRequest) -> None:

    logging.info("⏱️ Timer trigger ejecutado")

    cities = ["Madrid", "Pollensa", "Palma", "Inca", "Manacor", "Campos", "Soller"]

    try:
        for city in cities:
            weather = get_weather_openweather(city)

            db.collection(city).document("Actual").set({
                **weather,
                "timestamp": firestore.SERVER_TIMESTAMP
            })

            logging.info(f"✔ Datos guardados para {city}")

    except Exception as e:
        logging.error(f"❌ Error: {e}")


# -------------------------------------------------------------------
# 🌍 HTTP trigger (Para pruebas desde navegador o Postman)
# -------------------------------------------------------------------

@app.route(route="test_weather")
def http_trigger(req: func.HttpRequest) -> func.HttpResponse:
    logging.info("➡️ HTTP trigger llamado")

    try:
        city = req.params.get("city", "Madrid")
        weather = get_weather_openweather(city)

        # Devuelve el JSON completo formateado
        return func.HttpResponse(
            json.dumps(weather, indent=4),
            mimetype="application/json",
            status_code=200
        )

    except Exception as e:
        return func.HttpResponse(
            f"Error: {str(e)}",
            status_code=500
        )