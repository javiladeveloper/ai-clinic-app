package pe.saniape.app.ui.clinica.profesionales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DIAS_HORARIO
import pe.saniape.app.data.staff.Franja
import pe.saniape.app.data.staff.HorarioProfesional
import pe.saniape.app.data.staff.HorarioProfesionalRepo
import pe.saniape.app.data.staff.ProfesionalItem
import pe.saniape.app.data.staff.EspecialidadProfesionalRepo
import pe.saniape.app.data.staff.ReglasEspecialidadProfesional
import pe.saniape.app.data.staff.SedesAgendaRepo
import pe.saniape.app.data.staff.finDespuesDeInicio
import pe.saniape.app.tutoriales.PantallaTutorial
import pe.saniape.app.tutoriales.pluralPersonal
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoHora
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.tutoriales.BotonAyuda

/**
 * Más → (nombre del personal): la lista de profesionales activos (contrato §3,
 * pantalla `Profesionales`). Solo con permiso `equipo`; tocar uno abre su horario.
 */
@Composable
fun PantallaProfesionales(ctx: ContextoStaff, onSalir: () -> Unit, onAbrir: (ProfesionalItem) -> Unit) = PantallaTutorial("Profesionales") {
    val c = Sania.colors
    var lista by remember { mutableStateOf<List<ProfesionalItem>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    LaunchedEffect(ctx.clinicaId, recarga) {
        try { lista = HorarioProfesionalRepo.listar(); fallo = false } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { if (lista == null) fallo = true }
    }
    val titulo = pluralPersonal(ctx.terminologiaProfesional)
    var corrigiendo by remember { mutableStateOf<ProfesionalItem?>(null) }
    corrigiendo?.let { p ->
        DialogoEspecialidadesProfesional(p, onCerrar = { corrigiendo = null }, onGuardado = { corrigiendo = null; recarga++ })
    }
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera("← Más", titulo, onSalir) { BotonAyuda("Profesionales") }
            val actual = lista
            when {
                actual == null && fallo -> Reintentar("No se pudo cargar la lista. Revisa tu conexión.") { fallo = false; recarga++ }
                actual == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                actual.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("Todavía no hay ${titulo.lowercase()} activos. Se agregan desde Equipo y accesos (web).", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg), verticalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                    Text("Toca uno para ver y editar su horario semanal.", color = c.textoSuave, fontSize = 12.sp)
                    actual.forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .then(if (i == 0) Modifier.tourAncla("profesionales.tarjeta") else Modifier)
                                .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                                .clickable { onAbrir(p) }.padding(Sania.dim.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(c.chipBg), contentAlignment = Alignment.Center) {
                                Text(p.nombre.take(1).uppercase(), color = c.navy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                                val sub = listOfNotNull(p.especialidad, p.turno).joinToString(" · ")
                                if (sub.isNotBlank()) Text(sub, color = c.textoSuave, fontSize = 12.sp)
                                if (p.sinEspecialidad) {
                                    Text(
                                        "⚠ ${ReglasEspecialidadProfesional.AVISO} · Corregir",
                                        color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp).clickable { corrigiendo = p },
                                    )
                                }
                            }
                            Text("🕒 Horario →", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horario semanal de un profesional (pantalla `Profesional`). Con permiso
 * `equipo`: 7 días, bloques, agregar/quitar, "Aplicar a todos", sede
 * (multisede). El propio profesional lo ve en solo lectura. Todas las reglas
 * las aplica el servidor (/api/staff/profesional/horario); la app muestra su error.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaHorarioProfesional(
    terapeutaId: String,
    nombre: String,
    volver: String,
    onSalir: () -> Unit,
) = PantallaTutorial("Profesional") {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var horario by remember { mutableStateOf<HorarioProfesional?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var noDisponible by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    var guardando by remember { mutableStateOf(false) }
    var agregando by remember { mutableStateOf(false) }
    var confirmarVaciar by remember { mutableStateOf(false) }
    // Bloque a quitar con vaciar:true (el servidor dijo que era el último); null = PUT vaciar.
    var vaciarConBloque by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(terapeutaId, recarga) {
        when (val r = HorarioProfesionalRepo.cargar(terapeutaId)) {
            is HorarioProfesionalRepo.R.Ok -> { horario = r.horario; error = null }
            HorarioProfesionalRepo.R.NoDisponible -> noDisponible = true
            is HorarioProfesionalRepo.R.Error -> if (horario == null) error = r.mensaje else Toaster.error(r.mensaje)
        }
    }

    /** Aplica la respuesta de una escritura: estado fresco, tarea del tutorial, cachés de la agenda. */
    fun aplicar(r: HorarioProfesionalRepo.R, exito: String): Boolean = when (r) {
        is HorarioProfesionalRepo.R.Ok -> {
            horario = r.horario
            r.horario.tarea?.let { pe.saniape.app.tutoriales.MotorTutoriales.tarea(it) }
            SedesAgendaRepo.limpiarCache()
            Toaster.exito(exito)
            true
        }
        HorarioProfesionalRepo.R.NoDisponible -> { Toaster.error("Esta función todavía no está disponible."); false }
        is HorarioProfesionalRepo.R.Error -> { Toaster.error(r.mensaje); false }
    }

    val h = horario
    if (confirmarVaciar && h != null) {
        AlertDialog(
            onDismissRequest = { confirmarVaciar = false; vaciarConBloque = null },
            title = { Text("¿Dejar a ${h.terapeuta.nombre.ifBlank { nombre }} sin horario?") },
            text = { Text("No aparecerá en la agenda ni en las horas libres para reservar hasta que le agregues un bloque.") },
            confirmButton = {
                TextButton({
                    confirmarVaciar = false; guardando = true
                    val bloque = vaciarConBloque
                    vaciarConBloque = null
                    scope.launch {
                        val r = if (bloque != null) HorarioProfesionalRepo.quitar(terapeutaId, bloque, vaciar = true)
                        else HorarioProfesionalRepo.vaciar(terapeutaId)
                        aplicar(r, "Horario vaciado")
                        guardando = false
                    }
                }) { Text("Dejar sin horario", color = c.error) }
            },
            dismissButton = { TextButton({ confirmarVaciar = false; vaciarConBloque = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Cabecera(volver, h?.terapeuta?.nombre?.ifBlank { null } ?: nombre, onSalir) { BotonAyuda("Profesional") }
            when {
                noDisponible -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("El horario desde la app estará disponible muy pronto. Mientras tanto, edítalo en la web.", color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                h == null && error != null -> Reintentar(error!!) { error = null; recarga++ }
                h == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(Sania.dim.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🕒 Horario semanal", color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        h.terapeuta.turno?.let {
                            Box(Modifier.clip(RoundedCornerShape(50)).background(c.chipBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(it, color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (!h.puedeEditar) {
                        Aviso("👀 Tu horario lo edita el administrador de la clínica.", c.info, c.infoBg)
                    } else if (h.franjas.isEmpty()) {
                        Aviso("Sin horario la agenda no ofrece horas libres. Agrega al menos un bloque.", c.pend, c.pendBg)
                    }
                    DIAS_HORARIO.forEach { dia ->
                        val bloques = h.delDia(dia)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dia, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                            if (bloques.isEmpty()) Text("Sin horario", color = c.textoSuave, fontSize = 12.sp)
                            else FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                bloques.forEach { f -> ChipBloque(f, h, editable = h.puedeEditar && !guardando) {
                                    val id = f.id ?: return@ChipBloque
                                    // El último bloque deja al profesional sin horario: con confirmación (vaciar).
                                    if (h.franjas.size <= 1) confirmarVaciar = true
                                    else {
                                        guardando = true
                                        scope.launch {
                                            val r = HorarioProfesionalRepo.quitar(terapeutaId, id)
                                            // Era el último según el servidor (lo local estaba viejo): confirmar y reintentar.
                                            if (HorarioProfesionalRepo.pideVaciar(r)) { vaciarConBloque = id; confirmarVaciar = true }
                                            else aplicar(r, "Bloque quitado")
                                            guardando = false
                                        }
                                    }
                                } }
                            }
                        }
                    }
                    if (h.puedeEditar) {
                        Spacer(Modifier.height(4.dp))
                        if (!agregando) {
                            Box(
                                Modifier.fillMaxWidth().tourAncla("horario.agregar").clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                    .clickable { agregando = true }.padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("+ Agregar bloque", color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                        AnimatedVisibility(agregando, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            FormBloque(
                                h, guardando,
                                onCancelar = { agregando = false },
                                onAgregar = { dia, ini, fin, sede ->
                                    guardando = true
                                    scope.launch {
                                        if (aplicar(HorarioProfesionalRepo.agregar(terapeutaId, dia, ini, fin, sede), "Bloque agregado")) agregando = false
                                        guardando = false
                                    }
                                },
                                onAplicarTodos = { ini, fin, sede ->
                                    guardando = true
                                    scope.launch {
                                        if (aplicar(HorarioProfesionalRepo.aplicarATodos(terapeutaId, ini, fin, sede), "Bloque aplicado a todos los días")) agregando = false
                                        guardando = false
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(Sania.dim.xl))
                }
            }
        }
    }
}

@Composable
private fun ChipBloque(f: Franja, h: HorarioProfesional, editable: Boolean, onQuitar: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.chipBg).padding(start = 10.dp, end = if (editable) 2.dp else 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sede = if (h.multiSede) h.nombreSede(f.sedeId)?.let { " · $it" } ?: "" else ""
        Text("${f.horaInicio} – ${f.horaFin}$sede", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (editable) Text(
            "✕", color = c.textoSuave, fontSize = 12.sp,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onQuitar).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormBloque(
    h: HorarioProfesional,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onAgregar: (dia: String, inicio: String, fin: String, sedeId: String?) -> Unit,
    onAplicarTodos: (inicio: String, fin: String, sedeId: String?) -> Unit,
) {
    val c = Sania.colors
    var dia by remember { mutableStateOf("Lun") }
    var inicio by remember { mutableStateOf(h.rangoTurno?.inicio ?: "08:00") }
    var fin by remember { mutableStateOf(h.rangoTurno?.fin ?: "13:00") }
    var sede by remember { mutableStateOf(h.sedeDefectoId ?: h.sedes.firstOrNull { it.esPrincipal }?.id) }
    var eligiendo by remember { mutableStateOf<String?>(null) } // "ini" | "fin"
    val valido = finDespuesDeInicio(inicio, fin)

    eligiendo?.let { cual ->
        DialogoHora(
            horaInicial = if (cual == "ini") inicio else fin,
            onElegir = { if (cual == "ini") inicio = it else fin = it },
            onCerrar = { eligiendo = null },
        )
    }

    Column(
        Modifier.fillMaxWidth().tourAncla("horario.fila").clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.navy, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nuevo bloque", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DIAS_HORARIO.forEach { d -> Chip(d, d == dia) { dia = d } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Selector("Desde", inicio, Modifier.weight(1f)) { eligiendo = "ini" }
            Selector("Hasta", fin, Modifier.weight(1f)) { eligiendo = "fin" }
        }
        h.rangoTurno?.let { t ->
            Text(
                "Usar horario del turno (${t.inicio} – ${t.fin})", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { inicio = t.inicio; fin = t.fin }.padding(4.dp),
            )
        }
        if (h.multiSede && h.sedes.isNotEmpty()) {
            Text("Sede", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                h.sedes.forEach { s -> Chip(s.nombre, s.id == sede) { sede = s.id } }
            }
        }
        if (!valido) Text("La hora de fin debe ser después de la de inicio.", color = c.error, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Boton("✓ Agregar el $dia", lleno = true, habilitado = valido && !guardando, modifier = Modifier.weight(1f)) {
                onAgregar(dia, inicio, fin, if (h.multiSede) sede else null)
            }
            Boton("Aplicar a todos", lleno = false, habilitado = valido && !guardando, modifier = Modifier.weight(1f)) {
                onAplicarTodos(inicio, fin, if (h.multiSede) sede else null)
            }
        }
        Text(
            "Cancelar", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(6.dp)).clickable(onClick = onCancelar).padding(6.dp),
        )
    }
}

// ── Piezas compartidas ─────────────────────────────────────────────────────

@Composable
private fun Cabecera(volver: String, titulo: String, onSalir: () -> Unit, extra: @Composable () -> Unit) {
    val c = Sania.colors
    pe.saniape.app.ui.ManejarAtras(activo = true, onAtras = onSalir)
    Row(
        Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                volver, color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onSalir).padding(vertical = 2.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(titulo, color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
        }
        extra()
    }
}

@Composable
private fun Reintentar(mensaje: String, onReintentar: () -> Unit) {
    val c = Sania.colors
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mensaje, color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Sania.dim.md))
            Boton("Reintentar", lleno = true, onClick = onReintentar)
        }
    }
}

@Composable
private fun Aviso(texto: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(texto, color = fg, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp))
}

@Composable
private fun Chip(t: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(t, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal) }
}

@Composable
private fun Selector(etq: String, valor: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(etq, color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(valor, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Boton(texto: String, lleno: Boolean, habilitado: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (lleno) (if (habilitado) c.navy else c.borde) else c.superficie)
            .border(1.dp, if (lleno) (if (habilitado) c.navy else c.borde) else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (lleno) c.sobreNavy else if (habilitado) c.navy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/**
 * "Corregir" del aviso "Sin especialidad": marca las especialidades del
 * profesional (mínimo una, como en la web). Las reglas las valida el servidor.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DialogoEspecialidadesProfesional(p: ProfesionalItem, onCerrar: () -> Unit, onGuardado: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var datos by remember { mutableStateOf<pe.saniape.app.data.staff.EspecialidadesDeProfesional?>(null) }
    var elegidas by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        when (val r = EspecialidadProfesionalRepo.cargar(p.id)) {
            is EspecialidadProfesionalRepo.R.Ok -> {
                datos = r.datos
                // Una sola especialidad en la clínica: viene marcada.
                elegidas = r.datos.seleccionadas.toSet().ifEmpty { r.datos.disponibles.singleOrNull()?.let { setOf(it.id) } ?: emptySet() }
            }
            is EspecialidadProfesionalRepo.R.Error -> error = r.mensaje
        }
    }
    AlertDialog(
        onDismissRequest = { if (!guardando) onCerrar() },
        title = { Text("Especialidades de ${p.nombre}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(ReglasEspecialidadProfesional.AYUDA, color = c.textoSuave, fontSize = 12.sp)
                val d = datos
                when {
                    d == null && error == null -> CircularProgressIndicator(color = c.navy)
                    d != null && d.disponibles.isEmpty() -> Text("La clínica no tiene especialidades activas.", color = c.textoSuave, fontSize = 13.sp)
                    d != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.disponibles.forEach { e ->
                            Chip(e.nombre, e.id in elegidas) { elegidas = if (e.id in elegidas) elegidas - e.id else elegidas + e.id }
                        }
                    }
                }
                error?.let { Aviso(it, c.error, c.errorBg) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = datos != null && !guardando,
                onClick = {
                    val falta = ReglasEspecialidadProfesional.errorAlGuardar(datos?.disponibles?.size ?: 0, elegidas.size)
                    if (falta != null) { error = falta; return@TextButton }
                    guardando = true; error = null
                    scope.launch {
                        when (val r = EspecialidadProfesionalRepo.guardar(p.id, elegidas.toList())) {
                            is EspecialidadProfesionalRepo.R.Ok -> { Toaster.exito("Especialidades guardadas"); onGuardado() }
                            is EspecialidadProfesionalRepo.R.Error -> { error = r.mensaje; guardando = false }
                        }
                    }
                },
            ) { Text(if (guardando) "Guardando…" else "Guardar", color = c.navy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(enabled = !guardando, onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
    )
}
