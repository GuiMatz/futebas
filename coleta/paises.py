"""Nome de país (inglês, como no Transfermarkt) -> ISO-3."""
import pycountry

MANUAL = {
    "Bonaire": "BES", "Bosnia-Herzegovina": "BIH", "Cape Verde": "CPV", "Chinese Taipei": "TWN",
    "Cote d'Ivoire": "CIV", "Curacao": "CUW", "DR Congo": "COD", "England": "GBR",
    "Scotland": "GBR", "Wales": "GBR", "Northern Ireland": "GBR", "Hongkong": "HKG",
    "Korea, North": "PRK", "Korea, South": "KOR", "Kosovo": "XKX", "Neukaledonien": "NCL",
    "Palestine": "PSE", "Russia": "RUS", "Saint-Martin": "MAF", "Sint Maarten": "SXM",
    "Southern Sudan": "SSD", "St. Kitts & Nevis": "KNA", "St. Lucia": "LCA",
    "St. Vincent & Grenadinen": "VCT", "Tahiti": "PYF", "The Gambia": "GMB", "Turkey": "TUR",
    "Brasil": "BRA", "Iran": "IRN", "Venezuela": "VEN", "Bolivia": "BOL",
}
_cache: dict[str, str | None] = {}


def iso3(nome: str | None) -> str | None:
    if not nome:
        return None
    if nome in MANUAL:
        return MANUAL[nome]
    if nome not in _cache:
        try:
            _cache[nome] = pycountry.countries.lookup(nome).alpha_3
        except LookupError:
            _cache[nome] = None
    return _cache[nome]
