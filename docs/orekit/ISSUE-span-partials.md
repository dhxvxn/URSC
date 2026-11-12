# Draft issue for the Orekit project — not yet submitted

**Title:** One-way GNSS measurements attribute clock/ambiguity partials to the first time span of a spanned driver.

**Orekit version:** 13.1.2 (Hipparchus 4.0.2), Java 17.

## Summary

With a span-split `ParameterDriver`, the measurement partial is reported against the *first*
span. That happens even when the measurement date lies in a later span. The affected drivers are:
* the receiver clock offset driver of an `ObservableSatellite`, split with `addSpanAtDate`;
* an `AmbiguityDriver`.

As a result, a `BatchLSEstimator` with per-epoch receiver clocks (or per-arc ambiguities) built
as spans has all-zero Jacobian columns for every span after the first. It fails with
"unable to solve: singular problem".

## Reproducer

`SpanPartialsReproducer.java` (in this folder):
1. It builds a `OneWayGNSSRange` and a `OneWayGNSSPhase` at 03:00:00.
2. It splits the receiver clock offset driver into five spans of 30 s around that date.
3. It prints `EstimatedMeasurement.getParameterDerivatives(driver, spanStart)` for each span.

Observed:

```
d/d Spanclock-offset-LEO0 [-inf]  = [2.997947647215693E8]
d/d Spanclock-offset-LEO1 [02:59:15] = [0.0]
d/d Spanclock-offset-LEO2 [02:59:45] = [0.0]   <- the span that contains 03:00:00
d/d Spanclock-offset-LEO3 [03:00:15] = [0.0]
d/d Spanclock-offset-LEO4 [03:00:45] = [0.0]
```

Expected: the partial c on span 2 (02:59:45 – 03:00:15) and zero on the others.

## Workaround used in this project

The two parameter types are declared as separate drivers instead of spans:
* one `ObservableSatellite` per epoch (all with propagator index 0, each with its own clock
  driver);
* one `AmbiguityDriver` per arc (through an `AmbiguityCache` keyed by arc).

See `org.ursc.trajectory.orekit.OrekitCarrierPhaseOd`.
