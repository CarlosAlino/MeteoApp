package com.telematica.meteoapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth // ⭐ IMPORTACIÓN NECESARIA
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class HacerPremiumActivity : AppCompatActivity() {

    private lateinit var functions: com.google.firebase.functions.FirebaseFunctions
    private var userId: String? = null // Mantenemos por si lo usas en el futuro, aunque no es la fuente principal
    private lateinit var btnIniciarPago: Button
    private lateinit var auth: FirebaseAuth // ⭐ NUEVO: Variable para Firebase Auth

    // URL base de PayPal para iniciar la interfaz de pago (Sandbox)
    private val PAYPAL_CHECKOUT_URL = "https://www.sandbox.paypal.com/checkoutnow"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hacer_premium)

        // Inicialización de Firebase Services
        functions = Firebase.functions("us-central1")
        auth = FirebaseAuth.getInstance() // ⭐ Inicialización de Auth

        // Obtener el UID de SharedPreferences (Sesión local)
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        userId = sharedPref.getString("userId", null) // Se mantiene la lectura local

        if (userId == null) {
            // Si no hay ID local, verificamos la sesión global de Firebase
            if (auth.currentUser == null) {
                Toast.makeText(this, "Error: Sesión no válida. Vuelve a iniciar sesión.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            // Si Auth sí tiene usuario, lo usamos.
        }

        btnIniciarPago = findViewById<Button>(R.id.btn_confirmar_pago)

        btnIniciarPago.setOnClickListener {
            btnIniciarPago.isEnabled = false
            iniciarFlujoCompletoDePago()
        }

        // ⭐ LÓGICA DE DEEP LINK PARA CAPTURA (onResume y onNewIntent)
    }

    // ⭐ MÉTODOS AÑADIDOS PARA EL DEEP LINK (Captura automática)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Actualiza el Intent
    }

    override fun onResume() {
        super.onResume()

        val uri = intent.data

        if (uri != null && uri.scheme == "meteoapp" && uri.host == "paypal") {

            val orderId = uri.getQueryParameter("token")

            if (orderId != null) {
                Toast.makeText(this, "4. Autorización recibida. Capturando pago...", Toast.LENGTH_LONG).show()
                capturarPagoYFinalizar(orderId)
                intent.data = null
            } else {
                Toast.makeText(this, "Error: Regreso sin token de PayPal. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                btnIniciarPago.isEnabled = true
            }
        }
    }


    // =================================================================
    // FUNCIÓN PRINCIPAL: CREA ORDEN Y ABRE EL NAVEGADOR PARA AUTORIZACIÓN
    // =================================================================
    private fun iniciarFlujoCompletoDePago() {

        // ⭐ VERIFICACIÓN Y OBTENCIÓN DEL UID AUTENTICADO ⭐
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            Toast.makeText(this, "ERROR: Debes iniciar sesión para realizar un pago (Auth no detectado).", Toast.LENGTH_LONG).show()
            btnIniciarPago.isEnabled = true
            return
        }

        // Usamos el UID de Firebase Auth, que es el único que Cloud Functions acepta en el campo context.auth.uid
        val currentUserId = firebaseUser.uid

        Toast.makeText(this, "1. Creando orden de PayPal...", Toast.LENGTH_LONG).show()

        val data = hashMapOf(
            "userId" to currentUserId, // ⭐ Usamos el UID de Firebase Auth
            "price" to "4.99",
            "currency" to "EUR"
        )

        functions
            .getHttpsCallable("createPayPalOrder")
            .call(data)
            .addOnSuccessListener { task: HttpsCallableResult ->
                val resultData = task.getData()
                val orderId = (resultData as? Map<*, *>)?.get("orderID") as? String

                if (orderId != null) {
                    Toast.makeText(this, "2. Orden creada. Abriendo PayPal para autorización...", Toast.LENGTH_LONG).show()

                    // Llamar a la función que abre el navegador
                    lanzarInterfazPago(orderId)

                } else {
                    Toast.makeText(this, "Error: ID de orden no recibida.", Toast.LENGTH_LONG).show()
                    btnIniciarPago.isEnabled = true
                }
            }
            .addOnFailureListener { exception ->
                // Esto debería pasar de PERMISSION_DENIED al error original de PayPal.
                Toast.makeText(this, "Error de CF al crear orden: ${exception.message}", Toast.LENGTH_LONG).show()
                btnIniciarPago.isEnabled = true
            }
    }

    // ⭐ FUNCIÓN: Lanza el navegador para la autorización de PayPal
    private fun lanzarInterfazPago(orderId: String) {
        // La redirección final será a meteoapp://paypal
        val uri = Uri.parse("$PAYPAL_CHECKOUT_URL?token=$orderId")

        val browserIntent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(browserIntent)

        Toast.makeText(this, "Autoriza el pago en el navegador. Volverás a la app.", Toast.LENGTH_LONG).show()
    }


    // =================================================================
    // FUNCIÓN DE CAPTURA (Llamada desde onResume)
    // =================================================================
    private fun capturarPagoYFinalizar(orderId: String) {
        val data = hashMapOf(
            "orderID" to orderId,
            "userId" to auth.currentUser?.uid // Usamos el UID autenticado para la captura
        )

        functions
            .getHttpsCallable("capturePayPalOrder")
            .call(data)
            .addOnSuccessListener {
                Toast.makeText(this, "¡Premium activado! Inicia sesión de nuevo.", Toast.LENGTH_LONG).show()
                cerrarSesionYRedirigir()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error en la captura final: ${exception.message}", Toast.LENGTH_LONG).show()
                btnIniciarPago.isEnabled = true
            }
    }

    private fun cerrarSesionYRedirigir() {
        val sharedPref = getSharedPreferences("UserSession", Context.MODE_PRIVATE)
        sharedPref.edit().clear().apply()
        auth.signOut() // ⭐ Cerramos la sesión de Firebase Auth también.

        val intent = Intent(this, FormActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        finish()
    }
}