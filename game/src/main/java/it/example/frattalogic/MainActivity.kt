package it.example.frattalogic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import it.example.frattalogic.engine.DiveViewModel
import it.example.frattalogic.ui.DiveScreen
import it.example.frattalogic.ui.theme.FrattaLogicTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DiveViewModel by lazy {
        ViewModelProvider(this)[DiveViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FrattaLogicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiveScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.avviaAudio()
    }

    override fun onPause() {
        viewModel.fermaAudio()
        super.onPause()
    }
}
