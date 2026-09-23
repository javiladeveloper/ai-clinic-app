package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.HallazgoInterpretado
import pe.saniape.app.data.staff.interpretarDictadoDental
import pe.saniape.app.data.staff.nombreCara
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/**
 * Dictar el odontograma: "pieza dieciséis caries oclusal, pieza veinticuatro
 * ausente, sarro generalizado" y se marca todo junto.
 *
 * Con los guantes puestos y el paciente en el sillón, hablar es mucho mejor que
 * tocar 32 dientes. Pero NADA se marca sin que el odontólogo lo vea antes: el
 * texto reconocido queda editable (el reconocedor oye "17" donde se dijo "16")
 * y abajo se ve exactamente qué va a marcar.
 *
 * Gemelo de `components/odontologia/DictadoOdontograma.tsx` en la web; el
 * parser es el mismo (`interpretarDictadoDental`).
 */
@Composable
internal fun DictadoOdontograma(
    catalogo: List<HallazgoDental>,
    onCerrar: () -> Unit,
    /** Recibe SOLO lo que se puede marcar (con hallazgo del catálogo). */
    onAplicar: (List<HallazgoInterpretado>) -> Unit,
) {
    val c = Sania.colors
    // Lo ya confirmado por el reconocedor + lo que se está oyendo ahora.
    var confirmado by remember { mutableStateOf("") }
    var parcial by remember { mutableStateOf("") }
    var escuchando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val control = recordarReconocedorVoz(
        onTexto = { t, final ->
            if (final) {
                // Cada frase se SUMA a lo anterior: se dicta pieza por pieza.
                confirmado = listOf(confirmado, t).filter { it.isNotBlank() }.joinToString(" ")
                parcial = ""
            } else parcial = t
        },
        onEscuchando = { escuchando = it },
        onError = { aviso = it },
    )

    val texto = listOf(confirmado, parcial).filter { it.isNotBlank() }.joinToString(" ")
    val interpretados = remember(texto, catalogo) { interpretarDictadoDental(texto, catalogo) }
    // Lo que no está en el catálogo de la clínica no se puede marcar (no hay a
    // qué hallazgo apuntar). Se muestra igual, apagado, para que se sepa.
    val aplicables = interpretados.filter { it.hallazgoId != null }

    DialogoForm(
        titulo = "🎙 Dictar odontograma",
        subtitulo = "Ej.: pieza dieciséis caries oclusal, pieza veinticuatro ausente",
        textoAccion = if (aplicables.isEmpty()) "Nada que marcar" else "Marcar ${aplicables.size}",
        accionHabilitada = aplicables.isNotEmpty() && !escuchando,
        onCancelar = { control.detener(); onCerrar() },
        onAccion = { control.detener(); onAplicar(aplicables) },
    ) {
        // ── Micrófono ────────────────────────────────────────────────────
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(if (escuchando) c.error else c.navy)
                    .clickable {
                        aviso = null
                        if (escuchando) control.detener() else control.iniciar()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (escuchando) "■" else "🎙", fontSize = 28.sp, color = c.sobreNavy)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    escuchando -> "Escuchando… toca para terminar"
                    !control.disponible -> "Usa el micrófono del teclado o escribe"
                    else -> "Toca y dicta"
                },
                color = if (escuchando) c.error else c.textoSuave, fontSize = 12.sp,
            )
        }

        aviso?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.error, fontSize = 12.sp)
        }

        // ── El texto, corregible ─────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        EtqForm("Lo que se entendió (puedes corregirlo)")
        OutlinedTextField(
            value = texto,
            onValueChange = { confirmado = it; parcial = "" },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            colors = coloresCampoForm(),
            placeholder = { Text("pieza 16 caries oclusal…", color = c.textoSuave) },
        )

        // ── Qué se va a marcar ───────────────────────────────────────────
        if (interpretados.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            EtqForm("Se va a marcar")
            interpretados.forEach { h -> FilaInterpretada(h) }
        } else if (texto.isNotBlank() && !escuchando) {
            Spacer(Modifier.height(10.dp))
            Text(
                "No reconozco ninguna pieza. Nombra el número: \"pieza dieciséis caries\".",
                color = c.textoSuave, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun FilaInterpretada(h: HallazgoInterpretado) {
    val c = Sania.colors
    val ok = h.hallazgoId != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colorDe(h.color)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val donde = if (h.tipo == "boca") "Boca completa" else "Pieza ${h.diente}"
            Text(
                "$donde · ${h.hallazgoNombre}",
                color = if (ok) c.texto else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            )
            h.superficies?.let { caras ->
                Text(caras.joinToString(", ") { nombreCara(it) }, color = c.textoSuave, fontSize = 11.sp)
            }
            if (!ok) {
                Text("No está en el catálogo de tu clínica: no se marca.", color = c.error, fontSize = 11.sp)
            }
        }
    }
}
