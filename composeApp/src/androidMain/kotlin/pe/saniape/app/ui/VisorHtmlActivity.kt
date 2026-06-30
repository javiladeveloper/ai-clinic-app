package pe.saniape.app.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout

/**
 * Visor de HTML con botón "Imprimir / Guardar PDF". Recibe el HTML completo por el Intent
 * (no una URL): así la historia clínica que sirve /api/staff/historia con Bearer se ve sin
 * pedir login en el navegador externo. La impresión usa el PrintManager nativo de Android
 * (el usuario puede elegir "Guardar como PDF").
 */
class VisorHtmlActivity : Activity() {

    companion object {
        const val EXTRA_HTML = "html"
        const val EXTRA_TITULO = "titulo"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val html = intent.getStringExtra(EXTRA_HTML) ?: "<html><body>Sin contenido</body></html>"
        val titulo = intent.getStringExtra(EXTRA_TITULO) ?: "Documento"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true   // el botón "imprimir" del HTML usa window.print()
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // baseUrl https para que las imágenes (logo) y rutas relativas carguen bien.
            loadDataWithBaseURL("https://www.saniape.com", html, "text/html", "UTF-8", null)
        }

        val botonImprimir = Button(this).apply {
            text = "🖨  Imprimir / Guardar PDF"
            setOnClickListener { imprimir(web, titulo) }
        }

        root.addView(botonImprimir, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun imprimir(web: WebView, titulo: String) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = web.createPrintDocumentAdapter("Historia-$titulo")
        printManager.print(
            "Historia $titulo", adapter,
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build(),
        )
    }
}
