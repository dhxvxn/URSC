# Demo video — narration script

*A scene-by-scene narration for the demonstration video `GNSS-OD-demo.mp4`. The video is a silent
screen recording of the application (renamed build) with on-screen captions. Use this script either
to record a voice-over or to talk over the video live. Timings are approximate; the video runs about
**45–55 seconds**. Each scene lists the **on-screen caption**, a **voice-over** paragraph (read at a
calm pace), and what is **shown**.*

> Tip: the video is short and loops well. For a talk, play it once silently as an overview, then open
> the live application for the detailed demo (see the presentation script). To narrate live, pause on
> each scene and read the voice-over.

---

## Scene 1 — Title and the data catalogue · ~0:00–0:08
**On-screen caption:** *“GNSS Based Orbit Determination and Trajectory Prediction — running offline on
one computer.”* then *“Data: ~950 input files catalogued by content …”*
**Shown:** the Data tab; the coverage timeline; the scrolling file list.

**Voice-over:** *“This is a system that determines and forecasts a satellite's orbit from its own GPS
measurements, running entirely offline. It begins with data. Every one of roughly nine hundred and
fifty input files is recognised by its content — not its filename — and placed on this coverage
timeline: GPS observations in blue, the precise orbit and clock products in green and teal, the
reference orbits in purple, and attitude in pink. The software already knows each file's satellite,
time span, and type.”*

## Scene 2 — Run setup · ~0:08–0:16
**On-screen caption:** *“Run setup opens on the tested Sentinel-3A window; inputs already selected.”*
then *“Forecast length: 7 / 14 / 21 / 30-day presets, or any value.”*
**Shown:** the Run tab; the Forecast & output group expanding to reveal the preset buttons.

**Voice-over:** *“Setting up a run takes one glance. It opens on the tested Sentinel-3A window with the
correct inputs already selected. The forecast length offers one-click presets — seven, fourteen,
twenty-one, thirty days — or any value you type. Every setting carries an explanatory note, and every
force in the physics model is an independent switch.”*

## Scene 3 — The forecast on the 3-D globe · ~0:16–0:24
**On-screen caption:** *“Seven-day forecast on the 3-D globe; purple is the POEORB reference orbit.”*
**Shown:** full-view globe; the orbit sweeping as time advances.

**Voice-over:** *“Here is a seven-day forecast rendered on the three-dimensional globe. The bright
track is our predicted orbit; the purple track is ESA's precise reference, shown for comparison only
— it is never used as an input. As time advances, the satellite follows the predicted path around the
turning Earth.”*

## Scene 4 — Follow mode and the Sentinel-3A model · ~0:24–0:33
**On-screen caption:** *“Follow mode: the Sentinel-3A build flies nadir-pointing (model enlarged for
visibility).”*
**Shown:** the follow camera riding alongside the Sentinel-3A model over the limb of the Earth.

**Voice-over:** *“In follow mode the camera rides alongside the satellite. This is a recognisable
Sentinel-3A build, flown nadir-pointing — facing the Earth — exactly as the real spacecraft flies.
The model is enlarged here so it's visible at orbital scale.”*

## Scene 5 — The generic build · ~0:33–0:36
**On-screen caption:** *“Any other satellite is drawn with a generic build.”*
**Shown:** the globe with the generic satellite model.

**Voice-over:** *“Any satellite other than Sentinel-3A is drawn with a clean generic build, so the
tool works for any mission.”*

## Scene 6 — Comparing GPS product tiers · ~0:36–0:43
**On-screen caption:** *“Comparing GPS product tiers: error vs the POEORB truth orbit.”*
**Shown:** the comparison view; the overlaid error curves and the difference tables.

**Voice-over:** *“With one action the same case is run using each GPS product tier — broadcast,
ultra-rapid, rapid, and final — and their errors against the truth are overlaid. The striking result
is that they very nearly coincide: over a multi-day forecast, atmospheric drag dominates the error,
not the choice of GPS product.”*

## Scene 7 — The completed two-month campaign · ~0:43–0:52
**On-screen caption:** *“The completed two-month window: 61 daily cut-offs, all converged.”*
**Shown:** the Window tab; the summary tiles and the trend charts across the 61 days.

**Voice-over:** *“Finally, the whole validation campaign: sixty-one daily runs across two months of
real Sentinel-3A data. Every single day converged, with a median fit of about two-point-three metres,
and these charts show the trends in fit quality, GPS fixes, and forecast error. Everything you have
seen was produced on one computer, with no internet connection.”*

## Scene 8 — Close · ~0:52–end
**On-screen caption:** *(fades)*
**Voice-over (optional closing line):** *“From a satellite's own GPS recordings to a trustworthy orbit
and a forecast — entirely offline.”*

---

### Recording notes
- Read at ~150 words per minute; the whole voice-over is about 300 words, comfortably within the
  video length if you let the globe scenes breathe.
- If recording a voice-over to the file, the on-screen captions already carry the key phrases, so even
  a silent playback is self-explanatory.
- For a longer narration, expand Scene 3 and Scene 6 using the corresponding sections of the
  presentation script (the physics of drag, and the product-tier finding).
