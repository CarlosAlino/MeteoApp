import azure.functions as func
import logging
import os
import json
from google.cloud import firestore
from google.oauth2 import service_account
import requests


# -------------------------------------------------------------------
# 🔥 FIRESTORE INIT
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
# 📆 FORECAST (5 días · 3h)
# -------------------------------------------------------------------

def get_forecast_3h(city="Madrid"):
    API_KEY = os.environ["OPENWEATHER_API_KEY"]
    url = f"https://api.openweathermap.org/data/2.5/forecast?q={city}&appid={API_KEY}&units=metric&lang=es"

    r = requests.get(url)
    if r.status_code != 200:
        raise Exception("OpenWeather forecast error: " + r.text)

    return r.json()["list"]   # Las 40 predicciones


def save_forecast_overwrite(city: str, forecast: list):
    """
    Sobrescribe SIEMPRE los mismos documentos:
    {city}/forecast/items/0..39
    """
    for idx, entry in enumerate(forecast):

        doc_id = str(idx)

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
        if int(doc_id)<=16:
            db.collection(city).document("Predicción").collection("Standard").document(doc_id).set(data)
        else:
            db.collection(city).document("Predicción").collection("Premium").document(doc_id).set(data)

# -------------------------------------------------------------------
# ⏱️ TIMER TRIGGER
# -------------------------------------------------------------------

app = func.FunctionApp(http_auth_level=func.AuthLevel.FUNCTION)

@app.timer_trigger(
    schedule="0 0 */3 * * *",
    arg_name="myTimer",
    run_on_startup=False,
    use_monitor=False
)
def timer_trigger(myTimer: func.TimerRequest) -> None:

    logging.info("⏱️ Timer trigger ejecutado")

    cities = ["Madrid", "Pollensa", "Palma", "Inca", "Manacor", "Campos", "Soller"]

    try:
        for city in cities:
            # Predicción 5 días
            forecast = get_forecast_3h(city)
            save_forecast_overwrite(city, forecast)

            logging.info(f"✔ Guardados weather + forecast para {city}")

    except Exception as e:
        logging.error(f"❌ Error: {e}")



