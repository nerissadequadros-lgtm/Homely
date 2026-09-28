package app.homely.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.homely.platform.CreationFolders

@Composable
internal fun SaveCreationDialog(
    kind: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (folder: String, name: String) -> Unit,
) {
    val ctx = LocalContext.current
    val folders = remember(kind) { CreationFolders.folders(ctx, kind) }
    var name by remember { mutableStateOf(initialName.take(60)) }
    var folder by remember { mutableStateOf(folders.first()) }
    var newFolder by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar") },
        text = {
            Column(
                Modifier.testTag("save-creation-dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Field(name, { name = it.take(60) }, "Nome")
                BuilderChoices(
                    if (newFolder) "new" else folder,
                    folders.map { it to it } + ("new" to "+ Nova pasta"),
                ) {
                    newFolder = it == "new"
                    folder = if (newFolder) "" else it
                }
                if (newFolder) Field(folder, { folder = it.take(60) }, "Nome da pasta")
            }
        },
        confirmButton = {
            TextButton(
                { onSave(folder.trim(), name.trim()) },
                enabled =
                    name.trim().length >= (if (kind == "theme") 3 else 1) && folder.isNotBlank(),
                modifier = Modifier.testTag("confirm-save-creation"),
            ) {
                Text("Salvar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
