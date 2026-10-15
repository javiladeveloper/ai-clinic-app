package pe.saniape.app.ui.clinica.nutricion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.BorradorMedicion
import pe.saniape.app.data.staff.FilaHistorialMedicion
import pe.saniape.app.data.staff.MedicionCorporal
import pe.saniape.app.data.staff.MedicionesRepo
import pe.saniape.app.data.staff.MetricaMedicion
import pe.saniape.app.data.staff.RANGO_MEDICION
import pe.saniape.app.data.staff.clasificarImc
import pe.saniape.app.data.staff.historialMediciones
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.imcEnVivo
import pe.saniape.app.data.staff.lineaMedidas
import pe.saniape.app.data.staff.medicionDeJson
import pe.saniape.app.data.staff.metricasDisponibles
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.ordenarMediciones
import pe.saniape.app.data.staff.resumenMediciones
import pe.saniape.app.data.staff.serieMetrica
import pe.saniape.app.data.staff.textoCambio
import pe.saniape.app.data.staff.textoCampoMedicion
import pe.saniape.app.data.staff.ultimaTalla
import pe.saniape.app.data.staff.validarMedicion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.fisio.CampoTextoEval
import pe.saniape.app.ui.clinica.historia.GraficoLineaHc
import pe.saniape.app.ui.clinica.historia.fechaHc
import pe.saniape.app.ui.clinica.pacientes.CajaSelectorForm
import pe.saniape.app.ui.clinica.pacientes.DialogoFecha
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * Pestaña 📏 Medidas de la ficha (solo NUTRICIÓN, ver `pacienteEsNutricion`).
 * Gemelo de la pestaña de la web: registrar peso, talla y medidas por visita,
 * la evolución (primera → última y un gráfico por métrica) y el historial.
 *
 * Carga SOLO cuando la pestaña se abre (la monta el padre en su `when`): una
 * consulta chica por paciente a /api/staff/medicion-corporal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedidasNutricionTab(
    pacienteId: String,
    /** `puede('pacientes') || puede('sesiones')` y ficha no dada de baja (como el API). */
    puedeEditar: Boolean,
    /** Para clasificar el IMC (en menores de 18 no se clasifica). */
    edad: Int?,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var items by remember(pacienteId) { mutableStateOf<List<MedicionCorporal>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    LaunchedEffect(pacienteId, token) {
        val r = MedicionesRepo.listar(pacienteId)
        if (r != null) { items = r; fallo = false } else fallo = items == null
        cargando = false
    }
    var registrar by remember { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf<MedicionCorporal?>(null) }
    var borrando by remember { mutableStateOf(false) }

    if (cargando && items == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(36, 150, 110).forEach { h ->
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.chipBg))
            }
        }
        return
    }
    if (fallo) {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No se pudieron cargar las medidas", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("↻ Reintentar", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).clickable { cargando = true; token++ }.padding(8.dp))
        }
        return
    }

    val lista = items.orEmpty()
    val resumen = remember(lista, edad) { resumenMediciones(lista, edad) }
    val metricas = remember(lista) { metricasDisponibles(lista) }
    val historial = remember(lista) { historialMediciones(lista) }
    var metrica by remember { mutableStateOf(MetricaMedicion.PESO) }
    val metricaVista = if (metrica in metricas) metrica else metricas.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("📏 Peso, talla y medidas", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (puedeEditar) BotonMedidas("＋ Registrar medidas") { registrar = true }

        if (lista.isEmpty()) {
            Tarjeta {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📏", fontSize = 30.sp)
                    Text("Sin medidas registradas todavía", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp))
                    Text("Anota el peso, la talla y las medidas en cada visita: aquí verás cómo cambian el peso, la cintura y el IMC, " +
                        "y quedan en la historia clínica.",
                        color = c.textoSuave, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else {
            // ── Evolución: primera → última ──
            resumen?.let { r ->
                Tarjeta {
                    Titulo("Evolución", if (r.total > 1) " · ${fechaHc(r.desde)} → ${fechaHc(r.hasta)}" else null)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.cambioPeso?.let { d ->
                            Dato("Peso", textoCambio(d, "kg"),
                                "${numeroHc(r.pesoInicial ?: 0.0)} → ${numeroHc(r.pesoActual ?: 0.0)} kg")
                        } ?: r.pesoActual?.let { Dato("Peso", "${numeroHc(it)} kg", null) }
                        r.cambioCintura?.let { d -> Dato("Cintura", textoCambio(d, "cm"), null) }
                        r.imcActual?.let { Dato("IMC actual", numeroHc(it), r.clasificacionImc) }
                    }
                    if (r.total == 1) Text("Con una segunda medición verás el cambio.", color = c.textoSuave, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp))
                    metricaVista?.let { m ->
                        Spacer(Modifier.height(12.dp))
                        if (metricas.size > 1) {
                            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                metricas.forEach { op -> ChipMetrica(op.nombre, activo = op == m) { metrica = op } }
                            }
                        }
                        val serie = remember(lista, m) { serieMetrica(lista, m) }
                        GraficoLineaHc(
                            titulo = if (m.unidad.isNotEmpty()) "${m.nombre} (${m.unidad})" else m.nombre,
                            unidad = m.unidad, puntos = serie,
                        )
                    }
                }
            }

            // ── Historial (la más reciente primero) ──
            Tarjeta {
                Titulo("Mediciones (${lista.size})", null)
                historial.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    FilaMedicion(f, edad, puedeEditar) { confirmarBorrar = f.medicion }
                }
                Text("El cambio de peso es contra la medición anterior.", color = c.textoSuave, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
    }

    if (registrar) {
        FormularioMedidas(
            pacienteId = pacienteId,
            tallaPrevia = remember(lista) { ultimaTalla(lista) },
            edad = edad,
            onCerrar = { registrar = false },
            onGuardada = { nueva ->
                registrar = false
                // Se muestra al instante; la relectura confirma lo que quedó en la base.
                nueva?.let { n -> items = ordenarMediciones(lista.filter { it.id != n.id } + n) }
                token++
            },
        )
    }
    confirmarBorrar?.let { m ->
        AlertDialog(
            onDismissRequest = { if (!borrando) confirmarBorrar = null },
            title = { Text("¿Eliminar esta medición?", fontWeight = FontWeight.Bold) },
            text = { Text("${fechaHc(m.fecha)} · ${lineaMedidas(m)}", color = c.textoSuave) },
            confirmButton = {
                TextButton(enabled = !borrando, onClick = {
                    borrando = true
                    scope.launch {
                        val r = MedicionesRepo.borrar(m.id)
                        borrando = false
                        if (r.registrada) {
                            confirmarBorrar = null
                            items = lista.filter { it.id != m.id }
                            Toaster.exito("Medición eliminada")
                            token++
                        } else Toaster.error(r.rechazo?.error ?: "No se pudo eliminar")
                    }
                }) { Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(enabled = !borrando, onClick = { confirmarBorrar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

// ─── Formulario ──────────────────────────────────────────────────────────────

/** Etiqueta de cada campo del formulario (con su unidad). */
private val ETIQUETA_CAMPO = mapOf(
    "peso" to "Peso (kg)", "talla" to "Talla (cm)", "cintura" to "Cintura (cm)", "cadera" to "Cadera (cm)",
    "perimetro_abdominal" to "Perímetro abdominal (cm)", "grasa_pct" to "% grasa", "masa_muscular" to "Masa muscular (kg)",
)

@Composable
private fun FormularioMedidas(
    pacienteId: String,
    tallaPrevia: Double?,
    edad: Int?,
    onCerrar: () -> Unit,
    onGuardada: (MedicionCorporal?) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var fecha by remember { mutableStateOf(hoyClinicaIso()) }
    var elegirFecha by remember { mutableStateOf(false) }
    // La talla de un adulto no cambia entre visitas: se prellena con la última.
    var valores by remember { mutableStateOf(mapOf("talla" to textoCampoMedicion(tallaPrevia))) }
    var notas by remember { mutableStateOf("") }
    var errores by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    val imc = imcEnVivo(valores["peso"], valores["talla"], edad)

    fun guardar() {
        if (guardando) return
        val b = BorradorMedicion(pacienteId = pacienteId, fecha = fecha, valores = valores, notas = notas)
        val e = validarMedicion(b)
        errores = e
        if (e.isNotEmpty()) return
        guardando = true
        scope.launch {
            val r = MedicionesRepo.registrar(b)
            guardando = false
            if (r.registrada) {
                Toaster.exito("Medidas registradas")
                onGuardada((r.cuerpo?.get("medicion") as? kotlinx.serialization.json.JsonObject)?.let(::medicionDeJson))
            } else {
                val msg = r.rechazo?.error ?: "No se pudo guardar. Intenta de nuevo."
                errores = listOf(msg)
                Toaster.error(msg)
            }
        }
    }

    DialogoForm(
        titulo = "Registrar medidas",
        subtitulo = "Solo lo que mediste: todo es opcional",
        textoAccion = if (guardando) "Guardando…" else "Guardar",
        accionHabilitada = !guardando,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        EtqForm("Fecha")
        CajaSelectorForm(fechaHc(fecha)) { elegirFecha = true }
        Spacer(Modifier.height(12.dp))
        // De a dos por fila; el perímetro abdominal (etiqueta larga) va solo.
        val filas = listOf(listOf("peso", "talla"), listOf("cintura", "cadera"), listOf("perimetro_abdominal"), listOf("grasa_pct", "masa_muscular"))
        filas.forEach { fila ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                fila.forEach { k ->
                    Column(Modifier.weight(1f)) {
                        EtqForm(ETIQUETA_CAMPO[k] ?: k)
                        val r = RANGO_MEDICION[k]
                        CampoTextoEval(
                            valor = valores[k].orEmpty(),
                            onChange = { nuevo ->
                                // Solo dígitos y un separador decimal (coma o punto).
                                val limpio = nuevo.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                                valores = valores + (k to limpio)
                            },
                            placeholder = r?.let { "${numeroHc(it.min)}–${numeroHc(it.max)}" } ?: "",
                            modifier = Modifier.fillMaxWidth(),
                            teclado = KeyboardType.Decimal,
                        )
                    }
                }
                if (fila.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        imc?.let { i ->
            Text(
                "IMC ${numeroHc(i.valor)}" + (i.clasificacion?.let { " · $it" } ?: if (edad != null && edad < 18) " · en menores se lee con tablas por edad" else ""),
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
        EtqForm("Notas")
        OutlinedTextField(
            value = notas, onValueChange = { notas = it.take(2000) },
            placeholder = { Text("Ej: medido en ayunas, con ropa ligera…", color = c.textoSuave, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            colors = coloresCampoForm(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        if (errores.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.errorBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) { errores.forEach { Text(it, color = c.error, fontSize = 12.sp) } }
        }
    }
    if (elegirFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { elegirFecha = false }, inicial = fecha)
}

// ─── Piezas ──────────────────────────────────────────────────────────────────

@Composable
private fun FilaMedicion(f: FilaHistorialMedicion, edad: Int?, puedeEditar: Boolean, onBorrar: () -> Unit) {
    val c = Sania.colors
    val m = f.medicion
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fechaHc(m.fecha), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Signo NEUTRO: bajar de peso no siempre es lo buscado.
            f.deltaPeso?.let { d ->
                Text(textoCambio(d, "kg"), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.chipBg).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (puedeEditar) {
                Text("🗑", fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp).clickable(onClick = onBorrar).padding(6.dp))
            }
        }
        Text(lineaMedidas(m), color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        f.imc?.let { i ->
            Text("IMC ${numeroHc(i)}" + (clasificarImc(i, edad)?.let { " · $it" } ?: ""), color = c.textoSuave, fontSize = 11.sp)
        }
        m.notas?.let { Text(it, color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
private fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
private fun Titulo(titulo: String, extra: String?) {
    val c = Sania.colors
    Row(Modifier.padding(bottom = 8.dp)) {
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        extra?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
    }
}

/** Un dato del resumen: etiqueta, valor grande y una línea opcional debajo. */
@Composable
private fun Dato(etiqueta: String, valor: String, detalle: String?) {
    val c = Sania.colors
    Column(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(etiqueta.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(valor, color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        detalle?.let { Text(it, color = c.textoSuave, fontSize = 10.sp) }
    }
}

@Composable
private fun ChipMetrica(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun BotonMedidas(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Text(texto, color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier
        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.navy)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp))
}
