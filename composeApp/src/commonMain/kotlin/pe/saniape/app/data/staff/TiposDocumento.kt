package pe.saniape.app.data.staff

/**
 * TIPO DE DOCUMENTO DEL PACIENTE — gemelo de la parte "documento" de
 * `components/pacientes/regional-paciente.ts` (web).
 *
 * `pacientes.tipo_documento` (migración 20261017500000): 'DNI', 'Carné de
 * extranjería', 'Pasaporte', 'CI', 'RUT', 'Otro'; NULL = el documento nacional
 * del país de la sede (así quedan todas las fichas de antes: DALU no cambia).
 *
 * Los códigos de las opciones SON lo que se guarda; "" = el documento nacional
 * genérico de un país sin documento propio en el catálogo (se guarda NULL).
 */

val TIPOS_DOCUMENTO_PACIENTE: List<String> = listOf("DNI", "Carné de extranjería", "Pasaporte", "CI", "RUT", "Otro")

private val ALIAS_TIPO_DOC = mapOf(
    "dni" to "DNI", "ce" to "Carné de extranjería", "carne" to "Carné de extranjería",
    "carnedeextranjeria" to "Carné de extranjería", "pasaporte" to "Pasaporte",
    "ci" to "CI", "rut" to "RUT", "otro" to "Otro",
)

private fun sinTildes(s: String): String = s
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    .replace('Á', 'a').replace('É', 'e').replace('Í', 'i').replace('Ó', 'o').replace('Ú', 'u')

/** Valor válido para `pacientes.tipo_documento`, o null (tolera "CE", "pasaporte"…). */
fun normalizarTipoDocumento(v: String?): String? {
    val s = v?.trim().orEmpty()
    if (s.isEmpty()) return null
    TIPOS_DOCUMENTO_PACIENTE.firstOrNull { it == s }?.let { return it }
    val k = sinTildes(s).lowercase().filter { it in 'a'..'z' }
    return ALIAS_TIPO_DOC[k]
}

private fun normPaisDoc(pais: String?): String {
    val p = pais?.trim()?.uppercase().orEmpty()
    return if (p.length == 2 && p.all { it in 'A'..'Z' }) p else PAIS_POR_DEFECTO
}

private val OPC_DNI = "DNI" to "🇵🇪 DNI"
private val OPC_RUT = "RUT" to "🇨🇱 RUT"
private val OPC_CI = "CI" to "🇧🇴 CI"
private val OPC_PASAPORTE = "Pasaporte" to "🌎 Pasaporte"
private val OPC_CARNE = "Carné de extranjería" to "🪪 Carné de extranjería"
private val OPC_OTRO = "Otro" to "🪪 Otro documento"

/** Lo que se guarda para el documento nacional del país: DNI (PE), CI (BO), RUT (CL); otro país "" (= NULL). */
fun codigoDocumentoNacional(pais: String?): String = when (normPaisDoc(pais)) {
    "PE" -> "DNI"
    "BO" -> "CI"
    "CL" -> "RUT"
    else -> ""
}

/**
 * Documentos PRINCIPALES (chips) al registrar un paciente, el primero por
 * defecto. Perú: DNI (busca en RENIEC) / RUT / pasaporte, como siempre. Otro
 * país: su documento nacional (Bolivia: CI) y pasaporte, sin RENIEC.
 */
fun opcionesDocumento(pais: String?): List<Pair<String, String>> {
    val p = normPaisDoc(pais)
    return when (p) {
        PAIS_POR_DEFECTO -> listOf(OPC_DNI, OPC_RUT, OPC_PASAPORTE)
        "BO" -> listOf(OPC_CI, OPC_PASAPORTE)
        "CL" -> listOf(OPC_RUT, OPC_PASAPORTE)
        else -> listOf("" to "${banderaPais(p)} ${nombreDocumentoNacional(p)}", OPC_PASAPORTE)
    }
}

/** Los DEMÁS tipos ("Otro tipo"): carné de extranjería, CI, DNI… sin repetir los principales. */
fun opcionesDocumentoExtra(pais: String?): List<Pair<String, String>> {
    val principales = opcionesDocumento(pais).map { it.first }.toSet()
    return listOf(OPC_CARNE, OPC_DNI, OPC_CI, OPC_RUT, OPC_PASAPORTE, OPC_OTRO).filter { it.first !in principales }
}

/** Todos los tipos que se ofrecen en ese país. */
fun todasOpcionesDocumento(pais: String?): List<Pair<String, String>> = opcionesDocumento(pais) + opcionesDocumentoExtra(pais)

/**
 * Tipo de la filiación de la HC que sirve para MOSTRAR el tipo de un paciente
 * sin tipo propio: solo Pasaporte / Carné de extranjería / Otro. 'DNI' lo pone
 * la HC sola a cualquier documento de 8 dígitos (también a un CI).
 */
fun tipoDocumentoDeHc(tipoHc: String?): String? = normalizarTipoDocumento(tipoHc)?.takeIf { it != "DNI" }

/**
 * Tipo que se MUESTRA para un paciente (editar / reactivar): el guardado manda;
 * luego el de la HC; sin ninguno se deduce — Perú: 8 dígitos = DNI, si no RUT
 * (como siempre); otro país, el nacional. Lo deducido NO se guarda si nadie lo
 * toca ([tipoDocumentoAGuardar]).
 */
fun deducirTipoDocumento(dni: String?, pais: String?, guardado: String? = null, tipoHc: String? = null): String {
    (normalizarTipoDocumento(guardado) ?: tipoDocumentoDeHc(tipoHc))?.let { return it }
    val p = normPaisDoc(pais)
    if (p == PAIS_POR_DEFECTO) {
        val d = dni?.trim().orEmpty()
        return if (d.isEmpty() || (d.length == 8 && d.all { it.isDigit() })) "DNI" else "RUT"
    }
    return codigoDocumentoNacional(p)
}

/**
 * Lo que se guarda: SOLO un tipo elegido a mano ([tocado]); uno adivinado (RUT
 * por tener letras, DNI por defecto) queda null = "el del país" y ni se manda.
 * Sin documento escrito, null; el nacional genérico "" → null.
 */
fun tipoDocumentoAGuardar(codigo: String?, documento: String?, tocado: Boolean): String? =
    if (!tocado || documento.isNullOrBlank()) null else normalizarTipoDocumento(codigo)

/** ¿Se busca en el padrón (RENIEC)? Solo DNI peruano en una sede de Perú. */
fun buscaPadronDoc(pais: String?, tipo: String): Boolean = usaReniec(pais) && tipo == "DNI"

/**
 * Rótulo del documento DE UN PACIENTE (como la web): su tipo guardado manda
 * ("Pasaporte", "Carné de extranjería", "CI"…, 'Otro' → "Documento"); luego el
 * de la HC; sin tipo, el del país ([nombreDocumentoNacional]) — salvo en Perú
 * un documento que no es un DNI de 8 dígitos: "Documento". DALU → "DNI".
 */
fun etiquetaDocumentoPaciente(tipo: String?, pais: String?, dni: String? = null, tipoHc: String? = null): String {
    return when (val t = normalizarTipoDocumento(tipo) ?: tipoDocumentoDeHc(tipoHc)) {
        null -> {
            val d = dni?.trim().orEmpty()
            val esDni = d.length == 8 && d.all { it.isDigit() }
            if (normPaisDoc(pais) == PAIS_POR_DEFECTO && d.isNotEmpty() && !esDni) "Documento" else nombreDocumentoNacional(pais)
        }
        "Otro" -> "Documento"
        else -> t
    }
}

/** ¿Hace falta leer el tipo para rotular? No en una clínica de un local con un documento de 8 dígitos (DALU). */
fun hayQueLeerTipoDocumento(dni: String?, multiSede: Boolean): Boolean {
    val d = dni?.trim().orEmpty()
    if (d.isEmpty()) return false
    return multiSede || !(d.length == 8 && d.all { it.isDigit() })
}
