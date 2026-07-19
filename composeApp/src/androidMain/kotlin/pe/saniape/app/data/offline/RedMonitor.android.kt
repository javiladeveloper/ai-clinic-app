package pe.saniape.app.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest

/**
 * Android: escucha el ConnectivityManager. Necesita el Context, que se le da en
 * Application.onCreate (igual que el driver de la BD). El permiso
 * ACCESS_NETWORK_STATE ya está en el manifest.
 */
actual object RedMonitor {
    private var contexto: Context? = null
    private var registrado = false

    fun init(context: Context) {
        if (contexto == null) contexto = context.applicationContext
    }

    actual fun iniciar(alRecuperar: () -> Unit) {
        if (registrado) return
        val cm = contexto?.getSystemService(ConnectivityManager::class.java) ?: return
        registrado = true
        try {
            cm.registerNetworkCallback(
                NetworkRequest.Builder().build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { alRecuperar() }
                },
            )
        } catch (e: Exception) {
            registrado = false // si el sistema lo rechaza, se reintenta la próxima vez
        }
    }
}
