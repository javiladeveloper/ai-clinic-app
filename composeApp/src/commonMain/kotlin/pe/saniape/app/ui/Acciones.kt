package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Acciones nativas del sistema (abrir Maps, URLs externas). expect/actual:
 * en Android usan Intents reales (mejor que la web, que abre pestañas).
 */
interface AccionesNativas {
    /** Abre Google Maps / app de mapas en una ubicación. */
    fun abrirMapa(lat: Double, lng: Double, etiqueta: String?)
    /** Abre una URL (redes, sitio web, WhatsApp) en la app correspondiente. */
    fun abrirUrl(url: String)
}

@Composable
expect fun recordarAcciones(): AccionesNativas