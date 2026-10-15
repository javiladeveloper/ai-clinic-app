package pe.saniape.app.ui.clinica.fisio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CargaRutinas
import pe.saniape.app.data.staff.LimitesRutina
import pe.saniape.app.data.staff.PasoGuardarRutina
import pe.saniape.app.data.staff.PlanEjercicios
import pe.saniape.app.data.staff.RutinaEjercicios
import pe.saniape.app.data.staff.RutinasEjerciciosRepo
import pe.saniape.app.data.staff.buscarRutinas
import pe.saniape.app.data.staff.noDisponibles
import pe.saniape.app.data.staff.nombreSugeridoRutina
import pe.saniape.app.data.staff.pasoGuardarRutina
import pe.saniape.app.data.staff.resumenRutina
import pe.saniape.app.data.staff.rutinaConNombre
import pe.saniape.app.data.staff.textoBotonGuardarRutina
import pe.saniape.app.data.staff.textoItemRutina
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * RUTINAS DE EJERCICIOS — "📋 Usar rutina" y "💾 Guardar como rutina". Gemelo de
 * `components/fisio/RutinasEjerciciosModal.tsx`.
 *
 *  - [RutinasEjerciciosModal]: el buscador. Arriba las de la clínica, abajo las base
 *    de Sania (las manda la web: la app no las copia). Cada una se despliega para
 *    ver sus ejercicios con la dosis; "Usar" la aplica al destino de la pestaña.
 *  - [GuardarRutinaModal]: guarda el plan como rutina de la clínica. Mismo nombre =
 *    la reemplaza: se avisa y se pide confirmarlo antes.
 *
 * Todo dentro de DialogoForm (Column con scroll): sin listas perezosas anidadas.
 */

@Composable
internal fun RutinasEjerciciosModal(
    /** Para cuándo se aplica: "Sesión #8 · 06/06/26 · Terapia lumbar". */
    para: String?,
    puedeEditar: Boolean,
    onCerrar: () -> Unit,
    /** Aplica la rutina; true si salió bien (quien la monta cierra el modal). */
    onUsar: suspend (RutinaEjercicios) -> Boolean,
    /** "← Elegir de la biblioteca": vuelve al buscador de ejercicios sueltos. */
    onVolverBiblioteca: (() -> Unit)? = null,
    /** El servidor no tiene la función (web vieja / clínica sin fisio): ocultar los botones. */
    onNoDisponible: () -> Unit = {},
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var clinica by remember { mutableStateOf<CargaRutinas?>(null) }
    var base by remember { mutableStateOf<CargaRutinas?>(null) }
    var token by remember { mutableStateOf(0) }
    LaunchedEffect(token) {
        coroutineScope {
            val a = async { RutinasEjerciciosRepo.deClinica() }
            val b = async { RutinasEjerciciosRepo.base() }
            clinica = a.await()
            base = b.await()
        }
    }
    val nada = clinica is CargaRutinas.NoDisponible && base is CargaRutinas.NoDisponible
    LaunchedEffect(nada) { if (nada) onNoDisponible() }

    var q by remember { mutableStateOf("") }
    var abierta by remember { mutableStateOf<String?>(null) }
    var usando by remember { mutableStateOf<String?>(null) }
    var confirmarBorrar by remember { mutableStateOf<String?>(null) }
    var borrando by remember { mutableStateOf(false) }

    val todas = (clinica as? CargaRutinas.Ok)?.rutinas.orEmpty() + (base as? CargaRutinas.Ok)?.rutinas.orEmpty()
    val resultado = remember(todas, q) { buscarRutinas(todas, q) }
    val propias = resultado.filter { it.esDeClinica }
    val deSania = resultado.filter { !it.esDeClinica }
    val elegida = todas.firstOrNull { it.clave == abierta }

    // Anti doble toque: el primer toque marca `usando` antes de lanzar; el segundo ya lo ve.
    fun usar(r: RutinaEjercicios) {
        if (usando != null || r.ejercicios.isEmpty()) return
        usando = r.clave
        scope.launch {
            try { onUsar(r) } finally { usando = null }
        }
    }

    fun borrar(r: RutinaEjercicios) {
        if (borrando) return
        borrando = true
        scope.launch {
            val res = RutinasEjerciciosRepo.borrar(r.id)
            borrando = false
            confirmarBorrar = null
            if (!res.registrada) { Toaster.error(res.rechazo?.error ?: "No se pudo eliminar la rutina"); return@launch }
            Toaster.exito("Rutina eliminada")
            if (abierta == r.clave) abierta = null
            token++
        }
    }

    DialogoForm(
        titulo = "📋 Usar una rutina",
        subtitulo = para?.let { "Para: $it" },
        textoAccion = when {
            usando != null -> "Aplicando…"
            elegida != null -> "Usar «${elegida.nombre.take(28)}${if (elegida.nombre.length > 28) "…" else ""}»"
            else -> "Usar rutina"
        },
        accionHabilitada = elegida != null && elegida.ejercicios.isNotEmpty() && usando == null,
        textoCancelar = "Cerrar",
        onCancelar = { if (usando == null) onCerrar() },
        onAccion = { elegida?.let { usar(it) } },
    ) {
        if (onVolverBiblioteca != null) {
            Text("← Elegir de la biblioteca", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = usando == null) { onVolverBiblioteca() }.padding(vertical = 6.dp))
            Spacer(Modifier.height(4.dp))
        }
        Text("Entran con la dosis de la rutina; después ajustas cada ejercicio a este paciente.",
            color = c.textoSuave, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        if (nada) {
            Text(
                "Las rutinas todavía no están disponibles para tu clínica. Mientras tanto, elige los ejercicios de la biblioteca.",
                color = c.texto, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            return@DialogoForm
        }

        // Sin autofoco: el teclado taparía las rutinas, que suelen bastar a la vista.
        OutlinedTextField(
            colors = coloresCampoForm(), value = q, onValueChange = { q = it.take(80) },
            placeholder = { Text("Busca por malestar o ejercicio: lumbalgia, hombro, tobillo…", color = c.textoSuave, fontSize = 13.sp) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        EtqForm("De tu clínica")
        when (val cl = clinica) {
            null -> Esqueleto()
            is CargaRutinas.Error -> AvisoRutinas("No se pudieron cargar las rutinas de tu clínica. Las rutinas base sí están.") { token++ }
            is CargaRutinas.NoDisponible -> Text("Aún no disponible para tu clínica.", color = c.textoSuave, fontSize = 12.sp)
            is CargaRutinas.Ok -> if (propias.isEmpty()) Text(
                if (q.isNotBlank()) "Ninguna rutina de tu clínica con «${q.trim()}»."
                else "Aún no hay. Desde el plan de un paciente: 💾 Guardar como rutina.",
                color = c.textoSuave, fontSize = 12.sp,
            ) else ListaRutinas(propias, abierta, usando, puedeBorrar = puedeEditar, confirmarBorrar, borrando,
                onAbrir = { abierta = if (abierta == it.clave) null else it.clave }, onUsar = ::usar,
                onPedirBorrar = { confirmarBorrar = it?.clave }, onBorrar = ::borrar)
        }

        Spacer(Modifier.height(16.dp))
        EtqForm("Rutinas base de Sania")
        when (base) {
            null -> Esqueleto()
            is CargaRutinas.Error -> AvisoRutinas("No se pudieron cargar las rutinas base.") { token++ }
            is CargaRutinas.NoDisponible -> Text("Aún no disponibles.", color = c.textoSuave, fontSize = 12.sp)
            is CargaRutinas.Ok -> if (deSania.isEmpty()) Text("Ninguna rutina base con «${q.trim()}».", color = c.textoSuave, fontSize = 12.sp)
            else ListaRutinas(deSania, abierta, usando, puedeBorrar = false, null, false,
                onAbrir = { abierta = if (abierta == it.clave) null else it.clave }, onUsar = ::usar, onPedirBorrar = {}, onBorrar = {})
        }
        Text("Armadas solo con ejercicios revisados por un fisioterapeuta. Son un punto de partida: ajústalas a cada paciente.",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Esqueleto() {
    val c = Sania.colors
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg))
}

@Composable
private fun AvisoRutinas(texto: String, onReintentar: () -> Unit) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(texto, color = c.error, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("↻ Reintentar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onReintentar).padding(6.dp))
    }
}

@Composable
private fun ListaRutinas(
    rutinas: List<RutinaEjercicios>, abierta: String?, usando: String?, puedeBorrar: Boolean,
    confirmarBorrar: String?, borrando: Boolean,
    onAbrir: (RutinaEjercicios) -> Unit, onUsar: (RutinaEjercicios) -> Unit,
    onPedirBorrar: (RutinaEjercicios?) -> Unit, onBorrar: (RutinaEjercicios) -> Unit,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rutinas.forEach { r ->
            TarjetaRutina(
                r, abierta = abierta == r.clave, usando = usando == r.clave, bloqueada = usando != null,
                puedeBorrar = puedeBorrar && r.esDeClinica, confirmando = confirmarBorrar == r.clave, borrando = borrando,
                onAbrir = { onAbrir(r) }, onUsar = { onUsar(r) }, onPedirBorrar = { onPedirBorrar(if (it) r else null) },
                onBorrar = { onBorrar(r) },
            )
        }
    }
}

@Composable
private fun TarjetaRutina(
    r: RutinaEjercicios, abierta: Boolean, usando: Boolean, bloqueada: Boolean, puedeBorrar: Boolean,
    confirmando: Boolean, borrando: Boolean,
    onAbrir: () -> Unit, onUsar: () -> Unit, onPedirBorrar: (Boolean) -> Unit, onBorrar: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    val faltan = remember(r) { noDisponibles(r) }
    Column(Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.5.dp, if (abierta) c.navy else c.borde, forma)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).clickable(onClick = onAbrir)) {
                Text(r.nombre, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                r.descripcion?.let { Text(it, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp)) }
                Text(resumenRutina(r), color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                Text(if (abierta) "Ocultar ▴" else "Ver ejercicios ▾", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(8.dp))
            val activo = !bloqueada && r.ejercicios.isNotEmpty()
            Box(
                Modifier.clip(forma).background(if (activo) c.navy else c.borde).clickable(enabled = activo, onClick = onUsar)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (usando) "Aplicando…" else "Usar", color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        // Lo que ya no está en la biblioteca se omite al usarla: se dice ANTES, a la vista.
        if (faltan.isNotEmpty()) {
            Text("Se omitirá${if (faltan.size == 1) "" else "n"} (ya no está${if (faltan.size == 1) "" else "n"} en la biblioteca): ${faltan.joinToString(", ")}",
                color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp)).background(c.pendBg).padding(horizontal = 8.dp, vertical = 5.dp))
        }
        AnimatedVisibility(abierta) {
            Column {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.ejercicios.forEachIndexed { k, e ->
                        Column {
                            Text("${k + 1}. ${e.nombre}" + if (!e.disponible) " (ya no está)" else "",
                                color = if (e.disponible) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                textoItemRutina(e) + (if (e.lado == "ambos") " · ambos lados" else "") +
                                    (e.dolorMaximo?.let { " · parar si el dolor pasa de $it/10" } ?: ""),
                                color = c.textoSuave, fontSize = 11.sp,
                            )
                        }
                    }
                    if (puedeBorrar) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                        if (!confirmando) {
                            Text("Eliminar rutina", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onPedirBorrar(true) }.padding(vertical = 4.dp))
                        } else {
                            Text("¿Eliminar «${r.nombre}»? Los pacientes que ya la tienen no cambian.", color = c.texto, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(if (borrando) "Eliminando…" else "Eliminar", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable(enabled = !borrando, onClick = onBorrar).padding(vertical = 6.dp))
                                Text("Cancelar", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable(enabled = !borrando) { onPedirBorrar(false) }.padding(vertical = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Guardar el plan como rutina de la clínica ───────────────────────────────

@Composable
internal fun GuardarRutinaModal(plan: PlanEjercicios, onCerrar: () -> Unit, onNoDisponible: () -> Unit = {}) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var rutinas by remember { mutableStateOf<CargaRutinas?>(null) }
    LaunchedEffect(plan.id) { rutinas = RutinasEjerciciosRepo.deClinica() }
    val noDisponible = rutinas is CargaRutinas.NoDisponible
    LaunchedEffect(noDisponible) { if (noDisponible) onNoDisponible() }

    var nombre by remember { mutableStateOf(nombreSugeridoRutina(plan.motivo, plan.titulo)) }
    var descripcion by remember { mutableStateOf("") }
    val tieneTextos = !plan.indicacionesGenerales.isNullOrBlank() || !plan.precauciones.isNullOrBlank()
    var conTextos by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    // Confirmar el reemplazo vale para ESE nombre: si lo cambia, se vuelve a pedir.
    var confirmadoPara by remember { mutableStateOf<String?>(null) }

    val activos = plan.activos.size
    val existente = rutinaConNombre((rutinas as? CargaRutinas.Ok)?.rutinas.orEmpty(), nombre)
    val paso = pasoGuardarRutina(existente != null, confirmadoPara == nombre)
    val habilitado = nombre.isNotBlank() && activos > 0 && !guardando && !noDisponible

    fun guardar() {
        if (!habilitado) return
        if (paso == PasoGuardarRutina.PEDIR_CONFIRMACION) { confirmadoPara = nombre; return }
        guardando = true
        scope.launch {
            val r = RutinasEjerciciosRepo.guardar(
                nombre, descripcion, plan.id,
                if (conTextos) plan.indicacionesGenerales else null,
                if (conTextos) plan.precauciones else null,
            )
            guardando = false
            if (!r.registrada) { Toaster.error(r.rechazo?.error ?: "No se pudo guardar la rutina"); return@launch }
            val actualizada = (r.cuerpo?.get("actualizada") as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            Toaster.exito(if (actualizada) "Rutina actualizada: la encuentras en “📋 Usar rutina”" else "Rutina guardada: la encuentras en “📋 Usar rutina”")
            onCerrar()
        }
    }

    DialogoForm(
        titulo = "💾 Guardar como rutina",
        subtitulo = "${if (activos == 1) "1 ejercicio" else "$activos ejercicios"} con su dosis",
        textoAccion = textoBotonGuardarRutina(paso, guardando),
        accionHabilitada = habilitado,
        onCancelar = { if (!guardando) onCerrar() },
        onAccion = { guardar() },
    ) {
        Text(
            "Guarda ${if (activos == 1) "el ejercicio" else "los $activos ejercicios"} de este plan con su dosis, para dejárselos a otro paciente en un toque. No se guarda nada del paciente.",
            color = c.textoSuave, fontSize = 12.sp,
        )
        if (noDisponible) {
            Text("Las rutinas todavía no están disponibles para tu clínica.", color = c.texto, fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp))
            return@DialogoForm
        }
        Spacer(Modifier.height(12.dp))
        EtqForm("Nombre")
        OutlinedTextField(
            colors = coloresCampoForm(), value = nombre, onValueChange = { nombre = it.take(LimitesRutina.NOMBRE) },
            placeholder = { Text("Ej.: Lumbalgia — fase subaguda", color = c.textoSuave) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        existente?.let { e ->
            Text(
                if (paso == PasoGuardarRutina.REEMPLAZAR) "Toca «Sí, reemplazar» para cambiar los ejercicios de «${e.nombre}» por estos."
                else "Ya tienes una rutina «${e.nombre}»: se reemplazará con estos ejercicios.",
                color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        EtqForm("Para qué es (opcional)")
        OutlinedTextField(
            colors = coloresCampoForm(), value = descripcion, onValueChange = { descripcion = it.take(LimitesRutina.DESCRIPCION) },
            placeholder = { Text("Ej.: dolor lumbar mecánico, sin irradiación", color = c.textoSuave) }, modifier = Modifier.fillMaxWidth(),
        )
        if (tieneTextos) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { conTextos = !conTextos }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(if (conTextos) "☑" else "☐", fontSize = 18.sp, color = if (conTextos) c.navy else c.textoSuave)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Incluir las indicaciones generales y las precauciones", color = c.texto, fontSize = 13.sp)
                    Text("Al usarla, pasan al plan del paciente si el plan no tiene las suyas.", color = c.textoSuave, fontSize = 11.sp)
                }
            }
        }
    }
}
