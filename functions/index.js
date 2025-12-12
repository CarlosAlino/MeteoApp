// Firebase Cloud Functions para el manejo de PayPal
const functions = require("firebase-functions"); 
const { HttpsError } = require("firebase-functions/v2/https");
const { getFirestore } = require('firebase-admin/firestore');
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const axios = require('axios'); // <-- Usamos Axios de nuevo

// Inicializar Firebase Admin
initializeApp({ credential: applicationDefault() });

const db = getFirestore();

// --- Credenciales de PayPal desde Secret Manager (Accedidas via process.env) ---
const PAYPAL_CLIENT_ID = process.env.PAYPAL_CLIENT_ID;
const PAYPAL_SECRET = process.env.PAYPAL_SECRET;
const PAYPAL_API = 'https://api-m.sandbox.paypal.com'; // Usamos Sandbox

// =================================================================
// FUNCIÓN AUXILIAR: Obtener Token de Acceso
// =================================================================
async function generateAccessToken() {
    try {
        if (!PAYPAL_CLIENT_ID || !PAYPAL_SECRET) {
            console.error("DEBUG CRÍTICO: Secretos de PayPal no cargados. CLIENT_ID cargado:", !!PAYPAL_CLIENT_ID);
            throw new Error("Credenciales de PayPal faltantes en el entorno."); 
        }

        const auth = Buffer.from(`${PAYPAL_CLIENT_ID}:${PAYPAL_SECRET}`).toString('base64');
        const response = await axios.post(
            `${PAYPAL_API}/v1/oauth2/token`,
            'grant_type=client_credentials',
            {
                headers: {
                    'Authorization': `Basic ${auth}`,
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
            }
        );
        return response.data.access_token;
    } catch (error) {
        if (error.response) {
            console.error("--- ERROR PAYPAL RESPUESTA ---");
            console.error("Status:", error.response.status); 
            console.error("Data:", error.response.data);      
            console.error("------------------------------");
        } else {
            console.error("Error sin respuesta HTTP (de red o timeout):", error.message);
        }
        throw new HttpsError('internal', 'No se pudo obtener el token de acceso de PayPal.');
    }
}

// =================================================================
// 1. CREAR ORDEN DE PAGO (Redirección a example.com)
// =================================================================
exports.createPayPalOrder = functions
    .https.onCall(async (data, context) => {
    
    const { userId, price, currency } = data;
    if (!userId || !price || !currency) {
        throw new HttpsError('invalid-argument', 'Faltan campos (userId, price, currency).');
    }

    try {
        const accessToken = await generateAccessToken();
        
        const priceValue = parseFloat(price);
        if (isNaN(priceValue)) {
            throw new HttpsError('invalid-argument', 'El precio no es un número válido.');
        }
        const priceFormatted = priceValue.toFixed(2);


        const orderRequest = {
            intent: 'CAPTURE',
            purchase_units: [
                {
                    reference_id: userId,
                    amount: {
                        currency_code: currency,
                        value: priceFormatted, 
                    },
                    description: "Suscripción Premium MeteoApp",
                },
            ],
            application_context: {
                // ⭐ REDIRECCIÓN A EXAMPLE.COM
                return_url: 'https://example.com', 
                cancel_url: 'https://example.com',
            },
        };

        const response = await axios.post(
            `${PAYPAL_API}/v2/checkout/orders`,
            orderRequest,
            {
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json',
                },
            }
        );

        if (response.data.id) {
            return { orderID: response.data.id };
        } else {
            throw new Error("PayPal no devolvió un ID de orden.");
        }

    } catch (error) {
        console.error("Error al crear orden de PayPal:", error.response ? error.response.data : error.message);
        throw new HttpsError('internal', 'Error al crear la orden del pago.');
    }
});

// =================================================================
// 2. CAPTURAR PAGO
// =================================================================
exports.capturePayPalOrder = functions
    .https.onCall(async (data, context) => {

    const { orderID, userId } = data;
    if (!orderID || !userId) {
        throw new HttpsError('invalid-argument', 'Faltan campos (orderID, userId).');
    }

    try {
        const accessToken = await generateAccessToken();

        // 1. Capturar el pago de PayPal
        const response = await axios.post(
            `${PAYPAL_API}/v2/checkout/orders/${orderID}/capture`,
            {},
            {
                headers: {
                    'Authorization': `Bearer ${accessToken}`,
                    'Content-Type': 'application/json',
                },
            }
        );

        // 2. Verificar el estado de la captura
        if (response.data.status !== 'COMPLETED') {
            throw new Error(`Estado de la orden inesperado: ${response.data.status}`);
        }

        // 3. Si el pago se completó, actualizar Firestore (hacer Premium)
        await db.collection('usuarios').doc(userId).update({
            premium: true,
            premiumSince: new Date(),
        });

        return { success: true, message: "Pago completado y usuario actualizado a Premium." };

    } catch (error) {
        console.error("Error al capturar orden de PayPal o actualizar Firestore:", error.response ? error.response.data : error.message);
        throw new HttpsError('internal', 'Error al capturar la orden del pago.');
    }
});