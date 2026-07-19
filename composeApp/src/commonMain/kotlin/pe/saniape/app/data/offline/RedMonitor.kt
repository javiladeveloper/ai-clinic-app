package pe.saniape.app.data.offline

/**
 * Avisa cuando vuelve la conexión, para vaciar la cola sin esperar a que el
 * usuario reabra la app.
 */
expect object RedMonitor {
    fun iniciar(alRecuperar: () -> Unit)
}
