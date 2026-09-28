package app.homely.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** One persistent brand header shared by every application destination. */
@Composable
fun HomelyHeader(
    onHome: () -> Unit,
    onNotifications: () -> Unit,
    unread: Int = 0,
    title: String = "Homely",
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 18.dp)
                .testTag("homely-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconButton(onBack) { CleanIcon(Glyph.Back) }
            TextButton(
                onHome,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
            ) {
                if (title == "Homely") BrandMark(Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = if (title == "Homely") 23.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.7).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (onSearch != null) IconButton(onSearch) { CleanIcon(Glyph.Search) }
            IconButton(onNotifications, Modifier.testTag("discovery-bell")) {
                BadgedBox(
                    badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Outlined.NotificationsNone,
                        "Notificações",
                    )
                }
            }
        }
    }
}
