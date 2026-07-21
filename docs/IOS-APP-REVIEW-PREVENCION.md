# Sania iOS — prevención de rechazos de App Review (aprendido de FitCore)

**Fecha:** 2026-07-20
Aplicamos a Sania, de forma **proactiva**, lo que a FitCore le rebotó Apple, para no
repetir ciclos cuando Sania pueda enviarse al App Store (bloqueada hasta tener cuenta de
**organización** — ver [[sania-cuenta-organizacion]] / empresa RUC 20 + D-U-N-S ~ago 2026).

## Estado por punto

| Punto (rechazo de FitCore) | ¿Aplica a Sania? | Estado |
|---|---|---|
| Login abre navegador externo (Guideline 4) | ❌ No — Sania usa **GoogleSignIn nativo** (GIDClientID) | OK |
| Botón "Sign in with Apple" no visible en iPad (Guideline 4) | 🟡 Bajo — login **ya scrollea**; se añadió `safeDrawingPadding` | ✅ Hecho |
| Lenguaje "próximamente/Pronto" (Guideline 2.1) | ❌ No hay en Sania | OK |
| CI compila con SDK viejo (Xcode 16 → iOS 18) | ✅ Sí, mismo bug | ✅ Corregido a Xcode 26 |
| **Eliminar cuenta desde la app** (5.1.1(v)) | ✅ Sí | ✅ **Hecho** 2026-07-20 (abajo) |
| Modelo de negocio / IAP (Guideline 2.1(b)) | 🟡 Posible (pagos) | Respuesta lista (abajo) |
| Demo account con contenido (2.1(a)) | ✅ Ya existe | `admin_test@admin.com` |

---

## ✅ HECHO — Eliminar cuenta desde la app (2026-07-20)

**Qué borra:** el ACCESO (usuario de auth + `paciente_cuentas` + `portal_vinculos`).
**Qué NO borra:** la ficha del paciente, historia clínica, citas, pagos y documentos.
Decisión del dueño, y es lo legalmente correcto: esos datos son del establecimiento de
salud, obligado a conservarlos. Apple exige borrar la CUENTA, no destruir historias
clínicas. El diálogo se lo dice al paciente con todas las letras.

**Backend:** `POST /api/paciente/eliminar-cuenta` (no RPC: hace falta `service_role` para
`auth.admin.deleteUser`, que RLS no puede hacer).
- Guarda anti-staff: si la cuenta tiene fila en `perfiles` → **409 `ES_STAFF`**. En Sania
  toda cuenta puede usar el portal (el callback hace upsert incondicional), y como
  `perfiles.id` cascadea desde `auth.users`, sin esta guarda un Admin se autodestruiría
  el acceso al panel y podría dejar la clínica sin administrador.
- La guarda consulta con `service_role`, no con el cliente de sesión: un paciente no puede
  leer `perfiles`, así que con RLS la consulta volvería vacía y la guarda no protegería nada.
- Limpieza explícita de `portal_vinculos` (su `auth_user_id` NO tiene FK, no cascadea) y
  `portal_invitaciones.usado_por` → NULL (se conserva el rastro del canje, desligado).
- Borra el usuario de auth de verdad: un borrado "a medias" sería un no-op, porque el
  callback y el layout del portal recrean `paciente_cuentas` en el siguiente login.
- Rate limit 3/hora por usuario.

**App:** `PerfilRepo.eliminarCuenta()` + enlace "Eliminar mi cuenta" en la pantalla
**Mi cuenta** (`PantallasTabs.kt`), con diálogo de confirmación. No se muestra si la cuenta
es staff (`puedeIrAClinica`). Al terminar llama `onCerrarSesion()`.

**Verificado end-to-end contra la base real:**
- Admin intentando borrarse → 409, y sus perfiles **intactos**.
- Paciente de prueba → usuario de auth, cuenta de portal y vínculos a **0**; las 260 fichas
  clínicas **sin tocar**.

Ruta para las notas del revisor: **Mi cuenta → Eliminar mi cuenta**.
Referencia: https://developer.apple.com/support/offering-account-deletion-in-your-app

---

## Respuesta lista — Guideline 2.1(b) (modelo de negocio), si Apple pregunta

> Sania is a management tool for service businesses (clinics, spas, salons, aesthetics)
> that work by appointment. It does not sell digital content or digital subscriptions and
> does not use In-App Purchase.
> Patients use the app to view and book appointments with a specific business. Any payment
> corresponds to **real-world, physical services** provided in person by that business, and
> is handled outside the app. No digital content or subscriptions are unlocked in the app,
> so In-App Purchase does not apply (per Guidelines 3.1.3(e) / 3.1.1).

## Notas para el revisor (cuando se envíe)
- Demo: `admin_test@admin.com` / `Admin-Test-2026` (Admin de "Clinica Test", con datos).
- Login: paciente con Apple/Google (nativo, sin navegador); clínica con correo/contraseña.
- Eliminar cuenta: **Mi cuenta → Eliminar mi cuenta** (portal del paciente).
- Categoría en la ficha: **Negocios** (no Medicina) — se reposicionó para el 5.1.1(ix).

## Lo ya hecho en esta pasada (iOS/plataforma)
- CI iOS → Xcode 26 (`.github/workflows/ios-release.yml`) + build number automático en el Fastfile.
- Login: `safeDrawingPadding` (iPad).
