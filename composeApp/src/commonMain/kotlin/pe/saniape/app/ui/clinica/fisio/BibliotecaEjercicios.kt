package pe.saniape.app.ui.clinica.fisio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.DIFICULTADES
import pe.saniape.app.data.staff.EjercicioBiblioteca
import pe.saniape.app.data.staff.EjerciciosRepo
import pe.saniape.app.data.staff.FiltrosBiblioteca
import pe.saniape.app.data.staff.MAX_EJERCICIOS_PLAN
import pe.saniape.app.data.staff.OBJETIVOS
import pe.saniape.app.data.staff.POSICIONES
import pe.saniape.app.data.staff.dosisSugerida
import pe.saniape.app.data.staff.filtrarBiblioteca
import pe.saniape.app.data.staff.nombreCondicion
import pe.saniape.app.data.staff.nombreMaterial
import pe.saniape.app.data.staff.opcionesDeBiblioteca
import pe.saniape.app.data.staff.textoDias
import pe.saniape.app.data.staff.textoDosis
import pe.saniape.app.ui.AnimacionEjercicio
import pe.saniape.app.ui.MiniaturaEjercicio
import pe.saniape.app.ui.PasosEjercicio
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * BIBLIOTECA DE EJERCICIOS — el buscador con el que el fisio elige qué le deja al
 * paciente (Ejercicios de apoyo). Se abre desde la pestaña 🏠 de la ficha. Gemelo
 * de `components/fisio/BibliotecaEjerciciosModal.tsx`.
 *
 * La biblioteca (~600) se pide UNA vez y se filtra aquí (`filtrarBiblioteca`):
 * cada chip responde al instante, sin un viaje por filtro. Se elige por región →
 * malestar, y se afina por objetivo, posición, equipo y nivel. Lo elegido entra
 * al plan con la dosis sugerida; el fisio la ajusta después.
 *
 * Los ejercicios 'pendiente' los redactó una IA y nadie clínico los revisó: se
 * marcan a la vista ("Sin revisar").
 */

private const val POR_PAGINA = 24

/** Atajo dentro de la biblioteca: repetir los de la sesión anterior en vez de elegir de nuevo. */
internal data class RepetirEjercicios(val etiqueta: String, val onRepetir: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BibliotecaEjerciciosModal(
    /** De qué clínica es la biblioteca guardada en memoria (los ejercicios propios son de cada una). */
    clinicaId: String,
    /** Para cuándo se indican: "Sesión #8 · 06/06/26 · Terapia lumbar". */
    para: String?,
    /** "Hasta la próxima sesión" · "Para seguir en casa". */
    vigencia: String?,
    repetir: RepetirEjercicios?,
    /** Ejercicios ya activos en el plan del paciente (no se pueden elegir otra vez). */
    idsEnPlan: List<String>,
    guardando: Boolean,
    onCerrar: () -> Unit,
    onAgregar: (List<String>) -> Unit,
) {
    val c = Sania.colors
    var biblioteca by remember { mutableStateOf<List<EjercicioBiblioteca>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    LaunchedEffect(clinicaId) {
        val r = EjerciciosRepo.biblioteca(clinicaId)
        if (r != null) biblioteca = r else fallo = true
    }
    val lista = biblioteca.orEmpty()

    var f by remember { mutableStateOf(FiltrosBiblioteca()) }
    var elegidos by remember { mutableStateOf<List<String>>(emptyList()) }
    var abierto by remember { mutableStateOf<String?>(null) }
    var mostrar by remember { mutableStateOf(POR_PAGINA) }
    fun cambiar(nuevo: FiltrosBiblioteca) { f = nuevo; mostrar = POR_PAGINA }

    val opciones = remember(lista, f.zona) { opcionesDeBiblioteca(lista, f.zona) }
    val resultado = remember(lista, f) { filtrarBiblioteca(lista, f) }
    val enPlan = remember(idsEnPlan) { idsEnPlan.toSet() }
    val cupo = MAX_EJERCICIOS_PLAN - idsEnPlan.size
    fun alternar(id: String) {
        elegidos = if (id in elegidos) elegidos - id else if (elegidos.size >= cupo) elegidos else elegidos + id
    }

    DialogoForm(
        titulo = "🏠 Biblioteca de ejercicios",
        subtitulo = listOfNotNull(para?.let { "Para: $it" }, vigencia).joinToString(" · ").ifEmpty { null },
        textoAccion = if (guardando) "Agregando…" else "＋ Agregar" + if (elegidos.isNotEmpty()) " ${elegidos.size}" else "",
        accionHabilitada = elegidos.isNotEmpty() && !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { if (elegidos.isNotEmpty() && !guardando) onAgregar(elegidos) },
    ) {
        if (repetir != null) {
            Text(repetir.etiqueta, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier
                .fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                .border(1.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = !guardando) { repetir.onRepetir() }.padding(horizontal = 12.dp, vertical = 10.dp))
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            colors = coloresCampoForm(),
            value = f.texto, onValueChange = { cambiar(f.copy(texto = it)) },
            placeholder = { Text("Busca por nombre o malestar: puente, lumbalgia, banda…", color = c.textoSuave, fontSize = 13.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        EtqForm("Región")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipEval("Todas", activo = f.zona == null) { cambiar(f.copy(zona = null, condicion = null)) }
            opciones.zonas.forEach { z ->
                ChipEval(z.nombre, activo = f.zona == z.id) { cambiar(f.copy(zona = if (f.zona == z.id) null else z.id, condicion = null)) }
            }
        }

        if (f.zona != null && opciones.condiciones.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Malestar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opciones.condiciones.forEach { m ->
                    ChipEval("${m.nombre} ${m.total}", activo = f.condicion == m.id) {
                        cambiar(f.copy(condicion = if (f.condicion == m.id) null else m.id))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorBiblioteca("Objetivo", f.objetivo, OBJETIVOS.map { it.id to it.nombre }, Modifier.weight(1f)) { cambiar(f.copy(objetivo = it)) }
            SelectorBiblioteca("Posición", f.posicion, POSICIONES.map { it.id to it.nombre }, Modifier.weight(1f)) { cambiar(f.copy(posicion = it)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorBiblioteca(
                "Equipo", f.equipo,
                listOf("sin_equipo" to "Sin equipo especial") + opciones.materiales.map { it.id to "Con ${it.nombre.lowercase()}" },
                Modifier.weight(1f),
            ) { cambiar(f.copy(equipo = it)) }
            SelectorBiblioteca("Nivel", f.dificultad, DIFICULTADES.map { it.id to it.nombre }, Modifier.weight(1f)) { cambiar(f.copy(dificultad = it)) }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { cambiar(f.copy(soloRevisados = !f.soloRevisados)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (f.soloRevisados) "☑" else "☐", fontSize = 18.sp, color = if (f.soloRevisados) c.navy else c.textoSuave)
            Spacer(Modifier.width(8.dp))
            Text("Solo los revisados por un fisio", color = c.texto, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    biblioteca == null && !fallo -> "Cargando…"
                    else -> "${resultado.size} ejercicio${if (resultado.size == 1) "" else "s"}"
                } + when {
                    elegidos.isEmpty() -> ""
                    else -> " · ${elegidos.size} elegido${if (elegidos.size == 1) "" else "s"}" +
                        if (elegidos.size >= cupo && cupo > 0) " (tope de $MAX_EJERCICIOS_PLAN por plan)" else ""
                },
                color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            if (f.hayFiltros) Text("Limpiar filtros", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { cambiar(FiltrosBiblioteca()) }.padding(4.dp))
        }
        if (cupo <= 0) Text("Este plan ya tiene $MAX_EJERCICIOS_PLAN ejercicios: quita alguno antes de agregar más.",
            color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(10.dp))
        when {
            fallo && biblioteca == null -> Text(
                "No se pudo cargar la biblioteca. Cierra y vuelve a abrir.", color = c.error, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            // Andamio en vez de un aro: la forma de lo que viene.
            biblioteca == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)) }
            }
            resultado.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔍", fontSize = 28.sp)
                Text("Ningún ejercicio con esos filtros", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
                Text("Quita alguno o busca por otra palabra.", color = c.textoSuave, fontSize = 12.sp)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                resultado.take(mostrar).forEach { e ->
                    TarjetaEjercicioBiblioteca(
                        e = e, elegido = e.id in elegidos, enPlan = e.id in enPlan, abierto = abierto == e.id,
                        onElegir = { alternar(e.id) }, onVer = { abierto = if (abierto == e.id) null else e.id },
                    )
                }
                if (resultado.size > mostrar) {
                    Text("Mostrar más (${resultado.size - mostrar} restantes)", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { mostrar += POR_PAGINA }.padding(vertical = 10.dp))
                }
            }
        }
    }
}

/** Desplegable de un filtro: "Cualquiera" quita el filtro. */
@Composable
private fun SelectorBiblioteca(
    label: String, valor: String?, opciones: List<Pair<String, String>>, modifier: Modifier = Modifier, onElegir: (String?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier) {
        EtqForm(label)
        Box {
            CajaSelectorForm(opciones.firstOrNull { it.first == valor }?.second ?: "Cualquiera") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Cualquiera", fontSize = 14.sp) }, onClick = { menu = false; onElegir(null) })
                opciones.forEach { (v, t) ->
                    DropdownMenuItem(text = { Text(t, fontSize = 14.sp) }, onClick = { menu = false; onElegir(v) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaEjercicioBiblioteca(
    e: EjercicioBiblioteca, elegido: Boolean, enPlan: Boolean, abierto: Boolean, onElegir: () -> Unit, onVer: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    val dosis = remember(e) { dosisSugerida(e) }
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.5.dp, if (elegido) c.navy else c.borde, forma)) {
        Row(Modifier.clickable(enabled = !enPlan) { onElegir() }.padding(10.dp)) {
            MiniaturaEjercicio(e.posturaInicialUrl, e.gifUrl, Modifier.size(width = 84.dp, height = 63.dp).clickable { onVer() })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        e.zonaNombre, OBJETIVOS.firstOrNull { it.id == e.objetivo }?.nombre,
                        DIFICULTADES.firstOrNull { it.id == e.dificultad }?.nombre,
                    ).joinToString(" · "),
                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 1.dp),
                )
                Text("${textoDosis(dosis)} · ${textoDias(dosis.dias)}", color = c.textoSuave, fontSize = 11.sp)
                // Lo redactó una IA: que el fisio lo revise antes de indicarlo.
                if (e.estadoRevision != "aprobado") Text("Sin revisar", color = c.pend, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(4.dp)).background(c.pendBg).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (enPlan) Text("✓ En el plan", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                else Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (elegido) c.navy else c.superficie)
                        .border(1.5.dp, if (elegido) c.navy else c.borde, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (elegido) Text("✓", color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Text(if (abierto) "Ocultar ▴" else "Ver ▾", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp).clickable { onVer() }.padding(4.dp))
            }
        }
        AnimatedVisibility(abierto) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimacionEjercicio(e.gifUrl, e.posturaInicialUrl, e.nombre)
                    if (e.pasos.isNotEmpty()) PasosEjercicio(e.pasos)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (e.materiales.isEmpty()) EtiquetaBiblioteca("Sin material", c.ok, c.okBg)
                        else e.materiales.forEach { EtiquetaBiblioteca(nombreMaterial(it), c.navy, c.chipBg) }
                        e.condiciones.take(4).forEach { EtiquetaBiblioteca(nombreCondicion(it), c.purple, c.purpleBg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EtiquetaBiblioteca(texto: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 2.dp))
}
