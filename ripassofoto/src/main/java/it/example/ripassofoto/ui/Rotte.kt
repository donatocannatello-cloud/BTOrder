package it.example.ripassofoto.ui

object Rotte {
    const val HOME = "home"
    const val FOTOCAMERA = "fotocamera"
    const val REVISIONE = "revisione"
    const val IMPOSTAZIONI = "impostazioni"

    const val QUIZ = "quiz/{paginaId}"
    fun quiz(paginaId: Long) = "quiz/$paginaId"

    const val RISULTATO = "risultato/{paginaId}/{punteggio}/{totale}"
    fun risultato(paginaId: Long, punteggio: Int, totale: Int) = "risultato/$paginaId/$punteggio/$totale"

    const val DETTAGLIO = "dettaglio/{paginaId}"
    fun dettaglio(paginaId: Long) = "dettaglio/$paginaId"
}
