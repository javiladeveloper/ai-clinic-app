package pe.saniape.app.ui.clinica.atencion

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.AJUSTES_AV
import pe.saniape.app.data.staff.ANEXOS
import pe.saniape.app.data.staff.AV_BAJA
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.BIOMICROSCOPIA
import pe.saniape.app.data.staff.CUADRANTES
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.ExamenOftalmo
import pe.saniape.app.data.staff.FONDO_OJO
import pe.saniape.app.data.staff.METODOS_TONOMETRIA
import pe.saniape.app.data.staff.NORMAL_BIO
import pe.saniape.app.data.staff.NORMAL_FONDO
import pe.saniape.app.data.staff.OjosNum
import pe.saniape.app.data.staff.OjosTxt
import pe.saniape.app.data.staff.RANGOS
import pe.saniape.app.data.staff.Rango
import pe.saniape.app.data.staff.Refraccion
import pe.saniape.app.data.staff.RefraccionAv
import pe.saniape.app.data.staff.SNELLEN
import pe.saniape.app.data.staff.avisosExamenOftalmo
import pe.saniape.app.data.staff.con
import pe.saniape.app.data.staff.copiarASubjetiva
import pe.saniape.app.data.staff.de
import pe.saniape.app.data.staff.mostrarNum
import pe.saniape.app.data.staff.normalEnAnexos
import pe.saniape.app.data.staff.normalEnMapa
import pe.saniape.app.data.staff.normalizarAV
import pe.saniape.app.data.staff.partesAV
import pe.saniape.app.data.staff.pasoSiguiente
import pe.saniape.app.data.staff.recetaDeLentes
import pe.saniape.app.data.staff.valorEnRango
import pe.saniape.app.ui.AccionesNativas
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

// ─────────────────────────────────────────────────────────────────────────────
// 👁 EXAMEN OFTALMOLÓGICO (2026-10-30, Dr. Santiago) — gemelo de
// components/atencion-medica/ExamenOftalmologico.tsx, armado para el teléfono:
// los 7 bloques plegables, todo por ojo (OD / OI); los números con ▲▼ en su
// paso y su rango (Oftalmologia.kt); lo escrito a mano se lleva al paso al salir
// del campo. Sin listas perezosas ni medidas intrínsecas (ver la regla de la app).
// ─────────────────────────────────────────────────────────────────────────────

private val OJOS = listOf("od" to "OD", "oi" to "OI")

@Composable
internal fun ExamenOftalmologico(valor: ExamenOftalmo, onChange: (ExamenOftalmo) -> Unit, soloLectura: Boolean) {
    val c = Sania.colors
    val e = valor
    val hab = !soloLectura
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("👁 Examen oftalmológico", color = c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        BloqueOft("1. Agudeza visual", abiertoInicial = true) {
            for ((dist, sc, cc) in listOf(Triple("Lejos", e.av.lejosSc, e.av.lejosCc), Triple("Cerca", e.av.cercaSc, e.av.cercaCc))) {
                Text(dist, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                for ((ojo, O) in OJOS) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EtqOjo(O)
                        Column(Modifier.weight(1f)) {
                            MiniEtq("Sin corrección")
                            SelectorAV(sc.de(ojo), hab, "AV $dist $O sin corrección") { s ->
                                onChange(if (dist == "Lejos") e.copy(av = e.av.copy(lejosSc = e.av.lejosSc.con(ojo, s))) else e.copy(av = e.av.copy(cercaSc = e.av.cercaSc.con(ojo, s))))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            MiniEtq("Con corrección")
                            SelectorAV(cc.de(ojo), hab, "AV $dist $O con corrección") { s ->
                                onChange(if (dist == "Lejos") e.copy(av = e.av.copy(lejosCc = e.av.lejosCc.con(ojo, s))) else e.copy(av = e.av.copy(cercaCc = e.av.cercaCc.con(ojo, s))))
                            }
                        }
                    }
                }
            }
            Text("Agujero estenopeico", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((ojo, O) in OJOS) Column(Modifier.weight(1f)) {
                    MiniEtq(O)
                    SelectorAV(e.av.ae.de(ojo), hab, "AE $O") { s -> onChange(e.copy(av = e.av.copy(ae = e.av.ae.con(ojo, s)))) }
                }
            }
        }

        BloqueOft("2. Presión intraocular", abiertoInicial = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((ojo, O) in OJOS) Column(Modifier.weight(1f)) {
                    MiniEtq("PIO $O (mmHg)")
                    NumOft(if (ojo == "od") e.pio.od else e.pio.oi, RANGOS.pio, hab, "PIO $O") { n ->
                        onChange(e.copy(pio = if (ojo == "od") e.pio.copy(od = n) else e.pio.copy(oi = n)))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1.4f)) {
                    MiniEtq("Método")
                    SelectorTexto(e.pio.metodo, METODOS_TONOMETRIA, hab, "Método de tonometría") { m -> onChange(e.copy(pio = e.pio.copy(metodo = m))) }
                }
                Column(Modifier.weight(1f)) {
                    MiniEtq("Hora (HH:MM)")
                    TextoCorto(e.pio.hora, hab, "Hora de la PIO", "10:30", KeyboardType.Text) { h -> onChange(e.copy(pio = e.pio.copy(hora = h))) }
                }
            }
            Ayuda("Rango: 0 a 70 mmHg.")
        }

        BloqueOft("3. Refracción", abiertoInicial = true) {
            Subtitulo("Lensometría")
            TablaRefraccion("Lensometría", e.lenso.od, e.lenso.oi, hab) { ojo, r ->
                onChange(e.copy(lenso = if (ojo == "od") e.lenso.copy(od = r) else e.lenso.copy(oi = r)))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniEtq("ADD cerca")
                Box(Modifier.width(150.dp)) {
                    NumOft(e.lenso.addCerca, RANGOS.add, hab, "Lensometría ADD cerca", signo = true) { n -> onChange(e.copy(lenso = e.lenso.copy(addCerca = n))) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Subtitulo("Autorrefractómetro")
            TablaRefraccion("Autorrefractómetro", e.autorref.od, e.autorref.oi, hab) { ojo, r ->
                onChange(e.copy(autorref = if (ojo == "od") e.autorref.copy(od = r) else e.autorref.copy(oi = r)))
            }
            Spacer(Modifier.height(4.dp))
            Subtitulo("Refracción subjetiva (va a la receta de lentes)")
            if (hab) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnlaceOft("⇣ Copiar del autorrefractómetro") { onChange(copiarASubjetiva(e, "autorref")) }
                    EnlaceOft("⇣ Copiar de la lensometría") { onChange(copiarASubjetiva(e, "lenso")) }
                }
            }
            val s = e.subjetiva
            TablaRefraccion("Subjetiva", s.od.ref, s.oi.ref, hab) { ojo, r ->
                val prev = if (ojo == "od") s.od else s.oi
                val nuevo = RefraccionAv(r.esf, r.cil, r.eje, prev.av)
                onChange(e.copy(subjetiva = if (ojo == "od") s.copy(od = nuevo) else s.copy(oi = nuevo)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((ojo, O) in OJOS) Column(Modifier.weight(1f)) {
                    MiniEtq("AV c/Rx $O")
                    SelectorAV((if (ojo == "od") s.od else s.oi).av, hab, "Subjetiva $O AV") { v ->
                        onChange(e.copy(subjetiva = if (ojo == "od") s.copy(od = s.od.copy(av = v)) else s.copy(oi = s.oi.copy(av = v))))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    MiniEtq("ADD intermedia")
                    NumOft(s.addIntermedia, RANGOS.add, hab, "ADD intermedia", signo = true) { n -> onChange(e.copy(subjetiva = s.copy(addIntermedia = n))) }
                }
                Column(Modifier.weight(1f)) {
                    MiniEtq("ADD cerca")
                    NumOft(s.addCerca, RANGOS.add, hab, "ADD cerca", signo = true) { n -> onChange(e.copy(subjetiva = s.copy(addCerca = n))) }
                }
            }
            MiniEtq("DIP lejos / cerca (mm)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) { NumOft(s.dipLejos, RANGOS.dip, hab, "DIP lejos") { n -> onChange(e.copy(subjetiva = s.copy(dipLejos = n))) } }
                Text("/", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Box(Modifier.weight(1f)) { NumOft(s.dipCerca, RANGOS.dip, hab, "DIP cerca") { n -> onChange(e.copy(subjetiva = s.copy(dipCerca = n))) } }
            }
            MiniEtq("Indicaciones y comentarios (salen en la receta)")
            TextoLargo(e.indicaciones, hab, "Indicaciones de la receta", "Ej. Lentes de uso permanente, antirreflejo, control en 1 año…", 3) { t ->
                onChange(e.copy(indicaciones = t))
            }
            Ayuda("Esfera −25.00 a +25.00 · cilindro −10.00 a 0.00 (de 0.25 en 0.25) · eje 0° a 180° de 5° en 5° · ADD +0.25 a +4.00.")
        }

        BloqueOft(
            "4. Órbita y anexos", abiertoInicial = algo(e.anexos.orbita, e.anexos.lagrimal) || e.anexos.but.od != null || e.anexos.but.oi != null ||
                e.anexos.schirmer.od != null || e.anexos.schirmer.oi != null,
            normal = if (hab) { ojo -> onChange(e.copy(anexos = normalEnAnexos(e.anexos, ojo))) } else null,
        ) {
            for ((k, t) in ANEXOS) {
                val o = if (k == "orbita") e.anexos.orbita else e.anexos.lagrimal
                FilaTextos(t, o, hab) { nuevo -> onChange(e.copy(anexos = if (k == "orbita") e.anexos.copy(orbita = nuevo) else e.anexos.copy(lagrimal = nuevo))) }
            }
            for ((k, t, r) in listOf(Triple("but", "BUT (seg)", RANGOS.but), Triple("schirmer", "Test de Schirmer (mm)", RANGOS.schirmer))) {
                val o: OjosNum = if (k == "but") e.anexos.but else e.anexos.schirmer
                Text(t, color = c.texto, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((ojo, O) in OJOS) Column(Modifier.weight(1f)) {
                        MiniEtq(O)
                        NumOft(o.de(ojo), r, hab, "$t $O") { n ->
                            val nuevo = o.con(ojo, n)
                            onChange(e.copy(anexos = if (k == "but") e.anexos.copy(but = nuevo) else e.anexos.copy(schirmer = nuevo)))
                        }
                    }
                }
            }
        }

        BloqueOft("5. Biomicroscopía", abiertoInicial = e.bio.values.any { algo(it) },
            normal = if (hab) { ojo -> onChange(e.copy(bio = normalEnMapa(e.bio, NORMAL_BIO, ojo))) } else null) {
            for ((k, t) in BIOMICROSCOPIA) FilaTextos(t, e.bio[k] ?: OjosTxt(), hab) { nuevo -> onChange(e.copy(bio = e.bio + (k to nuevo))) }
        }

        BloqueOft("6. Fondo de ojo", abiertoInicial = e.fondo.values.any { algo(it) },
            normal = if (hab) { ojo -> onChange(e.copy(fondo = normalEnMapa(e.fondo, NORMAL_FONDO, ojo))) } else null) {
            for ((k, t) in FONDO_OJO) FilaTextos(t, e.fondo[k] ?: OjosTxt(), hab) { nuevo -> onChange(e.copy(fondo = e.fondo + (k to nuevo))) }
        }

        val g = e.gonio
        BloqueOft("7. Gonioscopía", abiertoInicial = CUADRANTES.any { g.grado.od[it] != null || g.grado.oi[it] != null } ||
            g.hallazgos.values.any { algo(it) } || !g.conclusion.isNullOrBlank() || !g.adicional.isNullOrBlank()) {
            Ayuda("Grado de apertura angular: 0, 1, 2, 3 o 4 en cada cuadrante de cada ojo.")
            for ((ojo, O) in OJOS) {
                val grado = if (ojo == "od") g.grado.od else g.grado.oi
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(O, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    for (cu in CUADRANTES) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cu.replaceFirstChar { it.uppercase() }, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(78.dp))
                            SelectorGrado(grado[cu], hab, "$O $cu") { n ->
                                val nuevo = grado.con(cu, n)
                                onChange(e.copy(gonio = g.copy(grado = if (ojo == "od") g.grado.copy(od = nuevo) else g.grado.copy(oi = nuevo))))
                            }
                        }
                    }
                }
            }
            for (cu in CUADRANTES) {
                FilaTextos("Hallazgos ${cu}", g.hallazgos[cu] ?: OjosTxt(), hab) { nuevo -> onChange(e.copy(gonio = g.copy(hallazgos = g.hallazgos + (cu to nuevo)))) }
            }
            MiniEtq("Conclusión de gonioscopía")
            TextoLargo(g.conclusion, hab, "Conclusión de gonioscopía", null, 2) { t -> onChange(e.copy(gonio = g.copy(conclusion = t))) }
            MiniEtq("Información adicional")
            TextoLargo(g.adicional, hab, "Información adicional", null, 2) { t -> onChange(e.copy(gonio = g.copy(adicional = t))) }
        }

        val avisos = avisosExamenOftalmo(e)
        if (avisos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (a in avisos) Text("⚠ $a", color = c.pend, fontSize = 12.sp)
            }
        }
    }
}

/**
 * «👓 Receta de lentes» (lo único que se imprime del examen): guarda antes y abre
 * la hoja del servidor (/api/staff/imprimir/lentes/{citaId}), como la web.
 * Solo aparece si la refracción subjetiva tiene algo que imprimir.
 */
@Composable
internal fun BotonRecetaLentes(vm: AtencionViewModel, d: DatosConsultaApp, examen: ExamenOftalmo, acciones: AccionesNativas) {
    if (recetaDeLentes(examen) == null) return
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var abriendo by remember { mutableStateOf(false) }
    BotonChico("👓 Receta de lentes", fg = c.navy, bg = c.superficie, borde = c.navy, habilitado = !abriendo) {
        if (abriendo) return@BotonChico
        abriendo = true
        scope.launch {
            try {
                if (!vm.guardarAntes()) return@launch
                val html = AtencionRepo.htmlImprimible("lentes", d.cita.id)
                if (html != null) acciones.abrirHtml(html, "Receta de lentes")
                else Toaster.error("No se pudo abrir la receta de lentes. Revisa tu conexión.")
            } finally {
                abriendo = false
            }
        }
    }
}

// ── Piezas ───────────────────────────────────────────────────────────────────

private fun algo(vararg o: OjosTxt) = o.any { !it.od.isNullOrBlank() || !it.oi.isNullOrBlank() }

/** Un bloque plegable; [normal] = botones «✓ OD normal» / «✓ OI normal». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloqueOft(titulo: String, abiertoInicial: Boolean, normal: ((String) -> Unit)? = null, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(abiertoInicial) }
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(Modifier.fillMaxWidth().clip(forma).border(1.dp, c.borde, forma).background(c.superficie)) {
        Row(
            Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(titulo, color = c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (abierto) "▴" else "▾", color = c.textoSuave, fontSize = 14.sp)
        }
        if (normal != null) {
            FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = if (abierto) 0.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((ojo, O) in OJOS) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).border(1.dp, c.ok, RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { normal(ojo); abierto = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("✓ $O normal", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        if (abierto) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
        }
    }
}

@Composable private fun MiniEtq(t: String) = Text(t, color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
@Composable private fun Ayuda(t: String) = Text(t, color = Sania.colors.textoSuave, fontSize = 11.sp)
@Composable private fun Subtitulo(t: String) = Text(t, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)

@Composable
private fun EtqOjo(o: String) {
    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
        Text(o, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnlaceOft(t: String, onClick: () -> Unit) {
    Text(t, color = Sania.colors.navy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable(onClick = onClick).padding(vertical = 4.dp))
}

/** Esfera / cilindro / eje por ojo: una fila por ojo, tres números con ▲▼. */
@Composable
private fun TablaRefraccion(nombre: String, od: Refraccion, oi: Refraccion, hab: Boolean, onCambio: (String, Refraccion) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.width(30.dp))
        for (t in listOf("Esfera (D)", "Cilindro (D)", "Eje (°)")) Box(Modifier.weight(1f)) { MiniEtq(t) }
    }
    for ((ojo, O) in OJOS) {
        val r = if (ojo == "od") od else oi
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EtqOjo(O)
            Box(Modifier.weight(1f)) { NumOft(r.esf, RANGOS.esf, hab, "$nombre $O esfera", signo = true) { n -> onCambio(ojo, r.copy(esf = n)) } }
            Box(Modifier.weight(1f)) { NumOft(r.cil, RANGOS.cil, hab, "$nombre $O cilindro", signo = true) { n -> onCambio(ojo, r.copy(cil = n)) } }
            Box(Modifier.weight(1f)) { NumOft(r.eje, RANGOS.eje, hab, "$nombre $O eje") { n -> onCambio(ojo, r.copy(eje = n)) } }
        }
    }
}

/**
 * Número con ▲▼ en su paso. Se escribe libre (coma o punto, "+2,00"): lo que ya
 * vale se manda al momento; al salir del campo se muestra ya llevado al paso (y
 * lo que no vale se borra).
 */
@Composable
private fun NumOft(v: Double?, r: Rango, hab: Boolean, aria: String, signo: Boolean = false, onChange: (Double?) -> Unit) {
    val c = Sania.colors
    var txt by remember { mutableStateOf(mostrarNum(v, r, signo)) }
    var enfocado by remember { mutableStateOf(false) }
    LaunchedEffect(v) { if (!enfocado) txt = mostrarNum(v, r, signo) }
    fun fijar() {
        val n = valorEnRango(txt, r)
        onChange(n)
        txt = mostrarNum(n, r, signo)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = txt,
            onValueChange = { t ->
                txt = t
                if (t.isBlank()) onChange(null) else valorEnRango(t, r)?.let(onChange)
            },
            enabled = hab,
            singleLine = true,
            colors = coloresCampoForm(legibleDeshabilitado = true),
            textStyle = TextStyle(fontSize = 14.sp, color = c.texto, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { fijar() }),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = aria }
                .onFocusChanged { f -> if (enfocado && !f.isFocused) fijar(); enfocado = f.isFocused },
        )
        if (hab) {
            Column(Modifier.padding(start = 2.dp)) {
                FlechaOft("▲", "$aria: subir") { val n = pasoSiguiente(v, r, 1); onChange(n); txt = mostrarNum(n, r, signo) }
                FlechaOft("▼", "$aria: bajar") { val n = pasoSiguiente(v, r, -1); onChange(n); txt = mostrarNum(n, r, signo) }
            }
        }
    }
}

@Composable
private fun FlechaOft(t: String, aria: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.size(width = 30.dp, height = 24.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
            .clickable(onClick = onClick).semantics { contentDescription = aria },
        contentAlignment = Alignment.Center,
    ) { Text(t, color = c.navy, fontSize = 10.sp) }
}

/** Agudeza visual: Snellen 20/x (+ letras de más/menos) o CD, MM, PL, NPL. */
@Composable
private fun SelectorAV(v: String?, hab: Boolean, aria: String, onChange: (String?) -> Unit) {
    val (base, ajuste) = partesAV(v)
    val snellen = base.startsWith("20/")
    val opciones = listOf("" to "—") + SNELLEN.map { it to it } + AV_BAJA
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1f)) {
            Desplegable(texto = base.ifEmpty { "—" }, opciones = opciones, hab = hab, aria = aria) { k ->
                onChange(normalizarAV(if (k.isEmpty()) "" else k + if (k.startsWith("20/")) ajuste else ""))
            }
        }
        if (snellen) {
            Box(Modifier.widthIn(min = 52.dp)) {
                Desplegable(texto = ajuste.ifEmpty { "±" }, opciones = listOf("" to "±") + AJUSTES_AV.map { it to it }, hab = hab, aria = "$aria: letras") { a ->
                    onChange(normalizarAV(base + a))
                }
            }
        }
    }
}

/** Un valor de una lista sugerida (o "—" para dejarlo vacío). */
@Composable
private fun SelectorTexto(v: String?, opciones: List<String>, hab: Boolean, aria: String, onChange: (String?) -> Unit) {
    Desplegable(texto = v ?: "—", opciones = listOf("" to "—") + opciones.map { it to it } +
        (if (!v.isNullOrBlank() && v !in opciones) listOf(v to v) else emptyList()), hab = hab, aria = aria) { k ->
        onChange(k.ifEmpty { null })
    }
}

@Composable
private fun Desplegable(texto: String, opciones: List<Pair<String, String>>, hab: Boolean, aria: String, onElegir: (String) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(forma).border(1.dp, c.borde, forma).background(c.superficie)
                .clickable(enabled = hab) { abierto = true }.padding(horizontal = 10.dp, vertical = 10.dp)
                .semantics { contentDescription = aria },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(texto, color = c.texto, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (hab) Text("▾", color = c.navy, fontSize = 12.sp)
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            for ((k, t) in opciones) {
                DropdownMenuItem(text = { Text(t, fontSize = 14.sp) }, onClick = { abierto = false; onElegir(k) })
            }
        }
    }
}

/** Grado 0–4 de un cuadrante; tocar el elegido lo borra. */
@Composable
private fun SelectorGrado(v: Int?, hab: Boolean, aria: String, onChange: (Int?) -> Unit) {
    val c = Sania.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { contentDescription = aria }) {
        for (n in 0..4) {
            val sel = v == n
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (sel) c.navy else c.superficie)
                    .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = hab) { onChange(if (sel) null else n) },
                contentAlignment = Alignment.Center,
            ) { Text("$n", color = if (sel) c.sobreNavy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Una exploración por ojo: OD y OI, uno al lado del otro. */
@Composable
private fun FilaTextos(titulo: String, o: OjosTxt, hab: Boolean, onChange: (OjosTxt) -> Unit) {
    Text(titulo, color = Sania.colors.texto, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((ojo, O) in OJOS) Box(Modifier.weight(1f)) {
            TextoLargo(o.de(ojo), hab, "$titulo $O", O, 1) { t -> onChange(o.con(ojo, t)) }
        }
    }
}

@Composable
private fun TextoLargo(v: String?, hab: Boolean, aria: String, placeholder: String?, minLineas: Int, onChange: (String?) -> Unit) {
    val c = Sania.colors
    OutlinedTextField(
        value = v.orEmpty(),
        onValueChange = { onChange(it.ifEmpty { null }) },
        enabled = hab,
        minLines = minLineas,
        colors = coloresCampoForm(legibleDeshabilitado = true),
        placeholder = placeholder?.let { p -> { Text(p, color = c.textoSuave, fontSize = 13.sp) } },
        textStyle = TextStyle(fontSize = 14.sp, color = c.texto),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = aria },
    )
}

@Composable
private fun TextoCorto(v: String?, hab: Boolean, aria: String, placeholder: String, teclado: KeyboardType, onChange: (String?) -> Unit) {
    val c = Sania.colors
    OutlinedTextField(
        value = v.orEmpty(),
        onValueChange = { onChange(it.take(5).ifEmpty { null }) },
        enabled = hab,
        singleLine = true,
        colors = coloresCampoForm(legibleDeshabilitado = true),
        placeholder = { Text(placeholder, color = c.textoSuave, fontSize = 13.sp) },
        textStyle = TextStyle(fontSize = 14.sp, color = c.texto),
        keyboardOptions = KeyboardOptions(keyboardType = teclado),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = aria },
    )
}
