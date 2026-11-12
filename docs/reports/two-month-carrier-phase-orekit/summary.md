| Run | Days | Successful | Median fit RMS | Median run time |
|---|---:|---:|---|---:|
| Before: SPP position fit (previous engine) | 61 | 61 | 2.702 m (positions) | 135 s |
| After: carrier-phase OD (in-house engine) | 61 | 61 | 0.069 m (carrier phase) | 191 s |
| Final: carrier-phase OD on the Orekit engine | 16 | 16 | 0.066 m (carrier phase) | 366 s |

**3-D position error against POEORB (m), median over the scored days** (95th percentile in brackets):

| Horizon | Before | After | Final | Change (final vs before) |
|---|---:|---:|---:|---:|
| At cut-off | 1.57 (3.21) | 0.78 (2.15) | 0.95 (2.45) | -40 % |
| +1 h | 1.93 (4.48) | 1.26 (4.52) | 1.53 (3.21) | -21 % |
| +6 h | 9.62 (712) | 8.80 (713) | 10.18 (316) | +6 % |
| +1 day | 94.91 (4,211) | 92.74 (4,212) | 95.39 (3,607) | +1 % |
| +3 days | 1,023 (16,355) | 1,020 (16,361) | 1,023 (15,662) | -0 % |
| +7 days | 8,208 (42,008) | 8,205 (42,014) | 14,353 (38,911) | +75 % |
