package pe.saniape.app.ui.clinica.ajustes

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.CalendarioGoogle
import pe.saniape.app.data.staff.CalendarioRepo
import pe.saniape.app.data.staff.CalendarioTraido
import pe.saniape.app.data.staff.CuentaCalendario
import pe.saniape.app.data.staff.DecisionesCal
import pe.saniape.app.data.staff.EstadoCalendarioExterno
import pe.saniape.app.data.staff.calendariosGoogleDe
import pe.saniape.app.data.staff.estadoCalendarioDe
import pe.saniape.app.data.staff.haceCuanto
import pe.saniape.app.data.staff.hexColor
import pe.saniape.app.data.staff.textoEstadoFuente
import pe.saniape.app.data.staff.textoQuitarFuente
import pe.saniape.app.data.staff.vistaPreviaDe
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Reanudacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Ajustes → Agenda de Google Calendar. Gemelo NATIVO de la web
 * /configuracion/calendario: conectar la cuenta (en el navegador: Google no deja
 * autorizar dentro de la app), elegir qué calendario va a qué profesional, la
 * lista "Tus calendarios en Sania" y la vista previa antes de importar
 * ([VistaPreviaCalendario]). Solo Admin; el servidor lo valida.
 */

/** Calendarios de una cuenta: cargando, error o la lista. */
private sealed interface ListaCuenta {
    data object Cargando : ListaCuenta
    data class Error(val mensaje: String) : ListaCuenta
    data class Lista(val calendarios: List<CalendarioGoogle>) : ListaCuenta
}

/** La vista previa abierta (como `vista` de la página web; [n] la vuelve a montar al actualizar). */
internal data class VistaAbierta(
    val fuente: CalendarioTraido,
    val datos: pe.saniape.app.data.staff.VistaPreviaCal,
    val config: pe.saniape.app.data.staff.ConfigFuenteCal,
    val decisiones: DecisionesCal,
    val profesional: String?,
    val n: Int,
)

/** Qué selector de profesional está abierto. */
private sealed interface SelectorProf {
    data class Calendario(val calId: String) : SelectorProf
    data class Fuente(val f: CalendarioTraido) : SelectorProf
}

@Composable
internal fun SeccionCalendarioExterno(onVolver: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var estado by remember { mutableStateOf<EstadoCalendarioExterno?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var terapeutas by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var procedimientos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var listas by remember { mutableStateOf<Map<String, ListaCuenta>>(emptyMap()) }
    var profElegido by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selector by remember { mutableStateOf<SelectorProf?>(null) }
    var confirmarQuitar by remember { mutableStateOf<CalendarioTraido?>(null) }
    var confirmarDesconectar by remember { mutableStateOf<CuentaCalendario?>(null) }
    var vista by remember { mutableStateOf<VistaAbierta?>(null) }
    var cargandoVista by remember { mutableStateOf(false) }

    // Al volver del navegador (Google) se relee solo.
    LaunchedEffect(recarga, Reanudacion.contador) {
        val r = CalendarioRepo.estado()
        val o = r.cuerpo
        if (r.registrada && o != null) { estado = estadoCalendarioDe(o); error = null }
        else if (estado == null) error = r.rechazo?.error ?: "No se pudo leer el estado"
    }
    LaunchedEffect(Unit) {
        terapeutas = runCatching { AgendaRepo.terapeutasActivos().map { it.id to it.nombre } }.getOrDefault(emptyList())
        procedimientos = CalendarioRepo.procedimientosActivos()
    }

    /** Escritura con el indicador global; si falla, el mensaje del servidor. */
    fun hacer(porDefecto: String, gestion: Gestion = Gestion.GUARDANDO, op: suspend () -> ResultadoEscritura, alTerminar: (ResultadoEscritura) -> Unit) {
        ocupado = true
        scope.launch {
            val r = guardarAjuste(gestion = gestion, porDefecto = porDefecto) { op() }
            ocupado = false
            if (r.registrada) alTerminar(r)
        }
    }

    fun conectar() = hacer("No se pudo iniciar la conexión", op = { CalendarioRepo.conectar() }) { r ->
        r.cuerpo?.s("url")?.let {
            acciones.abrirUrl(it)
            Toaster.info("Autoriza en Google y vuelve a la app: aquí eliges qué calendario traer.")
        }
    }

    fun verCalendarios(conexionId: String) {
        listas = listas + (conexionId to ListaCuenta.Cargando)
        scope.launch {
            val r = CalendarioRepo.calendarios(conexionId)
            val o = r.cuerpo
            listas = listas + (conexionId to if (r.registrada && o != null) ListaCuenta.Lista(calendariosGoogleDe(o)) else ListaCuenta.Error(r.rechazo?.error ?: "Error"))
        }
    }

    // Recién conectada (vuelta del navegador) y sin calendarios elegidos: se
    // muestran solos para elegir, sin otro toque.
    LaunchedEffect(estado) {
        val e = estado ?: return@LaunchedEffect
        e.cuentas.filter { cta -> cta.activa && listas[cta.id] == null && e.calendarios.none { it.conexionId == cta.id } }
            .forEach { verCalendarios(it.id) }
    }

    fun elegir(conexionId: String, cal: CalendarioGoogle) =
        hacer("No se pudo guardar", op = { CalendarioRepo.elegir(conexionId, cal, profElegido[cal.id]) }) { recarga++ }

    fun cambiarProfesional(f: CalendarioTraido, terapeutaId: String?) =
        hacer("No se pudo guardar", op = { CalendarioRepo.cambiarProfesional(f.id, terapeutaId) }) { recarga++ }

    fun recordatorios(f: CalendarioTraido, valor: Boolean) =
        hacer("No se pudo guardar", op = { CalendarioRepo.recordatorios(f.id, valor) }) {
            Toaster.exito(if (valor) "Los pacientes recibirán sus recordatorios" else "Recordatorios a pacientes apagados (WhatsApp y notificaciones)")
            recarga++
        }

    fun sincronizar(f: CalendarioTraido) =
        hacer("No se pudo sincronizar", Gestion.ACTUALIZANDO, op = { CalendarioRepo.sincronizar(f.id) }) { r ->
            val b = r.cuerpo
            Toaster.exito("Sincronizado: ${b?.i("creadas") ?: 0} nuevas, ${b?.i("actualizadas") ?: 0} movidas, ${b?.i("canceladas") ?: 0} canceladas")
            recarga++
        }

    fun quitar(f: CalendarioTraido) =
        hacer("No se pudo quitar", Gestion.ELIMINANDO, op = { CalendarioRepo.quitar(f.id) }) {
            Toaster.exito(if (f.esIcs) "Archivo quitado. Las citas importadas se quedan." else "Calendario quitado. Las citas importadas se quedan.")
            recarga++
        }

    fun desconectar(cta: CuentaCalendario) =
        hacer("No se pudo desconectar", Gestion.ELIMINANDO, op = { CalendarioRepo.desconectar(cta.id) }) {
            Toaster.exito("Desconectada")
            listas = listas - cta.id
            recarga++
        }

    /** Pide la vista previa (primera vez o "↻ Actualizar vista previa"). */
    suspend fun pedirVista(f: CalendarioTraido, config: pe.saniape.app.data.staff.ConfigFuenteCal, decisiones: DecisionesCal): Boolean {
        val r = pe.saniape.app.ui.conIndicador(Gestion.CARGANDO) { CalendarioRepo.vistaPrevia(f.id, config, decisiones) }
        val o = r.cuerpo
        if (!r.registrada || o == null) {
            Toaster.error(r.rechazo?.error ?: "No se pudo leer el calendario")
            return false
        }
        val profesional = estado?.calendarios?.find { it.id == f.id }?.profesional ?: f.profesional
        vista = VistaAbierta(f, vistaPreviaDe(o), config, decisiones, profesional, (vista?.n ?: 0) + 1)
        return true
    }

    fun abrirVista(f: CalendarioTraido) {
        cargandoVista = true
        scope.launch { pedirVista(f, f.config, DecisionesCal()); cargandoVista = false }
    }

    // ── Vista previa a pantalla completa ──
    vista?.let { v ->
        androidx.compose.runtime.key(v.n) {
            VistaPreviaCalendario(
                abierta = v,
                procedimientos = procedimientos,
                onActualizar = { config, decisiones -> pedirVista(v.fuente, config, decisiones) },
                onImportado = { recarga++ },
                onCerrar = { vista = null },
            )
        }
        return
    }

    selector?.let { sel ->
        val opciones = when (sel) {
            is SelectorProf.Calendario -> terapeutas
            is SelectorProf.Fuente -> listOf("" to "Sin profesional") + terapeutas
        }
        DialogoListaLarga(
            titulo = if (sel is SelectorProf.Calendario) "¿De qué profesional?" else "Profesional",
            opciones = opciones,
            onElegir = { id ->
                selector = null
                when (sel) {
                    is SelectorProf.Calendario -> profElegido = profElegido + (sel.calId to id)
                    is SelectorProf.Fuente -> if (id != (sel.f.terapeutaId ?: "")) cambiarProfesional(sel.f, id.ifEmpty { null })
                }
            },
            onCerrar = { selector = null },
        )
    }

    confirmarQuitar?.let { f ->
        val t = textoQuitarFuente(f.nombre, f.esIcs)
        AlertaConTeclado(
            onDismissRequest = { confirmarQuitar = null },
            title = { Text(t.boton, fontWeight = FontWeight.Bold) },
            text = { Text(t.confirmar) },
            confirmButton = { TextButton(onClick = { confirmarQuitar = null; quitar(f) }) { Text(t.boton, color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmarQuitar = null }) { Text("Cancelar", color = c.navy) } },
            containerColor = c.superficie,
        )
    }

    confirmarDesconectar?.let { cta ->
        AlertaConTeclado(
            onDismissRequest = { confirmarDesconectar = null },
            title = { Text("Desconectar", fontWeight = FontWeight.Bold) },
            text = { Text("¿Desconectar ${cta.cuenta.ifBlank { "esta cuenta" }}? Las citas ya importadas se quedan; solo deja de sincronizar.") },
            confirmButton = { TextButton(onClick = { confirmarDesconectar = null; desconectar(cta) }) { Text("Desconectar", color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmarDesconectar = null }) { Text("Cancelar", color = c.navy) } },
            containerColor = c.superficie,
        )
    }

    SubPantalla("Agenda de Google Calendar", onVolver) {
        val e = estado
        if (e == null && error != null) { ErrorCarga(error) { error = null; recarga++ }; return@SubPantalla }
        if (e == null) { CargandoLista(); return@SubPantalla }

        val ahora = Clock.System.now().toEpochMilliseconds()
        val ms = { iso: String? -> iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } }
        val nombreTer = { id: String? -> terapeutas.find { it.first == id }?.second }

        // 1. Conectar la cuenta
        Tarjeta("1. Conecta tu cuenta de Google") {
            Ayuda("Sania solo lee tu calendario: no crea, no cambia ni borra nada en Google. Cada evento será una cita (el título se usa como nombre del paciente). Puedes desconectarlo cuando quieras.")
            if (!e.disponible) Aviso("La conexión con Google aún no está activada en Sania. Mientras tanto puedes subir el archivo .ics (desde la web).")
            e.cuentas.forEach { cta ->
                CajaBorde {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cta.cuenta, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (cta.activa) "● conectada" else "● ${cta.error ?: "hay que volver a conectar"}",
                            color = if (cta.activa) c.ok else c.error, fontSize = 12.sp,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!cta.activa) Boton("Volver a conectar", habilitado = !ocupado, modifier = Modifier.weight(1f)) { conectar() }
                        else Boton("Elegir calendarios", primario = false, habilitado = listas[cta.id] != ListaCuenta.Cargando, modifier = Modifier.weight(1f)) { verCalendarios(cta.id) }
                        Boton("Desconectar", primario = false, habilitado = !ocupado, modifier = Modifier.weight(1f)) { confirmarDesconectar = cta }
                    }
                    when (val l = listas[cta.id]) {
                        null -> {}
                        ListaCuenta.Cargando -> Ayuda("Buscando tus calendarios…")
                        is ListaCuenta.Error -> Text(l.mensaje, color = c.error, fontSize = 12.5.sp)
                        is ListaCuenta.Lista -> l.calendarios.forEach { cal ->
                            val ya = e.calendarios.any { it.conexionId == cta.id && it.calendarioId == cal.id }
                            FilaCalendarioGoogle(
                                cal = cal, ya = ya, profesional = nombreTer(profElegido[cal.id]), ocupado = ocupado,
                                onProfesional = { selector = SelectorProf.Calendario(cal.id) },
                                onTraer = { elegir(cta.id, cal) },
                            )
                        }
                    }
                }
            }
            Boton(if (e.cuentas.isEmpty()) "Conectar con Google" else "+ Conectar otra cuenta", habilitado = e.disponible && !ocupado) { conectar() }
            Ayuda("Google puede mostrar “Google no verificó esta app”: toca Configuración avanzada → Ir a Sania. Es normal mientras Google revisa la app.")
        }

        // 2. Tus calendarios en Sania
        if (e.calendarios.isNotEmpty()) Tarjeta("2. Tus calendarios en Sania") {
            e.calendarios.forEach { f ->
                val quitarTxt = textoQuitarFuente(f.nombre, f.esIcs)
                CajaBorde {
                    Text((if (f.esIcs) "📄 " else "📅 ") + f.nombre, color = c.texto, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    Text(textoEstadoFuente(f, haceCuanto(ms(f.ultimaSync), ahora)), color = c.textoSuave, fontSize = 12.5.sp, lineHeight = 17.sp)
                    f.error?.let { Text("⚠ $it", color = c.error, fontSize = 12.sp) }
                    if (!f.esIcs) Selector("Profesional", nombreTer(f.terapeutaId) ?: f.profesional ?: "", placeholder = "Sin profesional", habilitado = !ocupado) {
                        selector = SelectorProf.Fuente(f)
                    }
                    if (f.importadoEn != null) FilaInterruptor(
                        "Enviar recordatorios a los pacientes",
                        "WhatsApp y notificaciones de la app del paciente",
                        activo = f.recordatoriosPacientes, habilitado = !ocupado,
                    ) { recordatorios(f, it) }
                    if (!f.esIcs) {
                        Boton(
                            if (cargandoVista) "Leyendo calendario…" else if (f.importadoEn != null) "Revisar de nuevo" else "Revisar e importar",
                            primario = f.importadoEn == null, habilitado = !cargandoVista && !ocupado,
                        ) { abrirVista(f) }
                        if (f.importadoEn != null) Boton("↻ Sincronizar ahora", primario = false, habilitado = !ocupado) { sincronizar(f) }
                    }
                    Boton(quitarTxt.boton, primario = false, habilitado = !ocupado) { confirmarQuitar = f }
                    Ayuda(quitarTxt.ayuda)
                }
            }
        }

        Tarjeta("¿Prefieres no conectar la cuenta? Sube el archivo .ics") {
            Ayuda("En Google Calendar (computadora): ⚙ Configuración → Importar y exportar → Exportar. Descarga un .zip; ábrelo y sube el archivo .ics de tu calendario. Es una importación única: los cambios futuros no llegan solos. Si lo vuelves a subir, no se duplica nada.")
            Boton("Subir el archivo en la web", primario = false) { acciones.abrirUrl("${Supabase.SITE_URL}/configuracion/calendario") }
        }
    }
}

/** Caja con borde dentro de una tarjeta (cuenta o calendario). */
@Composable
private fun CajaBorde(contenido: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido,
    )
}

/** Un calendario de la cuenta: color, nombre y "¿De qué profesional?" + "Traer este". */
@Composable
private fun FilaCalendarioGoogle(
    cal: CalendarioGoogle, ya: Boolean, profesional: String?, ocupado: Boolean,
    onProfesional: () -> Unit, onTraer: () -> Unit,
) {
    val c = Sania.colors
    val color = colorDeHex(cal.color?.let { hexColor(it) }) ?: c.textoSuave
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(12.dp).drawBehind { drawCircle(color) })
            Spacer(Modifier.width(8.dp))
            Text(cal.nombre + if (cal.principal) " (principal)" else "", color = c.texto, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (ya && cal.rol != "freeBusyReader") Text("✓ elegido", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        when {
            cal.rol == "freeBusyReader" -> Ayuda("Solo ves libre/ocupado: no se pueden leer los nombres.")
            ya -> {}
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profesional ?: "¿De qué profesional?",
                    color = if (profesional == null) c.textoSuave else c.texto, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable(enabled = !ocupado, onClick = onProfesional).padding(horizontal = 10.dp, vertical = 10.dp),
                )
                Boton("Traer este", primario = false, habilitado = !ocupado, modifier = Modifier) { onTraer() }
            }
        }
    }
}
