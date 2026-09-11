package pe.saniape.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pe.saniape.app.actualizacion.ActualizadorFlexible
import pe.saniape.app.push.SaniaFcmService
import pe.saniape.app.ui.CitaPendienteDeAbrir

class MainActivity : ComponentActivity() {

    // LA ACTUALIZACIÓN QUE SE BAJA SOLA (Jonathan, 2026-09-11): Play la
    // descarga en segundo plano y recién con el APK listo se ofrece
    // "Instalar", sin salir de Sania. Ver ActualizadorFlexible.
    private var actualizacionDescargando by mutableStateOf(false)
    private var actualizacionLista by mutableStateOf(false)
    private lateinit var actualizador: ActualizadorFlexible

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Sania está escrita en español y sus clínicas son peruanas. Sin esto, los
        // diálogos NATIVOS de Android (calendario, selector de hora) salen en el
        // idioma del teléfono: en un celular en inglés el paciente veía
        // "Select date" dentro de una app en español.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags("es")
        }
        // La app pudo arrancar porque el paciente tocó un recordatorio de cita.
        CitaPendienteDeAbrir.pedir(intent?.getStringExtra(SaniaFcmService.EXTRA_CITA))
        enableEdgeToEdge()
        // En onCreate y no después: el launcher interno del flujo de Play se
        // registra al construir, y eso tiene que pasar antes de RESUMED.
        actualizador = ActualizadorFlexible(this) { descargando, lista ->
            actualizacionDescargando = descargando
            actualizacionLista = lista
        }
        actualizador.arrancar()
        setContent {
            App(
                actualizacionDescargando = actualizacionDescargando,
                actualizacionLista = actualizacionLista,
                alInstalarActualizacion = { actualizador.instalar() },
            )
        }
    }

    /** Con la app ya abierta, tocar la notificación llega por aquí, no por onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CitaPendienteDeAbrir.pedir(intent.getStringExtra(SaniaFcmService.EXTRA_CITA))
    }

    /**
     * Volver al frente refresca los datos de las pantallas.
     *
     * Sin esto, `LaunchedEffect(Unit)` solo corría al montar: se agendaba una
     * cita, llegaba el aviso al celular, se abría la app… y la agenda seguía
     * mostrando lo de antes hasta refrescar a mano.
     */
    override fun onResume() {
        super.onResume()
        pe.saniape.app.ui.Reanudacion.volvioAlFrente()
        // La descarga pudo terminar con la app en segundo plano: se pregunta
        // de nuevo para no dejar una actualización lista sin ofrecer.
        if (::actualizador.isInitialized) actualizador.alVolverAPrimerPlano()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::actualizador.isInitialized) actualizador.soltar()
    }
}