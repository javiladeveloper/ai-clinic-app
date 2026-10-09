package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.AjustesRepo
import pe.saniape.app.data.staff.EstadoCalendarioExterno
import pe.saniape.app.data.staff.estadoCalendarioDe
import pe.saniape.app.data.staff.haceCuanto
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Reanudacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Ajustes → Agenda de Google Calendar. Gemelo (resumido) de la web
 * /configuracion/calendario: estado de la conexión, "Sincronizar ahora" y los
 * enlaces para conectar la cuenta (navegador, Google no deja autorizar dentro de
 * la app) y revisar la vista previa (web). Solo Admin; el servidor lo valida.
 */
@Composable
internal fun SeccionCalendarioExterno(onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var estado by remember { mutableStateOf<EstadoCalendarioExterno?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }

    // Al volver del navegador (Google o la web) se relee solo.
    LaunchedEffect(recarga, Reanudacion.contador) {
        val (o, e) = AjustesRepo.leerObjeto("/api/staff/calendario")
        if (o != null) { estado = estadoCalendarioDe(o); error = null } else if (estado == null) error = e ?: "No se pudo leer el estado"
    }

    fun conectar() {
        ocupado = true
        scope.launch {
            val r = guardarAjuste(porDefecto = "No se pudo iniciar la conexión") {
                AjustesRepo.enviar(HttpMethod.Post, "/api/staff/calendario/conectar", JsonObject(emptyMap()))
            }
            ocupado = false
            r.cuerpo?.s("url")?.takeIf { r.registrada }?.let {
                acciones.abrirUrl(it)
                Toaster.info("Autoriza en Google. Después elige el calendario y revisa la importación en la web.")
            }
        }
    }

    fun sincronizar(fuenteId: String?) {
        ocupado = true
        scope.launch {
            val cuerpo = buildJsonObject { fuenteId?.let { put("fuenteId", it) } }
            val r = guardarAjuste(porDefecto = "No se pudo sincronizar") {
                AjustesRepo.enviar(HttpMethod.Post, "/api/staff/calendario/sincronizar", cuerpo)
            }
            ocupado = false
            if (r.registrada) {
                val b = r.cuerpo
                Toaster.exito("Sincronizado: ${b?.i("creadas") ?: 0} nuevas, ${b?.i("actualizadas") ?: 0} movidas, ${b?.i("canceladas") ?: 0} canceladas")
                recarga++
            }
        }
    }

    fun recordatorios(fuenteId: String, valor: Boolean) {
        ocupado = true
        scope.launch {
            val cuerpo = buildJsonObject { put("id", fuenteId); put("recordatoriosPacientes", valor) }
            val r = guardarAjuste(porDefecto = "No se pudo guardar") {
                AjustesRepo.enviar(HttpMethod.Patch, "/api/staff/calendario/fuente", cuerpo)
            }
            ocupado = false
            if (r.registrada) {
                Toaster.exito(if (valor) "Los pacientes recibirán su recordatorio por WhatsApp" else "Recordatorios a pacientes apagados")
                recarga++
            }
        }
    }

    val abrirWeb = { acciones.abrirUrl("${Supabase.SITE_URL}/configuracion/calendario") }

    SubPantalla("Agenda de Google Calendar", onVolver) {
        val e = estado
        if (e == null && error != null) { ErrorCarga(error) { error = null; recarga++ }; return@SubPantalla }
        if (e == null) { CargandoLista(); return@SubPantalla }

        Ayuda("Trae a Sania las citas que tienes en Google Calendar (pasadas y futuras), con sus pacientes. Sania solo LEE tu calendario y los cambios llegan solos cada 10 minutos. Al profesional le llegan sus avisos de agenda (1 h antes y el resumen del día), pero no uno de \"nueva cita\" por cada importada. A los pacientes solo se les escribe si enciendes \"Enviar recordatorios a los pacientes\".")
        if (!e.disponible) Aviso("La conexión con Google aún no está activada en Sania. Puedes subir un archivo .ics desde la web.")

        e.cuentas.forEach { cta ->
            Aviso(
                if (cta.activa) "✓ Conectada: ${cta.cuenta}" else "⚠ ${cta.cuenta}: ${cta.error ?: "hay que volver a conectar"}",
                if (cta.activa) "ok" else "error",
            )
        }

        val ahora = Clock.System.now().toEpochMilliseconds()
        val ms = { iso: String? -> iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } }
        e.calendarios.forEach { cal ->
            Tarjeta((if (cal.esIcs) "📄 " else "📅 ") + cal.nombre) {
                Text(cal.profesional?.let { "Citas para $it" } ?: "Sin profesional asignado", color = c.textoSuave, fontSize = 12.5.sp)
                Text(
                    when {
                        cal.enCurso -> "Importando… ${cal.citas} citas hasta ahora (avanza sola cada 10 min)"
                        cal.importadoEn == null -> "Falta revisar e importar (en la web)"
                        cal.esIcs -> "Importado ${haceCuanto(ms(cal.ultimaSync), ahora)} · ${cal.citas} citas"
                        else -> "Sincronizado ${haceCuanto(ms(cal.ultimaSync), ahora)} · ${cal.citas} citas importadas"
                    },
                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                if (cal.sinCupo > 0) Text(
                    if (cal.esIcs) "${cal.sinCupo} sin cupo u horario: revisa el horario del profesional y vuelve a subir el archivo en la web."
                    else "${cal.sinCupo} sin cupo u horario: se reintentan solas cada 10 minutos (revisa el horario del profesional).",
                    color = c.textoSuave, fontSize = 12.sp,
                )
                if (cal.porRevisar > 0) Text("${cal.porRevisar} por revisar: no se sabe de qué paciente son. Resuélvelos en la web.", color = c.pend, fontSize = 12.sp)
                cal.error?.let { Text("⚠ $it", color = c.error, fontSize = 12.sp) }
                if (cal.importadoEn != null) FilaInterruptor(
                    "Enviar recordatorios a los pacientes",
                    "WhatsApp la mañana de la cita (si la clínica tiene WhatsApp). Apagado: estos pacientes no reciben nada de Sania.",
                    activo = cal.recordatoriosPacientes, habilitado = !ocupado,
                ) { recordatorios(cal.id, it) }
                if (!cal.esIcs && cal.importadoEn != null) {
                    Boton("↻ Sincronizar ahora", habilitado = !ocupado, primario = false) { sincronizar(cal.id) }
                }
            }
        }

        if (e.calendarios.any { !it.esIcs && it.importadoEn == null && !it.enCurso } || (e.cuentas.isNotEmpty() && e.calendarios.none { !it.esIcs })) {
            Aviso("Elige el calendario y revisa la importación en la web: ahí ves los pacientes detectados y decides qué importar.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Boton(if (e.cuentas.isEmpty()) "Conectar con Google" else "Conectar otra cuenta",
                habilitado = e.disponible && !ocupado, modifier = Modifier.weight(1f)) { conectar() }
            Boton("Abrir en la web", primario = false, modifier = Modifier.weight(1f)) { abrirWeb() }
        }
        Ayuda("Google puede decir “Google no verificó esta app”: toca Configuración avanzada → Ir a Sania.")
    }
}
