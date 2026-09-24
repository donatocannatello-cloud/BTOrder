"""Interfaccia comune a tutti gli adattatori (siti sorgente)."""

from __future__ import annotations

from abc import ABC, abstractmethod

from http_client import PoliteClient


class Adapter(ABC):
    #: identificativo della fonte, usato come prefisso negli ID ("pvp", ...)
    nome: str = ""
    #: categorie gestite dall'adattatore ("capannoni", in futuro "auto", "moto")
    categorie_supportate: tuple[str, ...] = ()

    def __init__(self, client: PoliteClient, config: dict | None = None):
        self.client = client
        self.config = config or {}

    def supporta(self, categoria: str) -> bool:
        return categoria in self.categorie_supportate

    @abstractmethod
    def fetch_listings(self, filters: dict) -> list[dict]:
        """Restituisce gli annunci trovati, già mappati sui campi di ``models.Annuncio``.

        ``filters`` contiene almeno ``categoria`` e opzionalmente ``regioni``,
        ``province``, ``prezzo_max``, ``superficie_min`` e le impostazioni
        specifiche della categoria (es. ``parole_chiave``).
        """

    @abstractmethod
    def fetch_detail(self, listing: dict) -> dict:
        """Arricchisce un annuncio con i dati della pagina di dettaglio
        (es. link alla perizia) e restituisce i soli campi aggiornati."""
