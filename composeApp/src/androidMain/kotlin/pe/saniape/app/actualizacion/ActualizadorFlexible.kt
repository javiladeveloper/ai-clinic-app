package pe.saniape.app.actualizacion

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * LA ACTUALIZACIÓN QUE SE BAJA SOLA (Jonathan, 2026-09-11).
 *
 * Hasta ahora el aviso de versión nueva mandaba a la Play Store: el fisio
 * SALÍA de Sania, miraba una barra de progreso en la tienda y tenía que
 * volver. Con el modo flexible de In-App Updates, Play pregunta si hay versión
 * nueva al abrir la app, la descarga en segundo plano mientras se sigue
 * atendiendo, y recién con el APK listo aparece "Instalar" — un reinicio de
 * segundos, sin salir nunca de la aplicación.
 *
 * Mismo patrón que ya funciona en LeadAI (2026-08-31).
 *
 * Funciona con cualquier build instalada DESDE PLAY, incluida la prueba
 * interna. En un APK instalado a mano no hay actualización que ofrecer y este
 * objeto no hace nada — por eso el chequeo contra el backend (`VersionRepo`)
 * sigue vivo como respaldo, aunque se calla mientras Play esté descargando.
 *
 * [alCambiar] recibe `(descargando, lista)`:
 * - `descargando = true` mientras el flujo de Play está activo — la UI usa
 *   esto para callar el aviso viejo y no ofrecer dos caminos a la vez.
 * - `lista = true` cuando el APK terminó de bajar — la UI ofrece "Instalar".
 */
class ActualizadorFlexible(
    activity: ComponentActivity,
    private val alCambiar: (descargando: Boolean, lista: Boolean) -> Unit,
) {

    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    // El launcher del diálogo de Play ("Sania quiere actualizarse"). Se
    // registra en la construcción — tiene que ocurrir antes de RESUMED, por
    // eso este objeto se crea en onCreate. El resultado no importa: si dicen
    // "ahora no", no se insiste hasta el próximo arranque.
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { /* rechazar la descarga es una respuesta válida */ }

    private val listener = InstallStateUpdatedListener { estado ->
        when (estado.installStatus()) {
            InstallStatus.DOWNLOADED -> alCambiar(false, true)
            InstallStatus.DOWNLOADING, InstallStatus.PENDING -> alCambiar(true, false)
            // FAILED/CANCELED: se vuelve al estado normal y el aviso viejo
            // recupera la voz — peor sería quedarse mudo en una versión vieja.
            else -> alCambiar(false, false)
        }
    }

    /** Llamar en `onCreate`: registra el listener y arranca el chequeo. */
    fun arrancar() {
        manager.registerListener(listener)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                alCambiar(true, false)
                manager.startUpdateFlowForResult(
                    info,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
        }
        // Sin addOnFailureListener a propósito: si Play no responde (APK
        // instalado a mano, sin Play Services), no hay nada que hacer acá.
    }

    /**
     * Llamar en `onResume`: si la descarga terminó con la app en segundo
     * plano, el listener puede no haberse enterado — se pregunta de nuevo para
     * no dejar una actualización lista sin ofrecer.
     */
    fun alVolverAPrimerPlano() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) alCambiar(false, true)
        }
    }

    /** El toque en "Instalar": Play aplica el APK y reinicia la app en segundos. */
    fun instalar() {
        manager.completeUpdate()
    }

    /** Llamar en `onDestroy`. */
    fun soltar() {
        manager.unregisterListener(listener)
    }
}
