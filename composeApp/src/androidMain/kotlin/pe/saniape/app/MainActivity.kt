package pe.saniape.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

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
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}