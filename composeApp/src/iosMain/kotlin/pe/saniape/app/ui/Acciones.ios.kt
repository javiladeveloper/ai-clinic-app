package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.create
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

/**
 * iOS: abre Maps/URLs con UIApplication.openURL y copia con UIPasteboard.
 * abrirHtml todavía no tiene visor nativo (ver TODO) — abre como data URL en Safari.
 */
private class AccionesIos : AccionesNativas {
    override fun abrirMapa(lat: Double, lng: Double, etiqueta: String?) {
        // Apple Maps por URL universal. Si hay etiqueta la usa como nombre del pin.
        val q = if (etiqueta != null) {
            "https://maps.apple.com/?q=${etiqueta.urlEncoded()}&ll=$lat,$lng"
        } else {
            "https://maps.apple.com/?ll=$lat,$lng&q=$lat,$lng"
        }
        abrirUrl(q)
    }

    override fun abrirUrl(url: String) {
        val u = if (url.startsWith("http")) url else "https://$url"
        val nsUrl = NSURL.URLWithString(u) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun abrirHtml(html: String, titulo: String) {
        // TODO(iOS Fase 2): visor nativo con WKWebView + impresión (equivalente a VisorHtmlActivity).
        // Provisional: abre el HTML como data URL en Safari.
        val nsUrl = NSURL.URLWithString("data:text/html,${html.urlEncoded()}") ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    override fun copiarTexto(texto: String, etiqueta: String) {
        UIPasteboard.generalPasteboard.string = texto
    }
}

/** Percent-encoding seguro para meter texto en una query de URL. */
private fun String.urlEncoded(): String =
    (this as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
        NSCharacterSet.URLQueryAllowedCharacterSet
    ) ?: this

@Composable
actual fun recordarAcciones(): AccionesNativas = remember { AccionesIos() }
