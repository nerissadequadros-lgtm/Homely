package app.homely.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.homely.data.*
import java.net.URLEncoder
import kotlinx.coroutines.*

@Composable
fun UsernameField(
    value: String,
    onChange: (String) -> Unit,
    vm: HomelyViewModel,
    onValid: (Boolean) -> Unit = {},
) {
    var status by remember { mutableStateOf("") }
    var valid by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        valid = false
        onValid(false)
        if (!Regex("[a-z][a-z0-9_]{2,23}").matches(value)) {
            status = "3–24 caracteres: comece com uma letra; use letras, números e _"
            return@LaunchedEffect
        }
        status = "Verificando disponibilidade…"
        delay(450)
        try {
            val result =
                vm.repo.get<UsernameAvailability>(
                    "/v1/usernames/availability?username=" + URLEncoder.encode(value, "UTF-8")
                )
            valid = result.available
            onValid(valid)
            status = if (valid) "@" + value + " disponível" else "Esse @ já está em uso"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Não foi possível consultar. Tente novamente."
            onValid(false)
        }
    }
    OutlinedTextField(
        value,
        { onChange(it.removePrefix("@").lowercase().take(24)) },
        Modifier.fillMaxWidth(),
        label = { Text("Nome de usuário") },
        prefix = { Text("@") },
        singleLine = true,
        isError = status == "Esse @ já está em uso",
        supportingText = {
            Text(
                status,
                color =
                    if (valid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16),
    )
}
