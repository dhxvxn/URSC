| Run | Days | Successful | Median fit RMS | Median run time |
|---|---:|---:|---|---:|
| Before: SPP position fit (previous engine) | 61 | 61 | 2.702 m (positions) | 135 s |
| After: carrier-phase OD (in-house engine) | 61 | 61 | 0.069 m (carrier phase) | 191 s |
| Final: carrier-phase OD on the Orekit engine | 61 | 61 | 0.072 m (carrier phase) | 280 s |

**3-D position error against POEORB (m), median over the scored days** (95th percentile in brackets):

| Horizon | Before | After | Final | Change (final vs before) |
|---|---:|---:|---:|---:|
| At cut-off | 1.57 (3.21) | 0.78 (2.15) | 0.89 (2.22) | -44 % |
| +1 h | 1.93 (4.48) | 1.26 (4.52) | 1.43 (4.68) | -26 % |
| +6 h | 9.62 (712) | 8.80 (713) | 8.61 (714) | -10 % |
| +1 day | 94.91 (4,211) | 92.74 (4,212) | 93.06 (4,213) | -2 % |
| +3 days | 1,023 (16,355) | 1,020 (16,361) | 1,000 (16,404) | -2 % |
| +7 days | 8,208 (42,008) | 8,205 (42,014) | 8,131 (42,118) | -1 % |
