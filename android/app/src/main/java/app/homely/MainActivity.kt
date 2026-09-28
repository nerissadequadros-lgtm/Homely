package app.homely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.homely.ui.HomelyApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomelyApp(
                viewModel(),
                intent.getStringExtra("editor"),
                intent.getStringExtra("package"),
                intent.getBooleanExtra("notifications", false),
            )
        }
    }
}
