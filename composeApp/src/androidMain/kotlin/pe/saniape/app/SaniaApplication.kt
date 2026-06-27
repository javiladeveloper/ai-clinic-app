package pe.saniape.app

import android.app.Application
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.Supabase

class SaniaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Preferencias.init(this)
        // Toca el cliente para inicializarlo y que restaure la sesión guardada.
        Supabase.client
    }
}