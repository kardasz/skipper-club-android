# Sailing Brief Areas — Data Sources

How the `brief_areas` records that power sailing briefs are sourced, generated,
and kept verifiable. One section per country; Croatia is the first.

## Principles

Sailing briefs are generated and served per technical area, so the areas are
reference data the product cannot run without — and they describe real waters,
so they must be **authoritative, never invented**. Every country follows the
same pipeline:

1. **Official source** — a national hydrographic office publication with
   verifiable, machine-readable boundaries.
2. **Generator** — `tools/briefareas` fetches the source, validates it (area
   count, unique codes, bounds sanity window), and renders deterministic seed
   SQL. No hand-edited geometry.
3. **Committed seed** — `db/seed/brief_areas_<country>.sql`, an idempotent
   upsert keyed by `brief_areas.code`. Reviewable in a PR, reproducible from
   the source, safe to re-apply.

```
official publication ──▶ go run ./tools/briefareas -country <cc> ──▶ db/seed/brief_areas_<cc>.sql ──▶ cli seed
```

## Workflow

| Task                                             | Command                                                                             |
| ------------------------------------------------ | ----------------------------------------------------------------------------------- |
| Regenerate a country's seed from the live source | `make brief-areas` (or `go run ./tools/briefareas -country hr`)                     |
| Detect drift against the live source             | `make brief-areas-check`                                                            |
| Apply all seeds to a database                    | `cli seed` (or a single file: `psql "$DATABASE_URL" -f db/seed/brief_areas_hr.sql`) |

The generator's output is deterministic, so `-check` fails exactly when the
official source changed (e.g. a new chart edition with adjusted limits) or the
committed file was edited by hand. Drift checks hit the external site — run
them deliberately, not in CI.

After applying a seed, the hourly scheduler picks the areas up automatically
(they are seeded `enabled`); briefs appear after the next slot hour (05:00,
12:00, 16:00 in each area's timezone). To generate immediately:
`cli sailing-briefs generate-now`.

## Croatia (HR)

**Source:** Croatian Hydrographic Institute (HHI) —
[nautical chart catalogue](https://www.hhi.hr/en/products-and-services/nautical-charts).
Croatia is divided into the coverage areas of the **13 active 100-series
coastal charts** (1:100 000). Each chart's detail page publishes its official
coverage limits (North/East/South/West); the generator converts them into an
envelope geometry, one `brief_areas` row per chart. Limits were first verified
on 2026-07-10.

### Why this division

- **Right-sized.** Each chart covers roughly 60–110 km of coast — about one
  weekly charter cruising ground (e.g. 100-21 "Šibenik - Split" is the
  Kornati/Trogir/Split circuit). Coarser official divisions (the three HHI
  radio-warning regions) are too generic for a local brief; finer ones (the 29
  MK-series small charts) triple generation cost with near-duplicate content.
- **Warning-addressable.** HHI radio navigational warnings — already ingested
  by this repository's alerts module (`cli alerts import`) — reference these
  chart labels in their `Charts` field, so a warning can be mapped to areas by
  a deterministic string match, never by guesswork.
- **Official and reproducible.** The limits are published per chart and can be
  re-fetched and diffed at any time (`make brief-areas-check`).

Chart 100-23 (Tremiti - Palagruža) is cancelled in the catalogue and therefore
absent; the numbering gap is intentional.

### Decisions

- **Flat model.** All 13 areas are independent top-level records
  (`parent_id = NULL`). No country-level fallback parent is seeded: it would
  generate generic country-wide content for a slot the specific areas already
  cover. Revisit only if coverage gaps show up in practice.
- **Envelope geometry (not clipped to Croatian waters).** The official
  rectangles give gap-free coverage of all Croatian waters including marinas,
  the shoreline, and open-water crossings. The trade-off: edge charts spill
  slightly across borders (100-15 toward Grado/the Slovenian coast, 100-28
  toward Budva) and rectangles include some hinterland — a user there receives
  the nearest coastal brief, which is harmless. Clipping to territorial waters
  was rejected for now because it creates offshore dead zones beyond 12 NM.
- **Overlap via unique priorities.** Chart footprints overlap by design; point
  lookup picks the covering area with the highest `priority`, and equal
  priorities would fall through to unspecified row order — so every area gets
  a unique value. The ordering encodes two rules: the two mostly-offshore
  charts (100-22 Jabuka - Vis, 100-24 Palagruža - Lastovo) rank below all
  inshore charts, so the coastal strip always resolves inshore while Vis,
  Palagruža, and open water stay covered; and among inshore charts the order
  is tuned so each chart's eponymous harbors — which sit on the seam with a
  neighbour — resolve to their own chart (Rovinj → 100-15, Mali Lošinj →
  100-17, Zadar → 100-20, Split → 100-21, Hvar → 100-26, Korčula → 100-27,
  Dubrovnik → 100-28; verified by point lookups against a seeded database).
  Other locations inside a seam resolve deterministically to one of the two
  relevant neighbouring charts (e.g. NW Pag to 100-17 rather than 100-19),
  which is inherent to overlapping official footprints and acceptable — both
  briefs describe those waters.
- **Names.** Official HHI chart titles, which are also the labels sailors and
  HHI warnings use.

### Seeded areas

| Code          | Name                | Chart                                                                                          | Priority |
| ------------- | ------------------- | ---------------------------------------------------------------------------------------------- | -------- |
| HR-HHI-100-15 | Grado - Rovinj      | [100-15](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2640)         | 22       |
| HR-HHI-100-16 | Pula - Kvarner      | [100-16](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2639)         | 14       |
| HR-HHI-100-17 | Lošinj - Molat      | [100-17](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2638)         | 21       |
| HR-HHI-100-18 | Rijeka - Kvarnerić  | [100-18/INT3473](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2637) | 13       |
| HR-HHI-100-19 | Silba - Pag         | [100-19](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2636)         | 12       |
| HR-HHI-100-20 | Dugi otok - Zadar   | [100-20](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/5999)         | 20       |
| HR-HHI-100-21 | Šibenik - Split     | [100-21](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2634)         | 19       |
| HR-HHI-100-22 | Jabuka - Vis        | [100-22](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2633)         | 2        |
| HR-HHI-100-24 | Palagruža - Lastovo | [100-24](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2631)         | 1        |
| HR-HHI-100-25 | Hvar - Lastovo      | [100-25](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2630)         | 15       |
| HR-HHI-100-26 | Brač - Hvar         | [100-26](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2629)         | 16       |
| HR-HHI-100-27 | Pelješac - Mljet    | [100-27](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2628)         | 17       |
| HR-HHI-100-28 | Dubrovnik - Budva   | [100-28](https://www.hhi.hr/en/products-and-services/nautical-charts/detail/pmid/2627)         | 18       |

The exact coordinates live in `db/seed/brief_areas_hr.sql`, each row annotated
with its source URL. All areas use the `Europe/Zagreb` timezone. Daily
generation load: 13 areas × 2 languages × 3 slots = 78 briefs.

### Licensing and safety

- The seed stores only published catalogue **metadata** (chart labels, titles,
  coverage limits), not chart imagery. Before production use, confirm HHI's
  [licensed-use terms](https://www.hhi.hr/en/documents/licensed-use-of-data).
- HHI states its warning e-service is not official safety-of-navigation
  information; sailing briefs must keep carrying their safety disclaimer and
  never present themselves as an official navigation product.

## Adding a new country (e.g. Greece, Italy)

1. **Find the authoritative division.** Prefer the national hydrographic
   office's published chart coverage or forecast/warning zones with verifiable
   coordinates (for Greece: the Hellenic Navy Hydrographic Service; for Italy:
   Istituto Idrografico della Marina). If no machine-readable official
   boundaries exist, derive areas from the Marine Regions database
   (territorial waters per country) rather than inventing shapes.
2. **Register the country** in `tools/briefareas`: add a fetch function
   against the official source and an entry in the `countries` registry (code,
   timezone, expected area count, sanity window). Keep the same reliability
   guards: integrity check against the source page, bounds validation,
   deterministic output.
3. **Generate and commit** `db/seed/brief_areas_<cc>.sql`, plus a country
   section in this document: source, division rationale, decisions, area
   table, licensing note.
4. **Verify**: point-lookup spot checks for well-known harbors, then apply to
   staging and review generated brief quality per area before enabling in
   production (areas can be enabled incrementally via the `enabled` flag).
