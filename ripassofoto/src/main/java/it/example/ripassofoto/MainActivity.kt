package it.example.ripassofoto

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.example.ripassofoto.data.PaginaStudio
import it.example.ripassofoto.ui.Rotte
import it.example.ripassofoto.ui.screens.SchermataDettaglioPagina
import it.example.ripassofoto.ui.screens.SchermataFotocamera
import it.example.ripassofoto.ui.screens.SchermataHome
import it.example.ripassofoto.ui.screens.SchermataImpostazioni
import it.example.ripassofoto.ui.screens.SchermataQuiz
import it.example.ripassofoto.ui.screens.SchermataRevisioneTesto
import it.example.ripassofoto.ui.screens.SchermataRisultato
import it.example.ripassofoto.ui.theme.RipassoFotoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RipassoFotoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRipassoFoto()
                }
            }
        }
    }
}

@Composable
fun AppRipassoFoto() {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: StudioViewModel = viewModel(factory = StudioViewModel.factory(application))
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val pagine by viewModel.pagine.collectAsState()
    val domande by viewModel.domandeCorrenti.collectAsState()
    val generandoDomande by viewModel.generandoDomande.collectAsState()
    val avviso by viewModel.avviso.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(avviso) {
        avviso?.let { messaggio ->
            snackbarHostState.showSnackbar(messaggio)
            viewModel.consumaAvviso()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Rotte.HOME) {
            composable(Rotte.HOME) {
                SchermataHome(
                    pagine = pagine,
                    onNuovaFoto = { navController.navigate(Rotte.FOTOCAMERA) },
                    onAperturaPagina = { pagina -> navController.navigate(Rotte.dettaglio(pagina.id)) },
                    onImpostazioni = { navController.navigate(Rotte.IMPOSTAZIONI) }
                )
            }

            composable(Rotte.IMPOSTAZIONI) {
                val chiaveAttuale = remember { viewModel.leggiChiaveApi() }
                SchermataImpostazioni(
                    chiaveIniziale = chiaveAttuale,
                    onSalva = { chiave ->
                        viewModel.salvaChiaveApi(chiave)
                        navController.popBackStack()
                    },
                    onElimina = {
                        viewModel.eliminaChiaveApi()
                        navController.popBackStack()
                    },
                    onIndietro = { navController.popBackStack() }
                )
            }

            composable(Rotte.FOTOCAMERA) {
                SchermataFotocamera(
                    onFotoScattata = { file ->
                        viewModel.fileFotoCorrente = file
                        navController.navigate(Rotte.REVISIONE)
                    },
                    onAnnulla = { navController.popBackStack() }
                )
            }

            composable(Rotte.REVISIONE) {
                val file = viewModel.fileFotoCorrente
                if (file == null) {
                    LaunchedEffect(Unit) { navController.popBackStack(Rotte.HOME, inclusive = false) }
                } else {
                    SchermataRevisioneTesto(
                        file = file,
                        riconosciTesto = { viewModel.riconosciTesto(it) },
                        onGeneraQuiz = { titolo, testo ->
                            scope.launch {
                                viewModel.generaNuoveDomande(testo)
                                val id = viewModel.salvaPagina(titolo, testo, file.absolutePath)
                                navController.navigate(Rotte.quiz(id)) { popUpTo(Rotte.HOME) }
                            }
                        },
                        onAnnulla = { navController.popBackStack() }
                    )
                }
            }

            composable(
                Rotte.QUIZ,
                arguments = listOf(navArgument("paginaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val paginaId = backStackEntry.arguments?.getLong("paginaId") ?: -1L
                SchermataQuiz(
                    domande = domande,
                    onQuizCompletato = { punteggio, totale ->
                        scope.launch {
                            viewModel.leggiPagina(paginaId)?.let { pagina ->
                                viewModel.registraEsito(pagina, punteggio, totale)
                            }
                            navController.navigate(Rotte.risultato(paginaId, punteggio, totale)) {
                                popUpTo(Rotte.HOME)
                            }
                        }
                    }
                )
            }

            composable(
                Rotte.RISULTATO,
                arguments = listOf(
                    navArgument("paginaId") { type = NavType.LongType },
                    navArgument("punteggio") { type = NavType.IntType },
                    navArgument("totale") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val args = backStackEntry.arguments!!
                val paginaId = args.getLong("paginaId")
                SchermataRisultato(
                    punteggio = args.getInt("punteggio"),
                    totale = args.getInt("totale"),
                    onRifaiQuiz = {
                        scope.launch {
                            viewModel.leggiPagina(paginaId)?.let { pagina ->
                                viewModel.generaNuoveDomande(pagina.testoEstratto)
                                navController.navigate(Rotte.quiz(paginaId)) { popUpTo(Rotte.HOME) }
                            }
                        }
                    },
                    onTornaAllaHome = {
                        navController.navigate(Rotte.HOME) { popUpTo(Rotte.HOME) { inclusive = true } }
                    }
                )
            }

            composable(
                Rotte.DETTAGLIO,
                arguments = listOf(navArgument("paginaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val paginaId = backStackEntry.arguments?.getLong("paginaId") ?: -1L
                var pagina by remember(paginaId) { mutableStateOf<PaginaStudio?>(null) }
                LaunchedEffect(paginaId) { pagina = viewModel.leggiPagina(paginaId) }

                pagina?.let { paginaCorrente ->
                    SchermataDettaglioPagina(
                        pagina = paginaCorrente,
                        onRifaiQuiz = {
                            scope.launch {
                                viewModel.generaNuoveDomande(paginaCorrente.testoEstratto)
                                navController.navigate(Rotte.quiz(paginaCorrente.id))
                            }
                        },
                        onElimina = {
                            scope.launch {
                                viewModel.eliminaPagina(paginaCorrente)
                                navController.popBackStack()
                            }
                        },
                        onIndietro = { navController.popBackStack() }
                    )
                }
            }
        }

        if (generandoDomande) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Sto generando le domande…",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
