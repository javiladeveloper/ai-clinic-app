package pe.saniape.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Implementación Android: Intents nativos para mapas y URLs. */
private class AccionesAndroid(private val context: Context) : AccionesNativas {
    override fun abrirMapa(lat: Double, lng: Double, etiqueta: String?) {
        // geo: con etiqueta → abre la app de mapas instalada (Google Maps, etc.).
        val label = etiqueta?.let { Uri.encode(it) }
        val geo = if (label != null) "geo:$lat,$lng?q=$lat,$lng($label)" else "geo:$lat,$lng?q=$lat,$lng"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geo)).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Sin Google Maps: cae al navegador con maps web.
            abrirUrl("https://www.google.com/maps?q=$lat,$lng")
        }
    }

    override fun abrirUrl(url: String) {
        val u = if (url.startsWith("http")) url else "https://$url"
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { /* sin app que abra el enlace */ }
    }

    override fun abrirHtml(html: String, titulo: String) {
        try {
            context.startActivity(
                Intent(context, VisorHtmlActivity::class.java)
                    .putExtra(VisorHtmlActivity.EXTRA_HTML, html)
                    .putExtra(VisorHtmlActivity.EXTRA_TITULO, titulo)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) { /* no se pudo abrir el visor */ }
    }

    override fun copiarTexto(texto: String, etiqueta: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText(etiqueta, texto))
        } catch (_: Exception) { /* sin portapapeles */ }
    }
}

@Composable
actual fun recordarAcciones(): AccionesNativas {
    val context = LocalContext.current
    return remember { AccionesAndroid(context) }
}