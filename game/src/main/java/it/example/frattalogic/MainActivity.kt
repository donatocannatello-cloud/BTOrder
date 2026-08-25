package it.example.frattalogic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import it.example.frattalogic.engine.ExplorationViewModel
import it.example.frattalogic.ui.ExplorationScreen
import it.example.frattalogic.ui.theme.FrattaLogicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ExplorationViewModel by lazy {
        ViewModelProvider(this)[ExplorationViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrattaLogicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExplorationScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.avvia()
    }

    override fun onPause() {
        viewModel.ferma()
        super.onPause()
    }
}
