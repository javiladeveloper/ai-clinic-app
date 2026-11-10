package pe.saniape.app.ui.clinica.agenda.componentes

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AccionSala
import pe.saniape.app.data.staff.BotonFilaSala
import pe.saniape.app.data.staff.EstadoSala
import pe.saniape.app.data.staff.FilaSala
import pe.saniape.app.data.staff.GrupoMenu
import pe.saniape.app.data.staff.GrupoSala
import pe.saniape.app.data.staff.esperaTexto
import pe.saniape.app.data.staff.horaDelInstante
import pe.saniape.app.data.staff.totalEsperando
import pe.saniape.app.ui.componentes.BotonMas
import pe.saniape.app.ui.componentes.BotonTarjeta
import pe.saniape.app.ui.componentes.MenuMas
import pe.saniape.app.ui.theme.Sania

/**
 * 🪑 Franja «Sala de espera» arriba de la agenda de HOY (todas las clínicas,
 * 09/11/2026). Gemela de components/citas/SalaEspera.tsx: un bloque por
 * profesional (con su nombre) con «En consulta», «Sigue → 1°» y «2°…», cada
 * fila con el nombre del paciente (nunca solo un número). Las reglas son de
 * armarSala; acá solo se dibuja.
 *
 * Sin LazyRow ni IntrinsicSize: va como un ítem dentro de la LazyColumn de la
 * agenda. El plegado no se recuerda entre sesiones (resolución 9).
 */
@Composable
fun FranjaSalaEspera(
    grupos: List<GrupoSala>,
    zona: String?,
    /** Citas que se están guardando ("…" en su botón, sin aceptar otro toque). */
    moviendo: Set<String>,
    /** El botón de la fila (▶ Pasar, ✓ Completar, ▶ Atender, 🩺 Triaje…); null = ninguno. */
    botonDe: (FilaSala, EstadoSala) -> BotonFilaSala?,
    /** ¿La fila ofrece «⋯» (Volver a la sala / Llegó por error)? */
    menuDe: (FilaSala) -> Boolean,
    onBoton: (FilaSala, BotonFilaSala) -> Unit,
    onAccionSala: (FilaSala, AccionSala) -> Unit,
) {
    val c = Sania.colors
    var plegada by rememberSaveable { mutableStateOf(false) }
    val esperando = totalEsperando(grupos)
    val enConsulta = grupos.sumOf { it.enConsulta.size }
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm / 2)
            .clip(forma).border(1.dp, c.pend, forma).background(c.superficie),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.pendBg)
                .semantics { role = Role.Button }
                .clickable { plegada = !plegada }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🪑", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Sala de espera · $esperando esperando" + if (enConsulta > 0) " · $enConsulta en consulta" else "",
                color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            Text(if (plegada) "▼" else "▲", color = c.pend, fontSize = 12.sp)
        }
        if (plegada) return@Column
        if (grupos.isEmpty()) {
            Text("Sala vacía", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            return@Column
        }
        grupos.forEachIndexed { gi, g ->
            if (gi > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // El profesional: «Sin profesional» / «Profesional sin nombre» si falta (nunca vacío).
                Text(g.nombre, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                g.enConsulta.forEach { f ->
                    FilaFranja(
                        etiqueta = "En consulta:", colorEtiqueta = c.purple,
                        texto = f.paciente + (horaDelInstante(f.consultaAt, zona)?.let { " · desde $it" } ?: ""),
                        boton = botonDe(f, EstadoSala.EN_CONSULTA), principal = false, moviendo = f.citaId in moviendo,
                        menu = menuDe(f), conVolver = true,
                        onBoton = { onBoton(f, it) }, onAccionSala = { onAccionSala(f, it) },
                    )
                }
                g.esperando.forEachIndexed { i, f ->
                    val espera = esperaTexto(f.minutos)
                    FilaFranja(
                        etiqueta = if (i == 0) "Sigue → 1°" else "${i + 1}°",
                        colorEtiqueta = if (i == 0) c.pend else c.textoSuave,
                        texto = f.paciente + if (espera.isEmpty()) "" else " · $espera",
                        boton = botonDe(f, EstadoSala.ESPERANDO), principal = i == 0, moviendo = f.citaId in moviendo,
                        menu = menuDe(f), conVolver = false,
                        onBoton = { onBoton(f, it) }, onAccionSala = { onAccionSala(f, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaFranja(
    etiqueta: String,
    colorEtiqueta: Color,
    texto: String,
    boton: BotonFilaSala?,
    /** El que sigue: su botón va lleno. */
    principal: Boolean,
    moviendo: Boolean,
    menu: Boolean,
    /** En consulta: «↩ Volver a la sala» además de «Llegó por error». */
    conVolver: Boolean,
    onBoton: (BotonFilaSala) -> Unit,
    onAccionSala: (AccionSala) -> Unit,
) {
    val c = Sania.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etiqueta, color = colorEtiqueta, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(texto, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (boton != null) {
            Spacer(Modifier.width(8.dp))
            BotonTarjeta(
                texto = if (moviendo) "…" else boton.texto,
                color = if (boton == BotonFilaSala.TRIAJE) c.info else c.navy,
                habilitado = !moviendo,
                lleno = principal,
            ) { onBoton(boton) }
        }
        if (menu) {
            Spacer(Modifier.width(6.dp))
            MenuMas(
                grupos = listOf(GrupoMenu("", listOfNotNull(AccionSala.VOLVER.takeIf { conVolver }, AccionSala.DESHACER))),
                texto = { if (it == AccionSala.VOLVER) "↩ Volver a la sala" else "Llegó por error" },
                onAccion = onAccionSala,
                rojo = { it == AccionSala.DESHACER },
                habilitado = !moviendo,
                descripcion = "Más acciones de la sala de espera",
                disparador = { abrir -> BotonMas(habilitado = !moviendo, descripcion = "Más acciones de la sala de espera", texto = "⋯", onClick = abrir) },
            )
        }
    }
}
