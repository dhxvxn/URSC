# Orbit estimation and prediction architecture

User-approved diagram: batch OD, physical force models and numerical propagation
are merged into one **Orbit Estimation and Prediction Engine**. This document
records the agreed structure; it does not claim that the algorithm has already
been refactored into one implementation class.

```mermaid
flowchart TD
    OBS["GNSSRD observations"]
    NAV["Broadcast navigation"]
    CUT["UTC cutoff and fitting-window settings"]
    OBS --> VALID["Input validation and time alignment"]
    NAV --> VALID
    CUT --> VALID
    VALID --> GPS["GPS satellite positions<br>and clock corrections"]
    GPS --> SPP["SPP: spacecraft position<br>and receiver clock"]
    SPP --> FIX["Position fixes, covariance<br>and solution status"]
    FIX --> FRAME["Transform positions and covariance<br>from ITRF to GCRF"]
    EOP["Earth-orientation parameters"] --> FRAME
    FRAME --> ENGINE["Orbit Estimation and Prediction Engine<br><br>Covariance-weighted batch OD<br>+ Physical force models<br>+ Numerical orbit propagation"]
    CUT --> ENGINE
    EOP --> ENGINE
    WEATHER["Past weather and<br>future forecast assumptions"] --> ENGINE
    STATIC["Gravity, tides, Sun/Moon<br>and spacecraft properties"] --> ENGINE
    ENGINE --> CURRENT["Estimated position, velocity<br>and covariance at cutoff"]
    ENGINE --> FUTURE["Future position, velocity<br>and formal covariance"]
    FUTURE --> SCORE["Separate accuracy evaluation"]
    TRUTH["POEORB reference"] --> SCORE
```

The cutoff is chosen before processing. Batch OD uses permitted observations to
estimate an orbit; inside the combined engine, its cutoff state starts numerical
propagation. The current-state block is a checkpoint output, not a second estimator.
POEORB is scoring-only for this GNSS-driven workflow. Covariance is formal uncertainty
under the assumptions in the estimator, not measured position error.

Excluded: sequential filtering, residual/consistency monitoring services,
outlier/maneuver investigation, recovery loops and planned-maneuver inputs.
Internal least-squares residual calculations are still necessary to solve SPP/OD.
The offline documentation assistant is separate from orbit computation.
