package pe.saniape.app.data

/**
 * Almacén de preferencias simples (modo activo de la cuenta). expect/actual:
 * en Android usa SharedPreferences. El modo NO es seguridad (la RLS lo valida);
 * solo recuerda en qué portal estaba el usuario.
 */
expect object Preferencias {
    /** "clinica" | "paciente" | null (no elegido aún). */
    fun modoActivo(): String?
    fun setModoActivo(modo: String?)
}
