package pe.saniape.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

/** Scope de corutina ligado al Composable, en el hilo principal (igual que Android). */
@Composable
fun rememberAppScope(): CoroutineScope = remember { MainScope() }
