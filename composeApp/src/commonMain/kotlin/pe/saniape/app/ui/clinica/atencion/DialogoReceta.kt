package pe.saniape.app.ui.clinica.atencion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.ProfesionalPlan
import pe.saniape.app.data.staff.SugerenciaMedicamento
import pe.saniape.app.data.staff.formatearNumeroReceta
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.sumarDiasIso
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 📝 NUEVA RECETA — gemela de components/recetas/RecetaForm.tsx (DS 014-2011-SA
// art. 56). Emite por POST /api/staff/receta/emitir (contrato §11): el servidor
// valida (validarReceta, controlados, prescriptor ajeno) y devuelve los errores
// en un 422 que se muestran arriba. Una receta emitida NO se edita.
//
// Las listas y helpers de abajo copian lib/recetas.ts, lib/recetas-acciones.ts
// y lib/atencion-medica.ts: si cambian allá, cambian acá.
// ─────────────────────────────────────────────────────────────────────────────

internal val FORMAS_FARMACEUTICAS = listOf(
    "Tableta", "Tableta recubierta", "Tableta de liberación modificada", "Tableta sublingual",
    "Cápsula", "Jarabe", "Suspensión", "Líquido oral", "Gotas orales", "Solución oral", "Polvo",
    "Inyectable", "Crema", "Ungüento", "Gel", "Loción", "Solución tópica", "Solución oftálmica",
    "Solución ótica", "Spray nasal", "Inhalador", "Solución para nebulizar", "Supositorio", "Óvulo",
    "Parche transdérmico", "Enjuague bucal",
)

internal val VIAS_ADMINISTRACION = listOf(
    "Oral", "Sublingual", "Tópica", "Intramuscular", "Intravenosa", "Subcutánea", "Oftálmica",
    "Ótica", "Nasal", "Inhalatoria", "Rectal", "Vaginal", "Transdérmica", "Bucal",
)

internal val VIGENCIAS_SUGERIDAS = listOf(7, 15, 30, 60, 90)
internal const val VIGENCIA_POR_DEFECTO = 30
internal const val MAX_ITEMS_RECETA = 12

private val FRECUENCIAS = listOf(
    "cada 4 horas", "cada 6 horas", "cada 8 horas", "cada 12 horas", "cada 24 horas",
    "una vez al día", "dos veces al día", "tres veces al día", "antes de dormir", "condicional a dolor",
)
private val DURACIONES = listOf("1 día", "3 días", "5 días", "7 días", "10 días", "14 días", "21 días", "30 días", "uso continuo")

/** Rubros cuyas especialidades recetan (RUBROS_QUE_RECETAN). */
internal val RUBROS_QUE_RECETAN = setOf("medicina_general", "odontologia", "ginecologia")

internal const val AVISO_PRESCRIPTOR_NO_MEDICO =
    "Legalmente solo los médicos, cirujano-dentistas y obstetras prescriben medicamentos (Ley 26842, art. 26). " +
        "Como profesional de otra especialidad puedes dar INDICACIONES (ejercicios, cuidados, productos de venta libre); " +
        "los medicamentos controlados no se pueden emitir."

// ── Puras (testeables) ───────────────────────────────────────────────────────

private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

/** Minúsculas y sin tildes (el `sinTildes` de la web). */
internal fun sinTildes(s: String): String = s.lowercase().map { TILDES[it] ?: it }.joinToString("")

/** Vía que se sugiere al elegir la forma (viaSugerida de la web). */
internal fun viaSugerida(forma: String?): String {
    val f = sinTildes(forma.orEmpty())
    if (f.isEmpty()) return ""
    return when {
        "sublingual" in f -> "Sublingual"
        "oftalm" in f -> "Oftálmica"
        "otica" in f -> "Ótica"
        "nasal" in f -> "Nasal"
        "inhal" in f || "nebuli" in f -> "Inhalatoria"
        "supositorio" in f -> "Rectal"
        "ovulo" in f -> "Vaginal"
        "parche" in f -> "Transdérmica"
        "enjuague" in f -> "Bucal"
        Regex("crema|unguento|gel|locion|topica").containsMatchIn(f) -> "Tópica"
        "inyect" in f -> ""
        Regex("tableta|capsula|jarabe|suspension|liquido|gotas|solucion oral|polvo").containsMatchIn(f) -> "Oral"
        else -> ""
    }
}

/** Unidad en que se cuenta la cantidad total según la forma (unidadSugerida de la web). */
internal fun unidadSugerida(forma: String?): String {
    val f = sinTildes(forma.orEmpty())
    if (f.isEmpty()) return ""
    return when {
        "tableta" in f -> "tabletas"
        "capsula" in f -> "cápsulas"
        "inyect" in f -> "ampollas"
        "supositorio" in f -> "supositorios"
        "ovulo" in f -> "óvulos"
        "parche" in f -> "parches"
        "inhalador" in f -> "inhaladores"
        "polvo" in f -> "sobres"
        Regex("crema|unguento|gel").containsMatchIn(f) -> "tubos"
        else -> "frascos"
    }
}

/** Sugerencias cuyo DCI (o una de sus palabras) empieza con lo escrito (filtrarSugerencias). */
internal fun filtrarSugerencias(lista: List<SugerenciaMedicamento>, texto: String, max: Int = 8): List<SugerenciaMedicamento> {
    val q = sinTildes(texto).trim()
    if (q.length < 2) return emptyList()
    val separadores = Regex("[\\s+(/,-]+")
    return lista.filter { s ->
        val d = sinTildes(s.dci)
        d.startsWith(q) || d.split(separadores).any { it.startsWith(q) }
    }.take(max)
}

/** ¿Alguno de estos rubros receta? (esPrescriptorLegal de la web). */
internal fun esPrescriptorLegal(rubros: List<String?>): Boolean = rubros.any { it != null && it in RUBROS_QUE_RECETAN }

/**
 * Quién puede figurar como prescriptor (aprox. de `puedePrescribir` con el mapa de
 * la clínica, que la app no tiene): activo (o el ya elegido) y con colegiatura
 * (≥ 3 caracteres). En modo "indicaciones" ([recetasOptIn], clínica no médica)
 * todos; si no, los que tienen alguna especialidad que receta o sin rubro cargado.
 */
internal fun prescriptoresReceta(
    profesionales: List<ProfesionalPlan>, recetasOptIn: Boolean, elegidoId: String? = null,
): List<ProfesionalPlan> = profesionales.filter { p ->
    (p.estado != "Inactivo" || p.id == elegidoId) &&
        (p.cmp?.trim()?.length ?: 0) >= 3 &&
        (recetasOptIn || p.especialidades.isEmpty() || p.especialidades.any { it.rubro == null || it.rubro in RUBROS_QUE_RECETAN })
}

/** El prescriptor no es de un rubro que receta: su hoja sale como INDICACIONES. */
internal fun prescriptorNoMedico(p: ProfesionalPlan?): Boolean =
    p != null && p.especialidades.isNotEmpty() && !esPrescriptorLegal(p.especialidades.map { it.rubro })

/** Colegiatura para mostrar (colegiaturaImpresa de la web: CMP / COP / CTMP). */
internal fun colegiaturaImpresa(p: ProfesionalPlan): String {
    val v = p.cmp?.trim().orEmpty()
    if (v.isEmpty()) return ""
    if (!Regex("^[0-9][0-9 .-]*$").matches(v)) return v
    val rubros = p.especialidades.mapNotNull { it.rubro }
    val receta = rubros.any { it in RUBROS_QUE_RECETAN }
    val otro = rubros.any { it !in RUBROS_QUE_RECETAN }
    if (otro && !receta) return if ("fisioterapia" in rubros) "CTMP $v" else v
    val soloDental = rubros.isNotEmpty() && rubros.all { it == "odontologia" }
    return "${if (soloDental) "COP" else "CMP"} $v"
}

/** Un medicamento del formulario (todo texto; `cantidad` se convierte al emitir). */
internal data class ItemRecetaForm(
    val dci: String = "",
    val marca: String = "",
    val concentracion: String = "",
    val forma: String = "",
    val via: String = "",
    val dosis: String = "",
    val frecuencia: String = "",
    val duracion: String = "",
    val cantidad: String = "",
    val unidad: String = "",
    val indicaciones: String = "",
) {
    /** Sin nada escrito no cuenta (esFilaVacia: forma/vía/unidad solas no la llenan). */
    val vacio: Boolean
        get() = listOf(dci, marca, concentracion, dosis, frecuencia, duracion, indicaciones).all { it.isBlank() } &&
            (cantidadNumero(cantidad) ?: 0.0) <= 0.0
}

/** "15" / "7,5" → número; vacío o inválido → null. */
internal fun cantidadNumero(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Clave de idempotencia: un UUID v4 por intento (la columna `clave_cliente` es UUID). */
internal fun nuevaClaveCliente(): String {
    val b = Random.nextBytes(16)
    b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte()
    b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val h = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-${h.substring(16, 20)}-${h.substring(20)}"
}

/**
 * El cuerpo de `POST /api/staff/receta/emitir` (contrato §11). Textos recortados
 * (vacío → null); las filas vacías no viajan; `cantidad` como número.
 */
internal fun cuerpoEmitirReceta(
    pacienteId: String,
    terapeutaId: String?,
    fecha: String,
    vigenciaDias: Int,
    diagnostico: String?,
    cie10: String?,
    indicacionesGenerales: String?,
    infoFarmaceutico: String?,
    items: List<ItemRecetaForm>,
    citaId: String?,
    tratamientoId: String?,
    claveCliente: String,
): JsonObject {
    fun t(s: String?): JsonPrimitive = s?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull
    return buildJsonObject {
        put("pacienteId", pacienteId)
        put("terapeutaId", t(terapeutaId))
        put("fecha", fecha)
        put("vigenciaDias", vigenciaDias)
        put("diagnostico", t(diagnostico))
        put("cie10", t(cie10?.uppercase()))
        put("indicacionesGenerales", t(indicacionesGenerales))
        put("infoFarmaceutico", t(infoFarmaceutico))
        put("items", JsonArray(items.filterNot { it.vacio }.map { f ->
            buildJsonObject {
                put("dci", t(f.dci))
                put("marca", t(f.marca))
                put("concentracion", t(f.concentracion))
                put("forma", t(f.forma))
                put("via", t(f.via))
                put("dosis", t(f.dosis))
                put("frecuencia", t(f.frecuencia))
                put("duracion", t(f.duracion))
                put("cantidad", cantidadNumero(f.cantidad)?.let { JsonPrimitive(it) } ?: JsonNull)
                put("unidad", t(f.unidad.ifBlank { unidadSugerida(f.forma) }))
                put("indicaciones", t(f.indicaciones))
            }
        }))
        put("citaId", t(citaId))
        put("tratamientoId", t(tratamientoId))
        put("claveCliente", claveCliente)
    }
}

/** "2026-09-30" → "30/09/2026" (fechaCorta de la web). */
private fun fechaCorta(iso: String): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return iso
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

// ── El diálogo ───────────────────────────────────────────────────────────────

/**
 * Receta del paciente [pacienteId] desde la consulta. [profesionales] = el equipo
 * de la consulta (se ofrecen los que pueden prescribir). [diagnostico]/[cie10] y
 * los vínculos ([citaId], [tratamientoId]) la prellenan. [recetasOptIn]: clínica
 * no médica con recetas encendidas (modo "indicaciones").
 * [onEmitida] recibe el id de la receta (el toast con el número ya lo muestra el diálogo).
 */
@Composable
fun DialogoReceta(
    ctx: ContextoStaff,
    pacienteId: String,
    profesionales: List<ProfesionalPlan>,
    diagnostico: String?,
    cie10: String?,
    citaId: String?,
    tratamientoId: String?,
    recetasOptIn: Boolean,
    onCancelar: () -> Unit,
    onEmitida: (recetaId: String) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val hoy = remember { hoyClinicaIso() }
    // El profesional vinculado firma a su nombre (el servidor también lo exige:
    // PRESCRIPTOR_AJENO); recepción / Admin / quien gestiona pacientes elige.
    val bloqueadoASiMismo = ctx.miTerapeutaId != null && !ctx.esAdmin && !ctx.puede("pacientes")
    val prescriptores = remember(profesionales, recetasOptIn) { prescriptoresReceta(profesionales, recetasOptIn) }

    var terapeutaId by remember {
        mutableStateOf(
            when {
                bloqueadoASiMismo -> ctx.miTerapeutaId.orEmpty()
                prescriptores.any { it.id == ctx.miTerapeutaId } -> ctx.miTerapeutaId.orEmpty()
                prescriptores.size == 1 -> prescriptores[0].id
                else -> ""
            },
        )
    }
    var vigencia by remember { mutableStateOf(VIGENCIA_POR_DEFECTO.toString()) }
    var dx by remember { mutableStateOf(diagnostico.orEmpty()) }
    var cie by remember { mutableStateOf(cie10.orEmpty().uppercase().take(7)) }
    var indicaciones by remember { mutableStateOf("") }
    var infoQf by remember { mutableStateOf("") }
    var verInfoQf by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf(ItemRecetaForm()) }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var emitiendo by remember { mutableStateOf(false) }
    var sugerencias by remember { mutableStateOf<List<SugerenciaMedicamento>>(emptyList()) }
    // Un intento = una clave: reintentar tras un corte no emite dos recetas ni gasta dos números.
    val clave = remember { nuevaClaveCliente() }

    LaunchedEffect(Unit) { sugerencias = AtencionRepo.sugerenciasMedicamentos() }

    val prescriptor = profesionales.firstOrNull { it.id == terapeutaId }
    val noMedico = prescriptorNoMedico(prescriptor) || (recetasOptIn && prescriptor == null)
    val esDentista = prescriptor != null && prescriptor.especialidades.isNotEmpty() &&
        prescriptor.especialidades.all { it.rubro == "odontologia" }
    val dias = vigencia.toIntOrNull() ?: 0
    val validaHasta = sumarDiasIso(hoy, dias.coerceIn(1, 365))

    fun emitir() {
        if (emitiendo) return
        emitiendo = true
        errores = emptyList()
        val cuerpo = cuerpoEmitirReceta(
            pacienteId = pacienteId, terapeutaId = terapeutaId, fecha = hoy, vigenciaDias = dias,
            diagnostico = dx, cie10 = cie, indicacionesGenerales = indicaciones,
            infoFarmaceutico = if (verInfoQf) infoQf else null, items = items.toList(),
            citaId = citaId, tratamientoId = tratamientoId, claveCliente = clave,
        )
        scope.launch {
            val r = AtencionRepo.emitirReceta(cuerpo)
            emitiendo = false
            if (r.registrada) {
                val receta = r.cuerpo?.get("receta") as? JsonObject
                val id = (receta?.get("id") as? JsonPrimitive)?.contentOrNull.orEmpty()
                val numero = (receta?.get("numero") as? JsonPrimitive)?.intOrNull
                val tipo = if (noMedico) "Indicaciones" else "Receta"
                Toaster.exito("$tipo ${formatearNumeroReceta(numero)} emitida" + if (noMedico) "s" else "")
                onEmitida(id)
            } else {
                val lista = r.rechazo?.error?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null } ?: listOf("No se pudo emitir la receta.")
                errores = lista
                Toaster.error(lista.first())
            }
        }
    }

    DialogoForm(
        titulo = if (noMedico) "📝 Indicaciones" else "📝 Nueva receta",
        subtitulo = "Emitida hoy ${fechaCorta(hoy)} · válida hasta ${fechaCorta(validaHasta)}",
        textoAccion = when {
            emitiendo -> "Emitiendo…"
            noMedico -> "Emitir indicaciones"
            else -> "Emitir receta"
        },
        accionHabilitada = !emitiendo && prescriptores.isNotEmpty(),
        onCancelar = { if (!emitiendo) onCancelar() },
        onAccion = ::emitir,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Errores del servidor (422 de validarReceta, prescriptor ajeno…): arriba y en rojo.
            if (errores.isNotEmpty()) {
                CajaAviso(errores.joinToString("\n") { "• $it" }, c.error, c.errorBg)
            }
            if (prescriptores.isEmpty()) {
                CajaAviso(
                    "Ningún profesional que receta tiene su N° de colegiatura cargado. Complétalo en " +
                        "Equipo → profesional (CMP para médicos, COP para odontólogos): la receta lo exige impreso.",
                    c.pend, c.pendBg,
                )
            }

            // ── Prescriptor ──
            Column {
                EtqForm("Prescriptor")
                val opciones = prescriptores.map { it.id to "${it.nombre} · ${colegiaturaImpresa(it)}" }
                if (bloqueadoASiMismo) {
                    val p = prescriptores.firstOrNull { it.id == terapeutaId }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                    ) {
                        Text(p?.let { "${it.nombre} · ${colegiaturaImpresa(it)}" } ?: prescriptor?.nombre ?: "—",
                            color = c.texto, fontSize = 14.sp)
                    }
                    if (p == null) {
                        Text("Tu ficha de profesional no tiene colegiatura o no es de una especialidad que receta.",
                            color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else {
                    SelectorDialogo("", terapeutaId, opciones) { terapeutaId = it }
                }
            }

            // ── Vigencia ──
            Column {
                EtqForm("Vigencia")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VIGENCIAS_SUGERIDAS.forEach { d ->
                        ChipVigencia("$d d", activo = dias == d) { vigencia = d.toString() }
                    }
                }
                Spacer(Modifier.height(6.dp))
                CampoDialogo("Días de vigencia", vigencia, { v -> vigencia = v.filter { it.isDigit() }.take(3) },
                    teclado = KeyboardType.Number, modifier = Modifier.widthIn(max = 160.dp))
            }

            if (noMedico && prescriptor != null) {
                CajaAviso("⚖ $AVISO_PRESCRIPTOR_NO_MEDICO Si solo das indicaciones, deja la lista de productos vacía.", c.texto, c.pendBg)
            }
            if (esDentista) {
                Text("El cirujano-dentista prescribe solo dentro del área de su profesión (Ley 26842, art. 26).",
                    color = c.textoSuave, fontSize = 12.sp)
            }

            // ── Diagnóstico ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoDialogo("Diagnóstico", dx, { dx = it }, "Ej. Faringitis aguda", modifier = Modifier.weight(1f))
                CampoDialogo("CIE-10 (opcional)", cie, { cie = it.uppercase().take(7) }, "J02.9",
                    capitalizacion = KeyboardCapitalization.Characters, modifier = Modifier.width(120.dp))
            }

            // ── Medicamentos (Rp.) ──
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (noMedico) "Productos sugeridos (opcional)" else "Rp. — Medicamentos",
                        color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("DCI obligatoria · marca opcional", color = c.textoSuave, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                items.forEachIndexed { i, fila ->
                    FilaMedicamento(
                        n = i + 1, item = fila, sugerencias = sugerencias,
                        onCambio = { nuevo -> items[i] = nuevo },
                        onQuitar = if (items.size > 1) ({ items.removeAt(i) }) else null,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (items.size < MAX_ITEMS_RECETA) {
                    Text(
                        if (noMedico) "+ Agregar producto" else "+ Agregar medicamento",
                        color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { items.add(ItemRecetaForm()) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }

            CampoDialogo("Indicaciones generales (opcional)", indicaciones, { indicaciones = it },
                "Ej. Abundantes líquidos, reposo 48 h, control en 7 días.", unaLinea = false, minLineas = 2)
            if (verInfoQf) {
                CampoDialogo("Información para el químico farmacéutico", infoQf, { infoQf = it },
                    "Ej. No sustituir por otra forma farmacéutica.")
            } else {
                Text(
                    "+ Nota para el químico farmacéutico", color = c.textoSuave, fontSize = 13.sp,
                    modifier = Modifier.clickable { verInfoQf = true }.padding(vertical = 6.dp),
                )
            }
            Text(
                "Una receta emitida no se edita (sin enmendaduras): si algo sale mal, se anula y se emite otra. " +
                    "Se imprime para firma y sello del prescriptor.",
                color = c.textoSuave, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ChipVigencia(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.pill.dp)
    Box(
        Modifier.heightIn(min = 32.dp).clip(forma).background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** Una fila "N." del Rp. (FilaMedicamento de la web). */
@Composable
private fun FilaMedicamento(
    n: Int,
    item: ItemRecetaForm,
    sugerencias: List<SugerenciaMedicamento>,
    onCambio: (ItemRecetaForm) -> Unit,
    onQuitar: (() -> Unit)?,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    var abierto by remember { mutableStateOf(false) }
    val opciones = if (abierto) filtrarSugerencias(sugerencias, item.dci) else emptyList()

    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).background(c.fondo).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$n.", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (onQuitar != null) {
                Text("Quitar", color = c.error, fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = onQuitar).padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        CampoDialogo("Medicamento (DCI)", item.dci, { onCambio(item.copy(dci = it)); abierto = true }, "Ej. Amoxicilina")
        if (opciones.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).background(c.superficie),
            ) {
                opciones.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                            onCambio(
                                item.copy(
                                    dci = s.dci,
                                    concentracion = s.concentracion?.ifBlank { null } ?: item.concentracion,
                                    forma = s.forma?.ifBlank { null } ?: item.forma,
                                    via = s.via?.ifBlank { null } ?: item.via.ifBlank { viaSugerida(s.forma) },
                                    unidad = item.unidad.ifBlank { unidadSugerida(s.forma) },
                                ),
                            )
                            abierto = false
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildString {
                                append(s.dci)
                                s.concentracion?.takeIf { it.isNotBlank() }?.let { append(" $it") }
                                s.forma?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                            },
                            color = c.texto, fontSize = 13.sp, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        if (s.origen == "clinica") Text("recetado ${s.usos}×", color = c.teal, fontSize = 11.sp)
                        else Text("PNUME", color = c.textoSuave, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDialogo("Concentración", item.concentracion, { onCambio(item.copy(concentracion = it)) }, "500 mg",
                modifier = Modifier.weight(1f))
            CampoDialogo("Marca (opcional)", item.marca, { onCambio(item.copy(marca = it)) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorDialogo("Forma", item.forma, FORMAS_FARMACEUTICAS.map { it to it }, Modifier.weight(1f)) { f ->
                onCambio(item.copy(forma = f, via = item.via.ifBlank { viaSugerida(f) }, unidad = item.unidad.ifBlank { unidadSugerida(f) }))
            }
            SelectorDialogo("Vía", item.via, VIAS_ADMINISTRACION.map { it to it }, Modifier.weight(1f)) { v ->
                onCambio(item.copy(via = v))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDialogo("Dosis por toma", item.dosis, { onCambio(item.copy(dosis = it)) }, "1 tableta", modifier = Modifier.weight(1f))
            CampoConPresets("Frecuencia", item.frecuencia, FRECUENCIAS, "cada 8 horas", Modifier.weight(1f)) {
                onCambio(item.copy(frecuencia = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoConPresets("Duración", item.duracion, DURACIONES, "7 días", Modifier.weight(1f)) {
                onCambio(item.copy(duracion = it))
            }
            CampoDialogo("Cantidad total", item.cantidad,
                { v -> onCambio(item.copy(cantidad = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7))) },
                teclado = KeyboardType.Decimal, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoDialogo("Unidad", item.unidad, { onCambio(item.copy(unidad = it)) },
                unidadSugerida(item.forma).ifEmpty { "tabletas" }, capitalizacion = KeyboardCapitalization.None,
                modifier = Modifier.weight(1f))
            CampoDialogo("Indicación", item.indicaciones, { onCambio(item.copy(indicaciones = it)) }, "después de comer",
                modifier = Modifier.weight(1f))
        }
    }
}

/** Texto libre con una lista de valores frecuentes (el `<datalist>` de la web) en ▾. */
@Composable
private fun CampoConPresets(
    label: String, valor: String, presets: List<String>, placeholder: String, modifier: Modifier, onChange: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        CampoDialogo(
            label, valor, onChange, placeholder, capitalizacion = KeyboardCapitalization.None,
            trailing = {
                Text("▾", color = Sania.colors.navy, fontSize = 16.sp,
                    modifier = Modifier.clickable { menu = true }.padding(horizontal = 10.dp, vertical = 8.dp))
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            presets.forEach { p ->
                DropdownMenuItem(text = { Text(p, fontSize = 14.sp) }, onClick = { menu = false; onChange(p) })
            }
        }
    }
}
