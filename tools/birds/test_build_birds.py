"""Run with: python3 -m unittest discover tools/birds"""
import json
import tempfile
import unittest
from pathlib import Path

import build_birds as b


def row(taxon, sci, **kw):
    return {"taxon": taxon, "sci": sci, **kw}


class BuildBirdsTest(unittest.TestCase):
    def test_keeps_only_birds_with_a_french_name(self):
        rows = [
            row("Q1", "Aquila chrysaetos", frCommon="Aigle royal", enCommon="Golden eagle", frTitle="Aigle royal", enTitle="Golden eagle"),
            row("Q2", "Obscura nameless", enCommon="Nameless bird"),
        ]
        birds = b.build_birds(rows)
        self.assertEqual([("Aigle royal", "Golden eagle", "Aquila chrysaetos", "Aigle royal", "Golden eagle", "")], birds)

    def test_a_label_equal_to_the_scientific_name_is_not_a_name(self):
        rows = [row("Q1", "Obscura nameless", frLabel="Obscura nameless", frTitle="Obscura nameless")]
        self.assertEqual([], b.build_birds(rows))

    def test_label_then_title_are_used_when_there_is_no_common_name(self):
        self.assertEqual("Pie bavarde", b.pick_name(None, "Pie bavarde", "x", "Pica pica"))
        self.assertEqual("Pie bavarde", b.pick_name(None, "Pica pica", "Pie bavarde", "Pica pica"))

    def test_rows_of_one_taxon_are_merged_and_the_shortest_name_wins(self):
        rows = [
            row("Q1", "Pica pica", frCommon="Pie bavarde commune"),
            row("Q1", "Pica pica", frCommon="Pie bavarde", enCommon="Eurasian magpie"),
        ]
        self.assertEqual([("Pie bavarde", "Eurasian magpie", "Pica pica", "", "", "")], b.build_birds(rows))

    def test_entries_that_are_not_common_names_are_dropped(self):
        self.assertFalse(b.is_vernacular("Falco ferrugineus Lichtenstein, 1839", "", "Falco ferrugineus"))
        self.assertFalse(b.is_vernacular("Strix occidentalis x varia", "", "Strix occidentalis × varia"))
        self.assertFalse(b.is_vernacular("Hypsipetes sp.", "", "Hypsipetes cowlesi"))
        self.assertFalse(b.is_vernacular("Pyrrhura pallescens", "Pyrrhura pallescens", "Pyrrhura pallescens"))
        self.assertFalse(b.is_vernacular("Vegavis", "Vegavis", "Vegavis iaai"))
        self.assertTrue(b.is_vernacular("Brubru", "Brubru", "Nilaus afer"))
        self.assertTrue(b.is_vernacular("Ara chloroptère", "Red-and-green macaw", "Ara chloropterus"))

    def test_disambiguation_suffix_is_removed(self):
        self.assertEqual("Merle", b.clean("Merle (oiseau)"))

    def test_duplicate_french_names_are_kept_once_and_the_output_is_sorted(self):
        rows = [row("Q2", "B b", frCommon="Zèbre"), row("Q1", "A a", frCommon="Abeille"), row("Q3", "C c", frCommon="zèbre")]
        self.assertEqual(["Abeille", "Zèbre"], [x[0] for x in b.build_birds(rows)])

    def test_json_csv_and_tsv_roundtrip(self):
        data = {"results": {"bindings": [{"taxon": {"value": "Q1"}, "sci": {"value": "A a"}, "frCommon": {"value": "Aigle\troyal"}}]}}
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "r.json")
            src.write_text(json.dumps(data), encoding="utf-8")
            birds = b.build_birds(b.read_rows(src))
            out = Path(d, "birds.tsv")
            b.write_tsv(birds, out)
            lines = out.read_text(encoding="utf-8").splitlines()
            self.assertTrue(lines[0].startswith("#"))
            self.assertEqual(["Aigle royal", "", "A a", "", "", ""], lines[2].split("\t"))
            csv_path = Path(d, "r.csv")
            csv_path.write_text("taxon,sci,frCommon\nQ1,A a,Aigle royal\n", encoding="utf-8")
            self.assertEqual("Aigle royal", b.build_birds(b.read_rows(csv_path))[0][0])


    def test_masses_are_the_median_and_impossible_values_are_dropped(self):
        rows = [
            {"taxon": "Q1", "mass": "0.02"}, {"taxon": "Q1", "mass": "0.03"}, {"taxon": "Q1", "mass": "0.5"},
            {"taxon": "Q2", "mass": "20"},        # 20 kg of "mass": plausible for a swan-sized bird
            {"taxon": "Q3", "mass": "3500"},      # grams entered as kilograms: ignored
            {"taxon": "Q4", "mass": "abc"},
        ]
        masses = b.masses_by_taxon(rows)
        self.assertEqual({"Q1": 0.03, "Q2": 20.0}, masses)

    def test_the_mass_is_written_in_grams_as_the_sixth_column(self):
        rows = [row("Q1", "Pica pica", frCommon="Pie bavarde"), row("Q2", "B b", frCommon="Colibri")]
        birds = b.build_birds(rows, {"Q1": 0.2255, "Q2": 0.0035})
        self.assertEqual(["225.5", "3.5"], [x[5] for x in sorted(birds, key=lambda x: x[0] == "Colibri")])
        self.assertEqual("", b.format_grams(None))
        self.assertEqual("1200", b.format_grams(1.2))


if __name__ == "__main__":
    unittest.main()
