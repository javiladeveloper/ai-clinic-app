package pe.saniape.app.ui.clinica.especialidades

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.EspecialidadApp
import pe.saniape.app.data.staff.EspecialidadesRepo
import pe.saniape.app.data.staff.ServicioSugerido
import pe.saniape.app.data.staff.SugerenciasEspecialidad
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * Una fila de la lista: el servicio sugerido (o propio) + lo que la persona tocó.
 * Inmutable: cada cambio reemplaza la fila en la lista (Compose ve el cambio).
 * [clave] es estable para `key(...)`: no depende de la posición ni del nombre.
 */
private data class FilaServicio(
    val clave: Int,
    val base: ServicioSugerido,
    val marcado: Boolean,
    val precioTexto: String,
)

/** Precio para el campo: "120" o "120.50" (sin ceros de más); 0 = vacío. */
private fun precioATexto(v: Double): String {
    if (v <= 0.0) return ""
    val centimos = kotlin.math.round(v * 100).toLong()
    val entero = centimos / 100
    val resto = centimos % 100
    return if (resto == 0L) entero.toString() else "$entero.${resto.toString().padStart(2, '0')}"
}

/** Lo tecleado a número; vacío o ilegible = 0 (el servicio se carga sin precio). */
private fun textoAPrecio(t: String): Double =
    t.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0

/** Solo dígitos y un único separador decimal. */
private fun filtrarPrecio(t: String): String {
    val limpio = t.filter { it.isDigit() || it == '.' || it == ',' }
    val i = limpio.indexOfFirst { it == '.' || it == ',' }
    return if (i < 0) limpio else limpio.substring(0, i + 1) + limpio.substring(i + 1).filter { it.isDigit() }
}

/**
 * Asistente de carga inicial de una especialidad (gemelo de
 * components/especialidades/AsistenteEspecialidad.tsx en la web).
 * Lo que se sugiere lo decide el servidor (`sugerencias`); la app solo deja
 * elegir, ajustar precios y sumar servicios propios, y manda todo a `sembrar`.
 */
@Composable
fun AsistenteEspecialidad(
    especialidad: EspecialidadApp,
    onCerrar: () -> Unit,
    onCargado: () -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var cargando by remember(especialidad.id) { mutableStateOf(true) }
    var fallo by remember(especialidad.id) { mutableStateOf(false) }
    var sugerencias by remember(especialidad.id) { mutableStateOf(SugerenciasEspecialidad()) }
    val filas = remember(especialidad.id) { mutableStateListOf<FilaServicio>() }
    var siguienteClave by remember(especialidad.id) { mutableIntStateOf(0) }
    var conImagenes by remember(especialidad.id) { mutableStateOf(true) }
    var conTipicos by remember(especialidad.id) { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }

    var propioNombre by remember { mutableStateOf("") }
    var propioPrecio by remember { mutableStateOf("") }

    LaunchedEffect(especialidad.id) {
        val r = EspecialidadesRepo.sugerencias(especialidad.id)
        fallo = r == null
        val s = r ?: SugerenciasEspecialidad()
        sugerencias = s
        filas.clear()
        s.servicios.forEachIndexed { i, sv ->
            filas.add(FilaServicio(clave = i, base = sv, marcado = true, precioTexto = precioATexto(sv.precio)))
        }
        siguienteClave = s.servicios.size
        cargando = false
    }

    val nImagenes = sugerencias.tiposImagen.size
    val nTipicos = sugerencias.tipicos
    val elegidos = filas.filter { it.marcado && it.base.nombre.isNotBlank() }
    val imagenesActivas = conImagenes && nImagenes > 0
    val tipicosActivos = conTipicos && nTipicos > 0
    val hayAlgo = elegidos.isNotEmpty() || imagenesActivas || tipicosActivos
    val textoAccion = when {
        ocupado -> "Cargando…"
        elegidos.size == 1 -> "Cargar 1 servicio"
        elegidos.size > 1 -> "Cargar ${elegidos.size} servicios"
        else -> "Cargar"
    }

    /** Reemplaza la fila [clave] (por clave, no por índice: robusto ante altas). */
    fun cambiarFila(clave: Int, cambio: (FilaServicio) -> FilaServicio) {
        val j = filas.indexOfFirst { it.clave == clave }
        if (j >= 0) filas[j] = cambio(filas[j])
    }

    fun agregarPropio() {
        val nombre = propioNombre.trim()
        if (nombre.isEmpty()) return
        filas.add(
            FilaServicio(
                clave = siguienteClave,
                base = ServicioSugerido(nombre = nombre, precio = textoAPrecio(propioPrecio)),
                marcado = true,
                precioTexto = propioPrecio,
            )
        )
        siguienteClave += 1
        propioNombre = ""
        propioPrecio = ""
    }

    fun cargar() {
        if (ocupado || cargando || !hayAlgo) return
        val servicios = elegidos.map { it.base.copy(precio = textoAPrecio(it.precioTexto)) }
        val conImg = imagenesActivas
        val conTip = tipicosActivos
        ocupado = true
        scope.launch {
            val r = try {
                EspecialidadesRepo.sembrar(especialidad.id, servicios, conImg, conTip)
            } finally {
                ocupado = false
            }
            val rechazo = r.rechazo
            when {
                rechazo != null -> Toaster.error(rechazo.error)
                r.encolada -> {
                    // No debería pasar (sembrar no usa la cola offline); si pasa, se avisa.
                    Toaster.info("Quedó pendiente: se cargará cuando vuelva la conexión.")
                    onCerrar()
                }
                r.registrada -> {
                    Toaster.exito(EspecialidadesRepo.resumenSembrado(r.cuerpo))
                    onCargado()
                    onCerrar()
                }
                else -> Toaster.error("No se pudo cargar. Intenta de nuevo.")
            }
        }
    }

    DialogoForm(
        titulo = "✨ Prepara ${especialidad.nombre}",
        subtitulo = "Servicios con precio, tipos de imagen y procedimientos típicos",
        textoAccion = textoAccion,
        accionHabilitada = !cargando && !ocupado && hayAlgo,
        onCancelar = onCerrar,
        onAccion = { cargar() },
        textoCancelar = "Ahora no",
    ) {
        if (cargando) {
            Text(
                "Cargando sugerencias…", color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                modifier = Modifier.padding(vertical = Sania.dim.lg),
            )
            return@DialogoForm
        }

        if (filas.isEmpty() && nImagenes == 0 && nTipicos == 0) {
            Text(
                if (fallo) "No pudimos traer las sugerencias. Revisa tu conexión, o agrega tus servicios aquí o en la web."
                else "No tenemos sugerencias para esta especialidad. Agrega tus servicios aquí o en la web.",
                color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                modifier = Modifier.padding(bottom = Sania.dim.md),
            )
        }

        if (filas.isNotEmpty()) {
            EtqForm("Servicios")
            Column(verticalArrangement = Arrangement.spacedBy(Sania.dim.sm)) {
                filas.forEach { fila ->
                    key(fila.clave) {
                        FilaServicioUI(
                            fila = fila,
                            onMarcar = { m -> cambiarFila(fila.clave) { it.copy(marcado = m) } },
                            onPrecio = { t -> cambiarFila(fila.clave) { it.copy(precioTexto = filtrarPrecio(t)) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.lg))
        }

        // Servicio propio: nombre + precio → se suma marcado al final de la lista.
        EtqForm("Agregar servicio propio")
        OutlinedTextField(
            value = propioNombre,
            onValueChange = { propioNombre = it },
            placeholder = { Text("Nombre del servicio") },
            singleLine = true,
            colors = coloresCampoForm(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Sania.dim.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = propioPrecio,
                onValueChange = { propioPrecio = filtrarPrecio(it) },
                prefix = { Text("S/ ") },
                placeholder = { Text("Precio") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = coloresCampoForm(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Sania.dim.sm))
            val puedeAgregar = propioNombre.isNotBlank()
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (puedeAgregar) c.navy else c.borde)
                    .clickable(enabled = puedeAgregar) { agregarPropio() }
                    .padding(horizontal = Sania.dim.lg, vertical = 14.dp),
            ) {
                Text(
                    "Agregar", color = if (puedeAgregar) c.sobreNavy else c.textoSuave,
                    fontWeight = FontWeight.Bold, fontSize = Sania.txt.cuerpo,
                )
            }
        }

        if (nImagenes > 0 || nTipicos > 0) {
            Spacer(Modifier.height(Sania.dim.lg))
            EtqForm("También")
        }
        if (nImagenes > 0) {
            CasillaExtra(
                marcado = conImagenes,
                onMarcar = { conImagenes = it },
                titulo = "Tipos de imagen sugeridos ($nImagenes)",
                detalle = sugerencias.tiposImagen.sortedBy { it.orden }.joinToString(", ") { it.nombre },
            )
        }
        if (nTipicos > 0) {
            CasillaExtra(
                marcado = conTipicos,
                onMarcar = { conTipicos = it },
                titulo = "Procedimientos típicos con consentimiento ($nTipicos)",
                detalle = null,
            )
        }
    }
}

@Composable
private fun FilaServicioUI(
    fila: FilaServicio,
    onMarcar: (Boolean) -> Unit,
    onPrecio: (String) -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma)
            .background(if (fila.marcado) c.superficie else c.fondo)
            .border(1.dp, if (fila.marcado) c.lav else c.borde, forma)
            .padding(end = Sania.dim.md, top = Sania.dim.xs, bottom = Sania.dim.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = fila.marcado,
                onCheckedChange = onMarcar,
                colors = CheckboxDefaults.colors(checkedColor = c.navy),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    fila.base.nombre, color = if (fila.marcado) c.texto else c.textoSuave,
                    fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Sania.dim.xs))
                PastillaCategoria(fila.base.categoria)
            }
            Spacer(Modifier.width(Sania.dim.sm))
            OutlinedTextField(
                value = fila.precioTexto,
                onValueChange = onPrecio,
                prefix = { Text("S/ ") },
                placeholder = { Text("0") },
                singleLine = true,
                enabled = fila.marcado,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = coloresCampoForm(),
                modifier = Modifier.width(120.dp),
            )
        }
        if (fila.base.descripcion.isNotBlank()) {
            Text(
                fila.base.descripcion, color = c.textoSuave, fontSize = Sania.txt.mini,
                modifier = Modifier.padding(start = 48.dp, top = Sania.dim.xs),
            )
        }
    }
}

@Composable
private fun PastillaCategoria(texto: String) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.purpleBg)
            .padding(horizontal = Sania.dim.sm, vertical = 2.dp),
    ) { Text(texto, color = c.purple, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CasillaExtra(
    marcado: Boolean,
    onMarcar: (Boolean) -> Unit,
    titulo: String,
    detalle: String?,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onMarcar(!marcado) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = marcado,
            onCheckedChange = onMarcar,
            colors = CheckboxDefaults.colors(checkedColor = c.navy),
        )
        Column(Modifier.weight(1f).padding(vertical = Sania.dim.xs)) {
            Text(titulo, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
            detalle?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = c.textoSuave, fontSize = Sania.txt.mini)
            }
        }
    }
}
