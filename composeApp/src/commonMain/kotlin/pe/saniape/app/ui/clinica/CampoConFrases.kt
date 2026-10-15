package pe.saniape.app.ui.clinica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import pe.saniape.app.data.staff.FraseFrecuenteApp
import pe.saniape.app.data.staff.FrasesCampoPlan
import pe.saniape.app.data.staff.FrasesCampoRepo
import pe.saniape.app.data.staff.RespuestaFrasesCampo
import pe.saniape.app.data.staff.frasesDeCampoAbierto
import pe.saniape.app.ui.clinica.atencion.CampoTextoClinico

/**
 * Campo de texto con chips y autocompletado que aprende: el MISMO de la
 * consulta guiada ([CampoTextoClinico], gemelo de CampoConFrases.tsx) con las
 * frases ya mezcladas ([FrasesCampoPlan]: base ∪ lo que la clínica escribió).
 * Tocar un chip lo agrega al final (con coma o en otra línea) y tocarlo otra
 * vez lo quita; al escribir se ofrece lo aprendido y la base.
 *
 * [frases] null (aún no llegaron, sin red o backend viejo) = el campo de texto
 * de siempre, sin chips: nunca bloquea ni avisa.
 */
@Composable
internal fun CampoConFrases(
    label: String,
    valor: String,
    onChange: (String) -> Unit,
    frases: FrasesCampoPlan?,
    placeholder: String? = null,
    minLineas: Int = 2,
    chipsVisibles: Int = 6,
    soloLectura: Boolean = false,
    /** false = sin la etiqueta encima (el formulario ya la muestra). */
    mostrarEtiqueta: Boolean = true,
    /** Para la clave interna de los chips (no se muestra). */
    campo: String = "frase",
) {
    val chips = remember(frases, campo) { frases?.chips.orEmpty().map { FraseFrecuenteApp(campo, it.texto, 0) } }
    CampoTextoClinico(
        label = label, valor = valor, onChange = onChange, frases = chips, soloLectura = soloLectura,
        minLineas = minLineas, placeholder = placeholder, porLinea = frases?.porLinea ?: false,
        opciones = frases?.opciones.orEmpty(), chipsVisibles = chipsVisibles, mostrarEtiqueta = mostrarEtiqueta,
    )
}

/**
 * Las frases de los [campos] abiertos de UNA pantalla (`/api/staff/frases`, un
 * solo viaje al abrirla; [FrasesCampoRepo] lo guarda en caché y al reabrir se
 * pinta sin parpadeo). Mientras llega, o si falla, el mapa está vacío y cada
 * campo queda como siempre. [activo] false = todavía no se pide (sección
 * plegada, solo lectura).
 */
@Composable
internal fun recordarFrasesCampos(
    campos: List<String>,
    especialidadId: String? = null,
    rubro: String? = null,
    activo: Boolean = true,
): Map<String, FrasesCampoPlan> {
    val clave = campos.sorted().joinToString(",")
    var datos by remember(clave, especialidadId, rubro) {
        mutableStateOf(mezclar(FrasesCampoRepo.enCache(campos, especialidadId, rubro)))
    }
    LaunchedEffect(clave, especialidadId, rubro, activo) {
        if (activo && datos.isEmpty()) datos = mezclar(FrasesCampoRepo.cargar(campos, especialidadId, rubro))
    }
    return datos
}

private fun mezclar(r: Map<String, RespuestaFrasesCampo>?): Map<String, FrasesCampoPlan> =
    r.orEmpty().mapValues { frasesDeCampoAbierto(it.value) }
