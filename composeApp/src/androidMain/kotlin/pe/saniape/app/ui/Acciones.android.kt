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

    override fun compartirTexto(texto: String, titulo: String) {
        try {
            val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
            context.startActivity(Intent.createChooser(envio, titulo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { copiarTexto(texto, titulo) }
    }

    override fun compartirArchivo(nombre: String, contenido: String, mime: String, titulo: String) {
        try {
            // Archivo temporal en cache/compartidos (expuesto por el FileProvider): se
            // pisa en cada exportación, no se acumulan copias.
            val dir = java.io.File(context.cacheDir, "compartidos").apply { mkdirs() }
            val f = java.io.File(dir, nombre.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            f.writeText(contenido, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            val envio = Intent(Intent.ACTION_SEND).setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, nombre)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                Intent.createChooser(envio, titulo)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) { compartirTexto(contenido, titulo) }
    }
}

@Composable
actual fun recordarAcciones(): AccionesNativas {
    val context = LocalContext.current
    return remember { AccionesAndroid(context) }
}