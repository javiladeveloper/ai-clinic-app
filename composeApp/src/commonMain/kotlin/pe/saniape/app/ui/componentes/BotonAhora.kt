package pe.saniape.app.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AhoraEnZona
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.ahoraEn
import pe.saniape.app.data.staff.zonaActivaId
import pe.saniape.app.data.staff.zonaDeSede
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * «AHORA» JUNTO A LOS CAMPOS DE FECHA Y HORA (Jonathan 07/11/2026; gemelo de
 * components/ui/BotonAhora.tsx de la web, spec
 * docs/superpowers/specs/2026-11-07-boton-ahora-design.md).
 *
 * Un toque pone la fecha y/o la hora de este momento EN LA ZONA DE LA SEDE
 * ([ahoraEn]), nunca la del teléfono ni UTC: una sede en La Paz va una hora
 * adelante de Lima. Solo en campos donde «ahora» puede ser la respuesta (lo
 * que se registra o una cita para atender ya); nunca en nacimientos, horarios
 * de la clínica, filtros ni vencimientos.
 *
 * - [ModoAhora.FECHA_HORA] (par Fecha + Hora): «Ahora», pone las dos.
 * - [ModoAhora.HORA]: «Ahora», solo la hora.
 * - [ModoAhora.FECHA]: «Hoy», solo la fecha.
 *
 * [onAhora] debe llamar al MISMO código que el selector al elegir, para que
 * corran sus validaciones (conflictos, horario del profesional…). Un solo
 * toque, sin abrir el diálogo.
 */
enum class ModoAhora { FECHA, HORA, FECHA_HORA }

/**
 * Zona de la sede de un registro/cita ([sedeId]) según el contexto del staff
 * (sin sede = la principal). Sin contexto cargado → null = la de la sede activa.
 */
fun zonaParaAhora(sedeId: String?): String? = StaffContextoRepo.actual?.zonaDeSede(sedeId)

@Composable
fun BotonAhora(
    modo: ModoAhora = ModoAhora.FECHA_HORA,
    /** Zona IANA de la sede de ESE registro/cita; null o vacía = la de la sede activa. */
    zona: String? = null,
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
    onAhora: (AhoraEnZona) -> Unit,
) {
    val c = Sania.colors
    val texto = if (modo == ModoAhora.FECHA) "Hoy" else "Ahora"
    val que = when (modo) {
        ModoAhora.FECHA -> "la fecha de hoy"
        ModoAhora.HORA -> "la hora de ahora"
        ModoAhora.FECHA_HORA -> "la fecha y la hora de ahora"
    }
    val forma = RoundedCornerShape(20.dp)
    // Alto ≈ el de la etiqueta del campo (10–11 sp + su margen): al ir a su lado
    // no empuja la caja del selector (Fecha y Hora quedan alineadas).
    Box(
        modifier
            .alpha(if (habilitado) 1f else 0.4f)
            .clip(forma)
            .background(c.superficie)
            .border(1.dp, c.lav, forma)
            .tocable(habilitado) { onAhora(ahoraEn(zona?.takeIf { it.isNotBlank() } ?: zonaActivaId())) }
            .semantics {
                role = Role.Button
                contentDescription = "Poner $que"
            }
            .padding(horizontal = 9.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = c.navy, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/**
 * La etiqueta del campo con «Ahora»/«Hoy» a la derecha (la etiqueta conserva
 * su estilo y su margen de siempre). Gemelo de `EtiquetaConAhora` de la web.
 */
@Composable
fun EtiquetaConAhora(
    modo: ModoAhora = ModoAhora.FECHA_HORA,
    zona: String? = null,
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
    onAhora: (AhoraEnZona) -> Unit,
    etiqueta: @Composable () -> Unit,
) {
    // Alineados por la línea de texto (como `items-baseline` de la web): sirve
    // igual para etiquetas con margen arriba o abajo.
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.weight(1f).alignByBaseline()) { etiqueta() }
        BotonAhora(modo = modo, zona = zona, habilitado = habilitado, onAhora = onAhora, modifier = Modifier.alignByBaseline())
    }
}
