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
    /**
     * Abre una página web DENTRO de la app (navegador in-app: Custom Tabs en Android,
     * Safari/SFSafariViewController en iOS) en vez de sacar al usuario a un navegador
     * externo. Se usa para flujos que continúan en la web (p. ej. la consulta guiada):
     * el botón atrás vuelve al APK y se comparten las cookies del navegador del sistema,
     * así que si ya hay sesión en la web no se re-loguea. Cae a [abrirUrl] si no hay un
     * navegador compatible.
     */
    fun abrirWeb(url: String)
    /**
     * Muestra un documento HTML (p.ej. la historia clínica imprimible) en un visor con
     * impresión. Se usa cuando el contenido viene de un endpoint con Bearer (no se puede
     * abrir la URL directa en el navegador externo porque no lleva la sesión del staff).
     */
    fun abrirHtml(html: String, titulo: String)
    /** Copia texto al portapapeles del sistema (p.ej. el resumen clínico IA o un enlace). */
    fun copiarTexto(texto: String, etiqueta: String = "Texto")
}

@Composable
expect fun recordarAcciones(): AccionesNativas