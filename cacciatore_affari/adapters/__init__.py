from adapters.base import Adapter
from adapters.pvp import PVPAdapter

#: registro degli adattatori disponibili, chiave = nome in config.json ("fonti")
REGISTRO: dict[str, type[Adapter]] = {
    PVPAdapter.nome: PVPAdapter,
}

__all__ = ["Adapter", "PVPAdapter", "REGISTRO"]
