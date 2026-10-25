#!/usr/bin/env python3
"""Builds app/src/main/assets/birds.tsv, the list of birds used by the station sheet.

Two ways to run it (Python 3.8+, no extra package):

  python3 tools/birds/build_birds.py                      # downloads from the Wikidata Query Service
  python3 tools/birds/build_birds.py --input export.csv   # or reads a result you exported yourself
                                                          # (query.wikidata.org -> "Download" -> CSV or JSON)

The query is in birds.sparql. Wikidata data is CC0. Keeps every species that has a French name. A second query (birds_mass.sparql) adds each bird's
body mass, from which the app estimates its flight speed.
If the download times out (the public service allows 60 s per query), use --input with an export
made from the web interface, or ask for the query to be split.
"""
import argparse
import csv
import json
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_OUTPUT = HERE.parent.parent / "app" / "src" / "main" / "assets" / "birds.tsv"
ENDPOINT = "https://query.wikidata.org/sparql"
HEARTBEAT_SECONDS = 10
USER_AGENT = "OpenCarbu-bird-list-builder/0.1 (https://github.com/; open-source Android app)"


def log(message):
    """Progress goes to the screen at once (flush), so a long wait never looks like a freeze."""
    print(message, flush=True)


def fetch_rows(query: str, endpoint: str = ENDPOINT, timeout: int = 300):
    """Runs the query on the Wikidata Query Service and returns a list of {column: value}.

    The service can take one to several minutes for ~11 000 birds. A message is printed every
    10 seconds so that the wait is visible, and the download size is shown as it arrives.
    """
    url = endpoint + "?" + urllib.parse.urlencode({"query": query, "format": "json"})
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"})

    started = time.monotonic()
    done = threading.Event()

    def heartbeat():
        while not done.wait(HEARTBEAT_SECONDS):
            log(f"  ...still waiting for Wikidata ({int(time.monotonic() - started)} s elapsed, limit {timeout} s)")

    log(f"Querying Wikidata ({endpoint}). This usually takes 1 to 3 minutes; please wait.")
    threading.Thread(target=heartbeat, daemon=True).start()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            log(f"Wikidata answered after {int(time.monotonic() - started)} s, downloading the result...")
            chunks, size = [], 0
            while True:
                chunk = response.read(64 * 1024)
                if not chunk:
                    break
                chunks.append(chunk)
                size += len(chunk)
                log(f"  {size / 1_000_000:.1f} MB received") if len(chunks) % 16 == 0 else None
            data = json.loads(b"".join(chunks))
    except urllib.error.HTTPError as error:
        detail = error.read(500).decode("utf-8", "replace").strip()
        sys.exit(f"Wikidata refused the request: HTTP {error.code} {error.reason}\n{detail}\n"
                 "If it says the query timed out, export the result from query.wikidata.org and use --input.")
    except (urllib.error.URLError, TimeoutError) as error:
        sys.exit(f"Could not reach Wikidata: {error}")
    finally:
        done.set()
    rows = rows_from_json(data)
    log(f"Received {len(rows)} result rows in {int(time.monotonic() - started)} s.")
    return rows


def rows_from_json(data):
    return [{key: cell["value"] for key, cell in binding.items()} for binding in data["results"]["bindings"]]


def read_rows(path: Path):
    text = path.read_text(encoding="utf-8-sig")
    if path.suffix.lower() == ".json":
        return rows_from_json(json.loads(text))
    return list(csv.DictReader(text.splitlines()))


def clean(value):
    """Trims, drops a disambiguation suffix such as ' (oiseau)', and returns None for empty text."""
    if not value:
        return None
    value = re.sub(r"\s*\((?:oiseau|bird|espèce|species)\)\s*$", "", value.strip(), flags=re.IGNORECASE)
    return value or None


def pick_name(common, label, title, scientific):
    """The vernacular name: common-name property, else label, else article title.
    A value equal to the scientific name is not a vernacular name (Wikidata falls back to it)."""
    for candidate in (common, label, title):
        candidate = clean(candidate)
        if candidate and candidate.lower() != scientific.lower():
            return candidate
    return None


def is_vernacular(french, english, scientific):
    """False for entries that are not really a bird's common name: a hybrid ("A x B"), a
    scientific name with its author and year, a placeholder ("Genus sp."), or a name that is
    just the scientific one (typically extinct genera, where French and English both fall back to it)."""
    if re.search(r"\b(1[5-9]|20)\d\d\b", french):
        return False
    if re.search(r"\s[x×]\s|\bsp\.|\bspp\.?(\s|$)", french):
        return False
    same = english and french.lower() == english.lower()
    genus = scientific.split()[0].lower() if scientific else ""
    return not (same and french.split()[0].lower() == genus)


# A bird weighs between about 2 g (bee hummingbird) and 150 kg (ostrich, extinct moa excepted).
# Anything outside is a unit mistake in Wikidata and is ignored.
MIN_MASS_KG, MAX_MASS_KG = 0.0015, 160.0


def masses_by_taxon(rows):
    """{taxon: median mass in kg} from the rows of birds_mass.sparql, ignoring impossible values."""
    values = {}
    for row in rows:
        try:
            mass = float(row.get("mass", ""))
        except (TypeError, ValueError):
            continue
        if row.get("taxon") and MIN_MASS_KG <= mass <= MAX_MASS_KG:
            values.setdefault(row["taxon"], []).append(mass)
    result = {}
    for taxon, masses in values.items():
        masses.sort()
        middle = len(masses) // 2
        result[taxon] = masses[middle] if len(masses) % 2 else (masses[middle - 1] + masses[middle]) / 2
    return result


def format_grams(kilograms):
    """'3.5' for 0.0035 kg; empty when unknown. Grams keep the file free of tiny decimals."""
    if kilograms is None:
        return ""
    grams = round(kilograms * 1000, 1)
    return str(int(grams)) if grams == int(grams) else str(grams)


def build_birds(rows, masses=None):
    """Merges the result rows (one taxon can appear several times) into one bird per taxon.
    [masses] is the result of masses_by_taxon; the mass (in grams) is the sixth column."""
    masses = masses or {}
    by_taxon = {}
    for row in rows:
        taxon = row.get("taxon")
        scientific = clean(row.get("sci"))
        if not taxon or not scientific:
            continue
        bird = by_taxon.setdefault(taxon, {"taxon": taxon, "sci": scientific, "fr": [], "en": [], "frTitle": None, "enTitle": None})
        for key, source in (("fr", ("frCommon", "frLabel", "frTitle")), ("en", ("enCommon", "enLabel", "enTitle"))):
            name = pick_name(row.get(source[0]), row.get(source[1]), row.get(source[2]), scientific)
            if name and name not in bird[key]:
                bird[key].append(name)
        bird["frTitle"] = bird["frTitle"] or clean(row.get("frTitle"))
        bird["enTitle"] = bird["enTitle"] or clean(row.get("enTitle"))

    result, seen = [], set()
    for bird in by_taxon.values():
        if not bird["fr"]:
            continue  # only birds with a French name
        french = sorted(bird["fr"], key=lambda n: (len(n), n))[0]  # shortest = the usual name
        if french.lower() in seen:
            continue  # two species sharing a French name: keep the first only
        seen.add(french.lower())
        english = sorted(bird["en"], key=lambda n: (len(n), n))[0] if bird["en"] else ""
        if not is_vernacular(french, english, bird["sci"]):
            continue
        result.append((french, english, bird["sci"], bird["frTitle"] or "", bird["enTitle"] or "",
                       format_grams(masses.get(bird["taxon"]))))
    result.sort(key=lambda b: b[0].lower())
    return result


def write_tsv(birds, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as out:
        out.write("# fr\ten\tscientific\tfrTitle\tenTitle\tmassGrams\n")
        out.write("# Generated by tools/birds/build_birds.py from Wikidata (CC0). Do not edit by hand.\n")
        for bird in birds:
            out.write("\t".join(field.replace("\t", " ").replace("\n", " ") for field in bird) + "\n")


def load_masses(args):
    """Masses are optional: without them every bird flies at the default speed. A failure here
    is reported but does not stop the build of the list itself."""
    try:
        if args.mass_input:
            log(f"Reading {args.mass_input}...")
            mass_rows = read_rows(args.mass_input)
        elif args.input:
            log("No --mass-input given: masses skipped (every bird will fly at the default speed).")
            return {}
        else:
            mass_rows = fetch_rows((HERE / "birds_mass.sparql").read_text(encoding="utf-8"), args.endpoint)
    except SystemExit as error:
        log(f"WARNING: masses unavailable, continuing without them. {error}")
        return {}
    masses = masses_by_taxon(mass_rows)
    log(f"{len(masses)} species with a usable mass.")
    if mass_rows and not masses:
        log("WARNING: no mass was between 1.5 g and 160 kg: the unit may not be kilograms.")
    return masses


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--input", type=Path, help="CSV or JSON result exported from query.wikidata.org")
    parser.add_argument("--mass-input", type=Path, help="CSV or JSON result of birds_mass.sparql, exported from query.wikidata.org")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--endpoint", default=ENDPOINT, help=argparse.SUPPRESS)  # for tests
    args = parser.parse_args(argv)

    if args.input:
        log(f"Reading {args.input}...")
        rows = read_rows(args.input)
    else:
        rows = fetch_rows((HERE / "birds.sparql").read_text(encoding="utf-8"), args.endpoint)
    masses = load_masses(args)
    log("Building the list...")
    birds = build_birds(rows, masses)
    if len(birds) < 100:
        sys.exit(f"Only {len(birds)} birds found: the result looks wrong, {args.output} was not changed.")
    write_tsv(birds, args.output)
    with_fr_page = sum(1 for b in birds if b[3])
    with_mass = sum(1 for b in birds if b[5])
    print(f"{len(birds)} birds written to {args.output} ({with_fr_page} with a French Wikipedia page, "
          f"{with_mass} with a known mass).")


if __name__ == "__main__":
    main()
