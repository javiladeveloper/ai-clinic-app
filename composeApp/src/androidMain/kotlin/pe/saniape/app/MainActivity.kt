package pe.saniape.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.saniape.app.push.SaniaFcmService
import pe.saniape.app.ui.CitaPendienteDeAbrir

class MainActivity : ComponentActivity() {
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
        setContent {
            App()
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
    }
}