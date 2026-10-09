package pe.saniape.app.ui.clinica.servicios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadServicio
import pe.saniape.app.data.staff.ServicioApp
import pe.saniape.app.data.staff.ServiciosRepo
import pe.saniape.app.data.staff.agruparPorEspecialidad
import pe.saniape.app.data.staff.etiquetaCaras
import pe.saniape.app.data.staff.filtrarServicios
import pe.saniape.app.data.staff.resumenTipicos
import pe.saniape.app.data.staff.dineroActivo
import pe.saniape.app.data.staff.sufijoPrecio
import pe.saniape.app.data.staff.tramosCaras
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Gestion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.EstadoVacio
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.conIndicador
import pe.saniape.app.ui.theme.Sania

/** "#2c3e7a" → Color; cualquier otra cosa → null. */
internal fun colorDeHex(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

/**
 * 💊 Servicios (Más → Administración). Gemelo de app/(app)/procedimientos en la
 * web: catálogo con búsqueda y filtros (estado, especialidad), agrupado por
 * especialidad; crear/editar con todo lo de la web (modo de cobro, paquetes,
 * precio por caras, serie automática, pasos encadenados, controles, evaluación
 * psicológica); activar/desactivar; procedimientos típicos (flujo médico).
 * Las plantillas de tratamiento siguen en la web (segunda fase).
 * Solo con permiso "servicios" (lo decide el padre; el servidor lo valida igual).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaServicios(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf<List<ServicioApp>?>(null) }
    var especialidades by remember { mutableStateOf<List<EspecialidadServicio>>(emptyList()) }
    var fallo by remember { mutableStateOf(false) }
    var recarga by remember { mutableIntStateOf(0) }
    var busqueda by remember { mutableStateOf("") }
    var filtroEstado by remember { mutableStateOf("") }          // "" = activos · "Inactivo" · "todos"
    var filtroEsp by remember { mutableStateOf<String?>(null) }
    // Formulario abierto (con el servicio a editar, o null = nuevo).
    var formAbierto by remember { mutableStateOf(false) }
    var formServicio by remember { mutableStateOf<ServicioApp?>(null) }
    var confirmarDesactivar by remember { mutableStateOf<ServicioApp?>(null) }
    var cargandoTipicos by remember { mutableStateOf(false) }

    LaunchedEffect(ctx.clinicaId, recarga) {
        try {
            val esps = ServiciosRepo.especialidades()
            lista = ServiciosRepo.listar()
            especialidades = esps
            fallo = false
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (lista == null) fallo = true
        }
    }

    fun cambiarEstado(s: ServicioApp) {
        val nuevo = if (s.activo) "Inactivo" else "Activo"
        scope.launch {
            val r = conIndicador(Gestion.ACTUALIZANDO) { ServiciosRepo.cambiarEstado(s.id, nuevo) }
            if (r.registrada) {
                Toaster.exito(if (nuevo == "Activo") "Servicio activado" else "Servicio desactivado")
                // Al instante en la lista; la recarga confirma con la base.
                lista = lista?.map { if (it.id == s.id) it.copy(estado = nuevo) else it }
                recarga++
            } else Toaster.error(r.rechazo?.error ?: "No se pudo cambiar el estado")
        }
    }

    /**
     * Desactivar un servicio normal es inofensivo. Pero el que define el precio
     * de una cita (Consulta/Evaluación) sostiene el flujo entero: se avisa antes
     * (igual que la web).
     */
    fun pedirCambioEstado(s: ServicioApp) {
        if (s.activo && !s.tipoCita.isNullOrBlank()) confirmarDesactivar = s else cambiarEstado(s)
    }

    fun cargarTipicos() {
        if (cargandoTipicos) return
        cargandoTipicos = true
        scope.launch {
            val r = try { conIndicador { ServiciosRepo.cargarTipicos() } } finally { cargandoTipicos = false }
            if (r.registrada) { Toaster.exito(resumenTipicos(r.cuerpo)); recarga++ }
            else Toaster.error(r.rechazo?.error ?: "No se pudieron cargar los procedimientos típicos")
        }
    }

    confirmarDesactivar?.let { s ->
        AlertaConTeclado(
            onDismissRequest = { confirmarDesactivar = null },
            title = { Text("¿Desactivar \"${s.nombre}\"?") },
            text = {
                Text(
                    "Es el servicio que define el precio de las citas de tipo ${s.tipoCita}. " +
                        "Al desactivarlo, esas citas se agendarán sin precio de referencia.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarDesactivar = null; cambiarEstado(s) }) {
                    Text("Desactivar", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarDesactivar = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }

    if (formAbierto) {
        FormularioServicio(
            ctx = ctx,
            inicial = formServicio,
            especialidades = especialidades,
            servicios = lista.orEmpty(),
            onCerrar = { formAbierto = false },
            onGuardado = { formAbierto = false; recarga++ },
        )
    }

    val activas = especialidades.filter { it.activa }
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Cabecera
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Servicios", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    lista?.let { l ->
                        Text(
                            "${l.count { it.activo }} servicios activos · ${activas.size} especialidades",
                            color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini,
                        )
                    }
                }
                pe.saniape.app.ui.tutoriales.BotonAyuda("Servicios")
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable(enabled = lista != null) { formServicio = null; formAbierto = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ Nuevo", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            val actual = lista
            when {
                actual == null && fallo -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No se pudieron cargar los servicios. Revisa tu conexión.",
                            color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { fallo = false; recarga++ }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                actual == null -> CargandoLista(filas = 5, conAvatar = false)
                else -> {
                    val filtrados = filtrarServicios(actual, busqueda, filtroEstado, filtroEsp)
                    val grupos = agruparPorEspecialidad(filtrados, especialidades)
                    val agrupar = filtroEsp == null && grupos.size > 1
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(top = Sania.dim.md)) {
                                OutlinedTextField(
                                    value = busqueda, onValueChange = { busqueda = it },
                                    placeholder = { Text("🔍 Buscar servicio…") }, singleLine = true,
                                    colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(Sania.dim.sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("" to "Activos", "Inactivo" to "Inactivos", "todos" to "Todos").forEach { (v, t) ->
                                        ChipFiltro(t, filtroEstado == v) { filtroEstado = v }
                                    }
                                }
                                if (activas.size > 1) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ChipFiltro("Todas las especialidades", filtroEsp == null) { filtroEsp = null }
                                        activas.forEach { e ->
                                            ChipFiltro("${e.icono.orEmpty()} ${e.nombre}".trim(), filtroEsp == e.id) { filtroEsp = e.id }
                                        }
                                    }
                                }
                                // Flujo médico: drenaje, sutura, extracción… con su consentimiento.
                                if (ctx.modulosClinicos.mapaMedico.activo) {
                                    Spacer(Modifier.height(Sania.dim.sm))
                                    BotonContorno(
                                        if (cargandoTipicos) "Cargando…" else "🩹 Cargar procedimientos típicos",
                                        habilitado = !cargandoTipicos,
                                    ) { cargarTipicos() }
                                }
                            }
                        }
                        if (filtrados.isEmpty()) {
                            item {
                                EstadoVacio(
                                    emoji = "💊",
                                    titulo = if (actual.isEmpty()) "Aún no hay servicios" else "No se encontraron servicios",
                                    subtitulo = if (actual.isEmpty()) "Crea el primero con “+ Nuevo”." else "Prueba con otra búsqueda o filtro.",
                                )
                            }
                        } else if (agrupar) {
                            grupos.forEach { g ->
                                // Llave = la clave del grupo (especialidad_id), nunca la especialidad encontrada.
                                item(key = "h-${g.clave}") { CabeceraGrupo(g.especialidad, g.servicios.size) }
                                items(g.servicios, key = { it.id }) { s ->
                                    TarjetaServicio(s, onEditar = { formServicio = s; formAbierto = true }, onCambiarEstado = { pedirCambioEstado(s) })
                                }
                            }
                        } else {
                            items(filtrados, key = { it.id }) { s ->
                                TarjetaServicio(s, onEditar = { formServicio = s; formAbierto = true }, onCambiarEstado = { pedirCambioEstado(s) })
                            }
                        }
                        item {
                            val acciones = pe.saniape.app.ui.recordarAcciones()
                            Text(
                                "📋 Las plantillas de tratamiento se editan en la web (Servicios → Plantillas) ↗",
                                color = c.navy, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = Sania.dim.lg)
                                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { acciones.abrirUrl("${pe.saniape.app.data.Supabase.SITE_URL}/procedimientos") }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabeceraGrupo(esp: EspecialidadServicio?, cuantos: Int) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (esp != null) {
            Text(esp.icono.orEmpty(), fontSize = 18.sp)
            Spacer(Modifier.width(6.dp))
            Text(esp.nombre, color = colorDeHex(esp.color) ?: c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("($cuantos servicio${if (cuantos != 1) "s" else ""})", color = c.textoSuave, fontSize = Sania.txt.mini)
        } else {
            Text("Sin especialidad asignada", color = c.textoSuave, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
        }
    }
}

/** Tarjeta de un servicio. Gemelo de components/procedimientos/ProcedimientoCard.tsx. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaServicio(s: ServicioApp, onEditar: () -> Unit, onCambiarEstado: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    val acento = colorDeHex(s.especialidad?.color) ?: c.purple
    Column(
        Modifier.fillMaxWidth().alpha(if (s.activo) 1f else 0.55f).clip(forma).background(c.superficie)
            .border(1.dp, c.borde, forma)
            // Barra lateral del color de la especialidad (drawBehind, nunca IntrinsicSize).
            .drawBehind { drawRect(acento, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val esp = s.especialidad
            if (esp != null) {
                Pastilla("${esp.icono.orEmpty()} ${esp.nombre}".trim(), Color.White, colorDeHex(esp.color) ?: c.navy)
            } else {
                Pastilla(s.categoriaLibre ?: s.categoria ?: "General", c.purple, c.purpleBg)
            }
            Spacer(Modifier.weight(1f))
            if (s.activo) Pastilla("Activo", c.ok, c.okBg) else Pastilla(s.estado, c.error, c.errorBg)
        }
        Spacer(Modifier.height(6.dp))
        Text(s.nombre, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (s.esEvaluacionPsico) {
            Spacer(Modifier.height(3.dp))
            Pastilla("🧠 Evaluación psicológica", c.purple, c.purpleBg)
        }
        s.categoriaLibre?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textoSuave, fontSize = Sania.txt.mini) }
        s.descripcion?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = c.textoSuave, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(dineroActivo(s.precio), color = c.texto, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val suf = sufijoPrecio(s)
            if (suf.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(suf, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp)) }
        }
        // Odontología: si cobra por caras, ESE precio manda en el presupuesto.
        val tramos = tramosCaras(s)
        val chips = buildList {
            tramos.forEach { (k, v) -> add("🦷 ${etiquetaCaras(k)}: ${dineroActivo(v)}" to true) }
            if (s.tarifarios.isNotEmpty()) s.tarifarios.forEach { t -> add("📦 ${t.cantidadSesiones}x ${dineroActivo(t.precioTotal)}" to false) }
            else s.precioPaquete?.let { add("📦 Paquete: ${dineroActivo(it)}" to false) }
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                chips.forEach { (t, esTeal) -> if (esTeal) Pastilla(t, c.teal, c.tealBg) else Pastilla(t, c.purple, c.purpleBg) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            BotonContorno("✏ Editar") { onEditar() }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (s.activo) c.errorBg else c.okBg)
                    .clickable { onCambiarEstado() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    if (s.activo) "✗ Desactivar" else "✓ Activar",
                    color = if (s.activo) c.error else c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
internal fun Pastilla(texto: String, fg: Color, bg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(texto, color = fg, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun ChipFiltro(texto: String, sel: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (sel) c.navy else c.superficie)
            .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(texto, color = if (sel) c.sobreNavy else c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun BotonContorno(texto: String, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(texto, color = if (habilitado) c.navy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}
