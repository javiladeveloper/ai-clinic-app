package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// ─────────────────────────────────────────────────────────────────────────────
// Informe psicológico — FASE 3: anexo de perfiles y borrador con IA
// (contrato §14.2 y §14.4; web: lib/informe-psico-ia.ts).
//
// La IA hoy está APAGADA en todos los planes (feature `ia` del plan): la UI solo
// ofrece el botón con `ctx.can("ia")`, y el servidor lo vuelve a verificar.
// No guarda nada: propone texto para las secciones VACÍAS; aceptar es por
// sección y lo escribe la app en el borrador con la marca `asistido_ia`.
// ─────────────────────────────────────────────────────────────────────────────

/** Secciones que la IA puede proponer (nunca la impresión diagnóstica ni las técnicas). */
val SECCIONES_IA_PSICO = listOf("motivo", "antecedentes", "observacion", "resultados", "conclusiones", "recomendaciones")

data class PropuestasIaPsico(
    /** clave de sección → texto propuesto (en el orden de [SECCIONES_IA_PSICO]). */
    val propuestas: Map<String, String>,
    val modelo: String = "",
    /** Constaba un consentimiento firmado que menciona la IA. */
    val consentimientoIA: Boolean = false,
)

/** `{ ok, propuestas, secciones, modelo, consentimientoIA }`. Solo secciones conocidas y con texto. */
internal fun parsearPropuestasIa(o: JsonObject?): PropuestasIaPsico {
    val p = o?.get("propuestas") as? JsonObject
    val mapa = SECCIONES_IA_PSICO.mapNotNull { k ->
        val t = (p?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()
        if (t.isNullOrEmpty()) null else k to t
    }.toMap()
    return PropuestasIaPsico(
        propuestas = mapa,
        modelo = (o?.get("modelo") as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }.orEmpty(),
        consentimientoIA = (o?.get("consentimientoIA") as? JsonPrimitive)?.contentOrNull == "true",
    )
}

/**
 * Aceptar la propuesta de UNA sección (aceptarPropuesta de la web): la escribe
 * en el borrador y suma la sección a `asistido_ia` (la marca solo crece). Una
 * sección con texto no se pisa salvo [reemplazar].
 */
fun aceptarPropuestaIa(
    c: ContenidoInformePsico, clave: String, texto: String,
    modelo: String, hoy: String, sinConsentimientoConfirmado: Boolean, reemplazar: Boolean = false,
): ContenidoInformePsico {
    if (clave !in SECCIONES_IA_PSICO || texto.isBlank()) return c
    if (!c.secciones[clave].isNullOrBlank() && !reemplazar) return c
    val previo = c.asistidoIa
    return c.copy(
        secciones = c.secciones + (clave to texto.trim()),
        asistidoIa = AsistidoIaPsico(
            secciones = (previo?.secciones.orEmpty() + clave).distinct(),
            modelo = modelo.ifBlank { previo?.modelo.orEmpty() },
            fecha = hoy,
            sinConsentimientoConfirmado = previo?.sinConsentimientoConfirmado == true || sinConsentimientoConfirmado,
        ),
    )
}

/** ¿La sección lleva el sello "✨ asistido por IA"? */
fun seccionAsistidaIa(c: ContenidoInformePsico, clave: String): Boolean =
    clave in SECCIONES_IA_PSICO && c.asistidoIa?.secciones?.contains(clave) == true

/**
 * La casilla "Incluir anexo de perfiles" se ofrece si el servidor tiene la fase
 * 3 (respondió GET /instrumentos) o si el contenido ya trae `anexoPerfiles`.
 */
fun ofrecerAnexoPerfiles(fase3: Boolean, c: ContenidoInformePsico): Boolean = fase3 || c.anexoPerfiles != null

/** Marca o desmarca el anexo; los perfiles que ya calculó el servidor se conservan hasta el próximo guardado. */
fun conAnexoPerfiles(c: ContenidoInformePsico, incluir: Boolean): ContenidoInformePsico =
    c.copy(anexoPerfiles = AnexoPerfilesPsico(incluir, c.anexoPerfiles?.perfiles.orEmpty()))

