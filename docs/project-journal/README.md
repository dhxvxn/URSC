# Project journal

Day-wise record of the URSC build, the assessment of the supplied orekit-data files, and the
handoff.

| document | what it covers |
|---|---|
| **[DAY-WISE-LOG.md](DAY-WISE-LOG.md)** | chronological log (Oct 9 → Oct 13): what was built each day, findings, commits |
| **[OREKIT-DATA-ASSESSMENT.md](OREKIT-DATA-ASSESSMENT.md)** | file-by-file verdict on the supplied orekit-data (what helps, with measurements) |
| **[HANDOFF.md](HANDOFF.md)** | project state, how to build/run, current accuracy, caveats, roadmap |

## Headline finding (Day 4)

Real IERS Earth-orientation data (`finals2000A.all`, via `--eop-file`) is the highest-value
addition: it **halves the orbit-determination residual** (4.87 → 2.78 m on a 6 h fit) and
**lowers the 20 h prediction floor by 37 %** (224 → 141 m). See `results/eop_sweep_compare.png`.

## Contents

```
results/
  eop_sweep_compare.png     force-sweep held-out RMS, no-EOP vs real-EOP (the headline chart)
  sweep_noeop.csv           sweep results without EOP
  sweep_realeop.csv         sweep results with real EOP
  validate_vs_hours.csv     validate RMS vs arc length, no-EOP vs real-EOP
inputs/
  finals2000A.all           IERS EOP IAU-2000A (the one to use)   [orekit-data]
  finals.all                IERS EOP IAU-1980 (redundant)         [orekit-data]
  eigen-6s.gfc              EIGEN-6S gravity model (optional)      [orekit-data]
```

Data provenance: IERS / orekit-data (EOP, gravity model), redistributed for reproducibility.
The DE440 excerpt and the full orekit-data zip are not committed (see the assessment for why).
