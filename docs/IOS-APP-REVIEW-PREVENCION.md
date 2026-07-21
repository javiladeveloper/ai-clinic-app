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
| **Eliminar cuenta desde la app** (5.1.1(v)) | ✅ **Sí — FALTA** | 🔴 Pendiente (abajo) |
| Modelo de negocio / IAP (Guideline 2.1(b)) | 🟡 Posible (pagos) | Respuesta lista (abajo) |
| Demo account con contenido (2.1(a)) | ✅ Ya existe | `admin_test@admin.com` |

---

## 🔴 PENDIENTE — Eliminar cuenta desde la app (para el agente)

Los **pacientes** inician sesión con Google/Apple; eso **crea** un usuario en Supabase Auth
→ Apple exige poder **eliminar la cuenta** desde la app. Sania hoy NO lo tiene. Sin esto,
el envío al App Store rebota (igual que FitCore).

**Backend (repo web `ai-clinic-dashboard`, aplicar en Supabase):**
- RPC `eliminar_mi_cuenta()` autenticada (Bearer del propio usuario, RLS, sin admin) que
  borra/anonimiza la identidad de auth + la PII del paciente (nombre, contacto, documento,
  foto). Historias clínicas/citas: anonimizar o soft-delete según reglas de la clínica;
  documentar qué se conserva por normativa.
- ⚠️ Solo para el rol **paciente/portal** (no borrar staff de una clínica desde aquí).

**App (commonMain):**
- En el portal del paciente (Perfil/Ajustes): botón **"Eliminar mi cuenta"** (rojo) →
  diálogo de confirmación ("acción permanente") → llama la RPC → `signOut`.
- Referencia: https://developer.apple.com/support/offering-account-deletion-in-your-app

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
- Eliminar cuenta: (ruta cuando el agente la implemente).
- Categoría en la ficha: **Negocios** (no Medicina) — se reposicionó para el 5.1.1(ix).

## Lo ya hecho en esta pasada (iOS/plataforma)
- CI iOS → Xcode 26 (`.github/workflows/ios-release.yml`) + build number automático en el Fastfile.
- Login: `safeDrawingPadding` (iPad).
