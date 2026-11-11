# CODE Final GNSS orbit and clock inputs

| Product | Files | Coverage | Sampling |
|---|---:|---|---|
| Final GNSS satellite orbits (`ORB.SP3.gz`) | 62 | 2 September-2 November 2024, daily | 5 minutes |
| Final GNSS satellite clocks (`CLK.CLK.gz`) | 62 | 2 September-2 November 2024, daily | 30 seconds |

The 124 original gzip files are archived byte-for-byte under `COD/OPS/2024/` (approximately 334 MiB compressed). [manifest.json](manifest.json) records every path, date, size and SHA-256. Complete paired coverage includes the previous-day boundary for the September 3-November 2 experiment.

Provider: CODE (AIUB), COD OPS Final products obtained from NASA CDDIS. These are GNSS satellite products used to derive Sentinel-3A fixes; they are not Sentinel-3A POEORB states. They are CODE analysis-centre Final products, not the IGS combined Final solution.

Final products are retrospective. Their filename epochs describe data coverage, not when they became available. Use only products known to be available by a forecast cutoff for operational testing; later Final products belong in explicitly retrospective comparisons.

## Verify archived bytes

From the repository root:

```sh
python3 - <<'PYVERIFY'
import hashlib,json
from pathlib import Path
m=json.loads(Path('data/gnss/products/final/manifest.json').read_text())
for item in m['inputs']:
    p=Path(item['path'])
    assert p.stat().st_size==item['bytes'],p
    assert hashlib.sha256(p.read_bytes()).hexdigest()==item['sha256'],p
print('All 124 Final input files match the manifest.')
PYVERIFY
```
