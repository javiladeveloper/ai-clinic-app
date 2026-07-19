package pe.saniape.app.data.offline

/**
 * iOS: por ahora no observa la red; la cola se vacía al abrir la app y después
 * de cada encolado. Implementar con NWPathMonitor cuando se compile en la Mac.
 */
actual object RedMonitor {
    actual fun iniciar(alRecuperar: () -> Unit) { /* pendiente: NWPathMonitor */ }
}
