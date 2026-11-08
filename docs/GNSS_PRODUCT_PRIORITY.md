# GNSS orbit and clock product selection

Requested priority, highest first:

1. IGS Final
2. IGS Rapid
3. IGS Ultra-rapid
4. Broadcast navigation

This order applies to the GPS satellite orbit and clock inputs used by SPP to
initialize Sentinel-3A orbit determination. These are GNSS satellite products,
not Sentinel-3A POEORB reference orbits. GNSSRD remains the measurement input.
The agreed merged architecture remains unchanged; product selection belongs
before the GPS satellite position and clock correction block.

## Forecast eligibility comes before priority

For a forecast cutoff, a candidate product must have been issued and available
by that cutoff and cover the required measurement epochs. Only then is priority
applied. A Final or Rapid product issued after cutoff must not displace a
cutoff-eligible lower-priority product. File validity dates alone do not establish
publication availability. Missing or unverified issuance metadata must not be
silently treated as proof of forecast eligibility.

Ultra-rapid observed and predicted segments must be distinguished and their
issue epoch recorded. For any forecast-time GNSS queries, use only the segment
available from a product issued by cutoff. The spacecraft propagation window
must still use cutoff-safe weather, Earth orientation and attitude assumptions.
POEORB remains separate scoring truth.

A retrospective comparison using later Final products is useful, but must be
labelled retrospective and kept separate from cutoff-safe forecast results.

## Quality and provenance

Within the eligible set, select the highest-priority supported product covering
the satellite and epoch. Fall back when coverage or required valid orbit/clock
information is unavailable. Reject invalid or unhealthy data. Record the selected
source and any fallback, per satellite and epoch; do not silently mix incompatible
orbit/clock conventions. Check time system, reference frame, units, satellite
identifiers, antenna/clock conventions and orbit interpolation support. Keep the
existing broadcast GPS LNAV path as the last fallback.

Each run should record product class, provider, filenames, hashes, issue time,
coverage, observed/predicted status, selected orbit and clock sources, and fallback
reasons. Explicit comparison modes may force a product tier, while preserving
cutoff eligibility for forecast-labelled runs.

## Implementation status and forthcoming inputs

This document records the requested selection policy. Automatic precise-product
selection and SP3/CLK integration are not implemented by this documentation
commit. The published two-month report remains the original broadcast baseline;
it has not been recalculated using precise products.

When product details arrive, establish supported SP3/CLK formats, providers,
issue-time metadata, matching orbit/clock availability and actual date coverage.
Then implement the resolver and adapters and test cutoff eligibility, priority,
missing coverage, clock compatibility and deterministic fallback before rerunning
the same daily cases. Final products must never enter forecast initialization
merely because they are present in the input directory.
