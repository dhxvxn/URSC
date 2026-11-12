<div class="keyfacts">
<span>Talk length <b>60–120 min</b></span>
<span>Audience <b>scientists / engineers</b></span>
<span>Test satellite <b>Sentinel-3A</b></span>
<span>Includes <b>live demo</b></span>
</div>

> **What this document is.** A complete speaker's script and demo guide for presenting the project
> *GNSS Based Orbit Determination and Trajectory Prediction* to a technical audience. It contains
> near-verbatim talking points, the reasoning behind every design and modelling choice, a
> step-by-step live-demo sequence, the experimental results, an honest account of every problem
> encountered (including why some runs did not converge and how that was fixed), and a prepared
> question-and-answer section. Two timing plans are given — a 60-minute version and a 120-minute
> version. Text you can say almost word-for-word is marked **“Say:”**; stage directions are marked
> **[Show]** or **[Do]**.

---

# How to use this script

- **Bold “Say:” lines** are suggested spoken wording — adapt to your voice.
- **[Show]** indicates a screenshot or the live application to display; **[Do]** indicates an action
  to perform in the demo.
- Each section header gives an approximate **duration** for the 90-minute default; the two timing
  tables at the end give the 60- and 120-minute variants.
- **Key numbers** you should know by heart are collected in Appendix B. The **anticipated questions**
  in Appendix A are the most valuable preparation — read them twice.
- Have the application already running and a browser open on the Run tab before you begin, plus the
  report PDF open as a backup in case the live demo misbehaves (Appendix E).

---

# Part 0 — The one-paragraph summary (memorise this)

**Say:** *“This project is a single, dependency-free Java application that takes the GPS
measurements a low-Earth-orbit satellite records about itself, reconstructs the satellite's true
orbit, and forecasts where it will be over the coming days — writing the result in the international
CCSDS standard. It runs entirely offline, on one computer, which is a hard requirement for secure
space-operations facilities. We validated it on two months of real data from ESA's Sentinel-3A
satellite: sixty-one daily runs, every one of which converged, with the determined orbit matching
ESA's own precise reference to within about two metres.”*

That paragraph is your anchor. Everything else expands one clause of it.

---

# Part 1 — Opening and framing · ~5 min

**[Show]** Title slide / the application's Results view with the globe turning.

**Say:** *“Good [morning]. Over the next [90] minutes I'll walk you through a system that answers two
deceptively simple questions about a satellite: where is it now, and where will it be next. I'll
cover the problem and why it matters, the data we used, the software architecture and why it's built
the way it is, the physics and the mathematics — including why we chose each model over the
alternatives — a live demonstration, our results on two months of real data, and, importantly, the
problems we hit and how we solved them, because that is where most of the engineering actually
happened.”*

**Say:** *“I'll keep the jargon honest: every specialised term I use, I'll define the first time.
Please interrupt with questions; there's also a dedicated Q&A at the end.”*

**Framing device.** Put one sentence on screen and return to it: *“From a satellite's own GPS
recordings to a trustworthy orbit and a forecast — entirely offline.”*

---

# Part 2 — The problem and why it matters · ~8 min

**Say:** *“A satellite in low Earth orbit — a few hundred kilometres up — travels at about seven and
a half kilometres per second and circles the Earth roughly every hundred minutes, about fifteen
times a day. Sentinel-3A, our test satellite, sits near eight hundred and fifteen kilometres.”*

**Say:** *“To do anything useful with such a satellite you must know its orbit precisely. If you're
imaging the Earth, you need to know exactly where the camera was pointing. If you want to download
data, you need to predict the few-minute windows when the satellite is above your ground station. If
you're worried about collisions — and in today's crowded orbits everyone is — you need an accurate
forecast of where it will be, and an honest estimate of how uncertain that forecast is.”*

**Say:** *“Two tasks, then. **Orbit determination** — reconstructing the true orbit from
measurements — and **trajectory prediction**, or **propagation** — simulating the physics forward.
This project does both, from the satellite's own GPS data.”*

**Why from GPS?** **Say:** *“Modern LEO satellites carry a GPS receiver, exactly like your phone but
in orbit, and record what they hear. That recording is a free, self-contained measurement — no
ground radar network required. Our job is to turn those raw recordings into an orbit.”*

**The hard constraint.** **Say:** *“And there's a constraint that shapes the entire design: it has to
run on a single computer with no internet connection. Many operational and defence facilities are
'air-gapped' for security. So everything — the 3-D globe, the maps, the documentation, even the help
search — is bundled inside the program. Nothing is ever fetched online.”*

---

# Part 3 — A short primer (optional, skip for an expert audience) · ~8 min

*Use this only if the room is mixed. For an orbital-mechanics audience, compress to 60 seconds and
move on.*

- **Orbit = continuous free-fall.** The cannonball analogy: fire it fast enough and the Earth curves
  away as fast as it falls.
- **Two ways to describe an orbit:** a **state vector** (position + velocity, six numbers), or the six
  **Keplerian elements** (size, shape, tilt, and three orientation angles). We convert losslessly.
- **Reference frames:** physics is done in an inertial frame fixed to the stars (GCRF/J2000);
  geography is in an Earth-fixed frame (ITRF). Converting between them needs the Earth's orientation
  model — precession, nutation, polar motion.
- **Time matters:** light travels 300 m per microsecond, so clocks must be handled carefully — TAI,
  UTC, GPS time, each defined.

**Say:** *“I'll define anything else as it comes up. Let's get to the data, because the data is where
this becomes real.”*

---

# Part 4 — The data: “the G data” · ~12 min

**Say:** *“When I say 'the data', I mean primarily **GNSS data** — Global Navigation Satellite System
data, of which GPS is the American instance. Let me break down exactly what we feed in and where it
comes from.”*

**4.1 The measurement — GNSSRD.** **Say:** *“The mandatory input is the satellite's own raw GPS
observations. For Sentinel-3 these come in files called GNSSRD, in the standard RINEX format. Inside
is, for each epoch — typically every ten or thirty seconds — the list of GPS satellites in view and
the **pseudorange** to each: the distance the receiver measured from the signal's travel time. It's
called a *pseudo*-range because the receiver's own clock error is baked into every measurement, and
we solve for that clock error too.”*

**[Show]** The Data tab — point at the GNSS observations rows and the coverage timeline.

**4.2 Where were the GPS satellites? — the products.** **Say:** *“A measured distance to a GPS
satellite is useless unless you know where that GPS satellite was and what its clock read at the
moment of transmission. That comes as **GPS products**, and here's the key trade-off: accuracy
versus latency.”*

| Product | Source | Accuracy | Latency |
|---|---|---|---|
| **Broadcast** | the GPS satellites themselves | ~1–2 m | immediate (real time) |
| **Ultra-rapid** | IGS (half predicted, half observed) | ~5 cm | hours |
| **Rapid** | IGS | ~2.5 cm | ~1 day |
| **Final** | IGS | ~2 cm | ~1–2 weeks |

**Say:** *“IGS is the International GNSS Service, a global science collaboration. Its analysis centres
— CODE in Switzerland, ESA, Canada's EMR, Germany's GFZ — each publish products, as two files: an
SP3 file with the satellites' positions and a CLK file with their clocks. Our system automatically
prefers the most accurate product that was actually available at the chosen time, falling back per
satellite and per epoch.”*

**4.3 The reference — POEORB.** **Say:** *“To know whether our answer is any good, we compare against
ESA's own precise reconstructed orbit for Sentinel-3, called POEORB. This is crucial: POEORB is used
**only to score** our result. It is never an input. If we fit to the truth, we'd learn nothing.
Keeping it strictly out of the computation is what makes the validation honest.”*

**4.4 The supporting data.** Briefly: **PROQUA** attitude (which way the satellite points, used only
for advanced shape modelling); **EOP** (Earth-orientation corrections); **space-weather** files
(solar and geomagnetic activity, which drive atmospheric drag — more on that later).

**4.5 The campaign dataset.** **Say:** *“Everything is validated on a real two-month window:
Sentinel-3A, the 2nd of September to the 2nd of November 2024. The bundled dataset holds, for that
window, sixty-two days of GPS observations, sixty-three attitude files, sixty-three reference orbits,
and GPS products from four analysis centres across all four accuracy tiers — about nine hundred and
fifty catalogued files in total, each with a cryptographic checksum so every result is traceable to
its exact inputs.”*

**Transition.** **Say:** *“So that's what goes in. Now, how is the software that consumes it built —
and why in the languages and components we chose?”*

---

# Part 5 — Architecture and language choices · ~12 min

**[Show]** The architecture diagram (report §10) or draw the three layers.

**Say:** *“The system is one Java application with **zero external runtime libraries** — the entire
physics core is plain, auditable Java. That 'zero dependencies' point matters for two reasons:
security auditing in an air-gapped facility, and the ability to ship a self-contained bundle. It's
organised in three layers.”*

**Layer 1 — the engine.** *“The numerical core: readers for every input format, the GPS positioning,
the orbit determination, the switchable force models, the integrator, and the standard-format
writer.”*

**Layer 2 — the data catalogue.** *“It scans input files and recognises each by its **content**, not
its filename — which, as you'll see later, caught a real data error. It records coverage, satellite,
and a SHA-256 checksum, and selects the right files for each run.”*

**Layer 3 — the interface.** *“A small local web server that serves a browser interface. The browser
is only the window; the server binds to localhost only and nothing leaves the machine.”*

## Why each programming language and component

**Say:** *“People often ask why this stack. Each choice is deliberate.”*

- **Java 17 for the engine and server.** *“Java gives us strong static typing — the compiler catches
  a whole class of errors before anything runs, which matters for numerical code. It's fast enough
  for heavy integration thanks to the just-in-time compiler. It's write-once-run-anywhere: the same
  jar runs on Linux, Windows or macOS with no recompilation, which is important for delivering to
  whatever facility needs it. It has a mature numerical tradition, and the de-facto reference library
  in this field, Orekit, is Java — so published algorithms map directly onto our code. Finally, Java
  lets us reach **zero third-party runtime dependencies** and still build a self-contained installer
  that carries its own Java runtime, so target machines need nothing pre-installed.”*

- **The web server is the JDK's built-in HTTP server** (`com.sun.net.httpserver`), **not** a
  framework. *“We deliberately avoided Spring or any web framework. The built-in server is tiny,
  dependency-free, and more than sufficient. It binds to 127.0.0.1 only — part of the security model
  — and streams live progress to the browser with server-sent events.”*

- **JavaScript (plain ES modules) + HTML + CSS for the interface.** *“The browser is the one place
  where only JavaScript runs, so the interactive front end is JavaScript. We use **three.js**, a
  WebGL library, for the 3-D globe, and the HTML canvas directly for the 2-D ground-track map and the
  diagnostic plots. Crucially there is **no build step and no front-end framework** — it's plain
  modules — which keeps the whole thing inspectable and dependency-free, consistent with the
  air-gapped philosophy.”*

- **Maven for the build** (an XML project file): standard Java compile/test/package.

- **Packaging** uses Java's own `jlink` and `jpackage` to produce a minimal runtime and a native
  bundle, with small PowerShell and Bash scripts for Windows and Linux.

- **Hand-written parsers** for every data format (RINEX, SP3, CLK, the POEORB XML, space-weather
  CSV). *“We wrote the readers ourselves rather than pulling in libraries — again, to keep
  dependencies at zero and the behaviour fully auditable.”*

- **Development tooling that is not shipped:** *“For producing documentation and media I used Node.js
  with Playwright to drive a headless browser, ffmpeg to assemble video, and pandoc for the reports.
  These are authoring tools; none of them is part of the delivered product.”*

**Say:** *“In one line: Java where correctness and portability matter, JavaScript only where the
browser forces it, and nothing else in the runtime at all.”*

---

# Part 6 — The physics, and why these models · ~20 min

**[Show]** The Run tab → Perturbations group, toggling forces, and the equation of motion slide.

**Say:** *“Forecasting the orbit means solving Newton's second law for the satellite: its
acceleration is the sum of accelerations from every force that acts on it. Each force is an
independent module you can switch on or off, which lets you see exactly what each one contributes.
Let me go through them, and — because this is the interesting part — why we chose each model rather
than an alternative.”*

**6.1 Gravity — and why EGM96 to degree 70.** *“The dominant force is Earth's gravity, but the Earth
isn't a point mass or even a perfect sphere — it bulges at the equator and has density variations. We
represent its field as a **spherical-harmonic expansion**, a sum of standard patterns indexed by
degree and order. We use the **EGM96** model to degree and order 70.”*

*“Why 70, and why EGM96 rather than the newer, much larger EGM2008? Two reasons. First, physics: at
eight hundred kilometres altitude the fine detail of the gravity field is smoothed out, so the signal
essentially saturates by around degree seventy — going higher buys you corrections that are smaller
than our other error sources, like drag and Earth-orientation. Second, cost and licensing: the
computational cost of the harmonic sum grows with the square of the degree, so EGM2008 at two
thousand degrees would be orders of magnitude slower for no measurable benefit here, and EGM96 ships
license-clean. We made the degree a user setting, so you can lower it to twenty or thirty for a quick
look, or raise it if you ever fly lower.”*

**6.2 Atmospheric drag — the hard one, and the density models.** *“This is the single largest source
of forecast error, so it gets the most attention. Even at eight hundred kilometres there's a trace
atmosphere, and ploughing through it at seven and a half kilometres per second slowly lowers the
orbit. The drag acceleration is one-half rho v-squared times the ballistic coefficient. Every term is
known reasonably well except **rho**, the air density — and at these altitudes density can swing by a
factor of ten with solar activity, which itself isn't predictable days ahead. So the quality of the
density model, and the unpredictability of space weather, dominate the forecast error.”*

*“We offer four density models, and the choice matters:”*

- *“**Exponential** — density just falls off with height. Static, crude, fast; fine for a sanity
  check, useless for accuracy.”*
- *“**Harris–Priester** — adds the day–night 'diurnal bulge': the atmosphere puffs up on the sunlit
  side. Better, still no response to solar activity.”*
- *“**NRLMSISE-00** — our default. It's an **empirical** model fitted to decades of real measurements
  — satellite drag, mass spectrometers, ground radar — and it responds to solar and geomagnetic
  activity. It's the community standard, and our implementation reproduces the model's official
  reference case to seven significant figures. That's why it's the default: realism plus a verifiable
  pedigree.”*
- *“**JB2008**, Jacchia–Bowman 2008 — another high-fidelity empirical model, particularly good during
  geomagnetic storms; we offer it as an alternative.”*

*“Why not NRLMSIS 2.0 or DTM2020, the very newest models? Purely licensing — their coefficient files
aren't redistributable, so we deliberately left them out rather than ship something we can't license
cleanly. And we expose all the models precisely so a user can compare them rather than take our word
for it.”*

**The space-weather drivers.** *“The high-fidelity models need to know how active the Sun is, via two
indices: **F10.7**, the Sun's radio flux at ten-point-seven centimetres, which proxies the
ultraviolet that heats the upper atmosphere; and **Ap**, a geomagnetic index that captures storms.
We read these from a space-weather file. The honest caveat: because nobody can predict F10.7 and Ap
precisely days ahead, even a perfect density model leaves an irreducible forecast error — which is
exactly what we see in the results.”*

**6.3 Solar radiation pressure.** *“Sunlight carries momentum and pushes on the satellite, including a
realistic Earth-shadow model with full and partial shadow. Small but steady.”*

**6.4 Sun and Moon third-body gravity.** *“The Sun and Moon pull on the satellite; because they pull
differently on the satellite than on the Earth, the difference perturbs the orbit. We use compact
analytical positions by default — accurate to a fraction of a degree, which is ample for a
perturbing force — and can optionally load NASA's high-precision DE440 ephemeris.”*

**6.5 Solid and ocean tides.** *“The same lunar and solar pull that raises ocean tides also flexes the
solid Earth and moves ocean water, slightly changing Earth's gravity moment to moment. We include
both, to the international IERS-2010 conventions.”*

**6.6 General relativity.** *“Newton is superb but not exact; we add Einstein's Schwarzschild
correction and the smaller frame-dragging term from Earth's rotation. For completeness and
correctness.”*

**6.7 Earth albedo and infrared.** *“Sunlight reflected off the Earth and heat radiated by the Earth
also push on the satellite — a small cousin of solar pressure.”*

**Say:** *“The design principle across all of these: every force is a pluggable module behind one
interface, and every parameter is data, not code. You dial fidelity up or down by configuration, and
you can quantify what each force is worth — which is both good science and good engineering.”*

---

# Part 7 — The mathematics and algorithms · ~15 min

**Say:** *“Four computational stages turn raw measurements into a scored forecast. I'll say why we
chose each method, because the alternatives are reasonable and you'll ask.”*

**7.1 Single-point positioning.** *“At each epoch we have several pseudoranges and four unknowns —
three position coordinates and the clock error. It's over-determined and noisy, so we solve by least
squares: the position and clock that best fit all the pseudoranges. Each epoch yields one position
'fix'. Over a six-hour arc sampled every five minutes, that's about seventy-three fixes.”*

**7.2 Numerical integration — why Dormand–Prince 5(4).** *“To propagate, we integrate the equation of
motion numerically — stepping forward and re-evaluating every force at each step, which is called
Cowell's method. We use a **Dormand–Prince 5(4)** Runge–Kutta integrator. Why this one? It computes
each step to both fifth- and fourth-order accuracy, and the difference gives a free error estimate,
which it uses to **adapt the step size** — small steps where the dynamics change quickly, large steps
where they're smooth. Compare the alternatives: a fixed-step RK4 either wastes effort or silently
loses accuracy; classical multistep methods like Adams or Gauss–Jackson are very efficient for smooth
orbits but need a separate starter and struggle with the discontinuities our force model has — the
satellite entering and leaving the Earth's shadow. Dormand–Prince is self-starting, robust to those
discontinuities, and it's the standard choice in the field. For very long, multi-month 'lifetime'
runs we also provide a semi-analytical DSST propagator that takes giant steps.”*

**7.3 Orbit determination — why batch least squares, not a Kalman filter.** *“The position fixes don't
lie on any single perfect orbit. Orbit determination finds the one physically consistent orbit that
best fits them all. We use **batch least squares** with Gauss–Newton iteration: guess an initial
state, propagate it, measure the residuals, compute how each residual responds to each component of
the initial state — the Jacobian — and solve for the correction that most reduces the squared
residuals; then repeat.”*

*“Why batch rather than a Kalman or unscented filter? For a single fixed arc of data, batch is the
natural and robust choice: it uses all the data at once and produces a clean covariance at one epoch,
with no process-noise tuning to get wrong. A Kalman filter shines for sequential, real-time streaming
estimation; that's a different operating mode, and a sensible future extension, but it adds tuning
knobs we don't need here. One more design decision: we compute the Jacobian by **finite differences**
— perturb each initial component, re-propagate, difference. That's a little more expensive than
analytic partials, but it makes the orbit determination **completely independent of the force model**.
You can switch on ocean tides or change the drag model and the estimator just works, with no new
derivatives to derive. For a system whose whole point is swappable physics, that was the right
trade.”*

*“We report the fit quality as the **post-fit RMS** — the root-mean-square residual in metres — and a
**variance factor** that checks whether our assumed measurement noise was realistic.”*

**7.4 Covariance — the uncertainty.** *“A prediction without an uncertainty is half an answer. The
orbit determination yields a **covariance** — a matrix of the uncertainty and the correlations
between directions — at the cut-off. To report it along the forecast we propagate it with the
**state-transition matrix**, again by finite differences. It costs about one extra propagation per
estimated parameter, so it's optional and off in the fast default profile; when on, we show it as an
uncertainty ellipsoid or as radial/along-track/cross-track error bands.”*

---

# Part 8 — Live demonstration · ~20 min

*Keep the browser ready. If anything stalls, switch to the screenshots in the report (Appendix E).*

**8.1 The data catalogue.** **[Do]** Open the **Data** tab. **Say:** *“Here's the catalogue. Every one
of these ~950 files was recognised by its content, not its name, and placed on this coverage
timeline — blue is GPS observations, gold is broadcast navigation, purple is reference orbits, pink
is attitude. Notice it already knows each file's satellite, time span, and product class.”*

**8.2 Setting up a run.** **[Do]** Open the **Run** tab. **Say:** *“It opens on the tested Sentinel-3A
window with the inputs already selected. The forecast length has one-click presets — seven, fourteen,
twenty-one, thirty days — and the cut-off time can be filled straight from the data. Every single
setting has an information note explaining what it does and how to choose it.”* **[Do]** Expand
**Perturbations** and toggle a force. **Say:** *“And here's the modular physics — every force is an
independent switch with a model choice.”*

**8.3 A single run.** **[Do]** Press **Run** (or open a pre-computed run to save time). **Say:** *“A
run does single-point positioning, then batch orbit determination, then the seven-day forecast. On a
normal machine it's a couple of minutes; I've pre-computed one so we don't watch a progress bar.”*

**8.4 Results — the globe and the interactive map.** **[Do]** Open **Results** on a finished run.
**Say:** *“Here's the forecast on the 3-D globe and, beside it, the 2-D ground track — the path of the
point directly beneath the satellite. Watch this: as I move the mouse over the map, it scrubs time
across the globe and the plots together, and the latitude, longitude, altitude, and the error against
the reference orbit update live. The purple curve is ESA's precise reference, shown for comparison
only.”* **[Do]** Toggle a couple of map layers.

**8.5 The satellite models.** **[Do]** Click **Follow satellite**. **Say:** *“This is Sentinel-3A
flown nadir-pointing — facing the Earth — along the predicted trajectory. Any other satellite gets a
clean generic build.”* **[Do]** Switch the model to generic to show it.

**8.6 The whole campaign.** **[Do]** Open the **Window** tab. **Say:** *“This is the entire two-month
campaign — sixty-one daily runs — summarised: every day issued a forecast, the median fit was about
two-point-three metres, and these charts show the trends in fit quality, GPS fixes, and forecast
error.”*

**8.7 Comparing GPS products.** **[Do]** Trigger **Compare product tiers** (or open the pre-computed
comparison). **Say:** *“One click runs the same case with broadcast, ultra-rapid, rapid, and final
products and overlays their error against the truth. I'll come back to what this reveals — it's one
of the more interesting findings.”*

**8.8 Outputs and offline help.** **[Do]** Show the OEM / KML / report buttons, and the Help tab.
**Say:** *“Every run exports the CCSDS standard ephemeris, plus Google-Earth and Cesium tracks and a
printable report. And the Help here answers questions from the bundled documentation entirely
offline, citing its sources — it physically cannot reach the internet.”*

---

# Part 9 — Results and validation · ~12 min

**[Show]** The results table and the product-comparison chart.

**Say:** *“Sixty-one daily cut-offs, frozen settings — six-hour arc, seven-day forecast, EGM96 70×70,
NRLMSISE-00 drag. The headline numbers:”*

| Measure | Result |
|---|---|
| Forecasts issued | **61 of 61** (all converged; none flagged) |
| Orbit-fit post-fit RMS | median **2.35 m** (1.85–4.38 m) |
| Error vs POEORB at the cut-off | 2.17 m (8 Oct), 0.48 m (16 Oct) |
| Error vs POEORB after 1 day | 268 m (7 Oct), 28.7 m (15 Oct) |

**Say:** *“The determined orbit matches ESA's precise reference to a couple of metres at the cut-off.
The forecast then degrades — and the way it degrades is the interesting science.”*

**The product-tier finding.** **Say:** *“We ran the same case with every GPS product tier. At the
cut-off they differ by only a few metres — broadcast two-point-one, final two-point-four-five metres
of fit. But over the seven-day forecast the trajectories drift by about **twenty kilometres** through
atmospheric-drag uncertainty, which completely dwarfs the few-metre differences between products. The
practical conclusion — and it's a useful one — is that for a multi-day LEO forecast, the drag model
and the forecast horizon dominate the error, not which GPS product you used. The product choice
matters most for the instantaneous orbit, not the multi-day prediction. That finding is exactly why
we invested in a high-fidelity, switchable drag model and in honest uncertainty reporting, rather
than chasing the last centimetre of GPS product accuracy.”*

**Independent checks.** **Say:** *“Beyond the campaign, more than a hundred and twenty automated tests
verify the mathematics — orbit conversions, energy conservation, the J2 nodal-regression formula, the
leap-second table, NRLMSISE-00 against its official case to seven significant figures, recovery of a
known orbit from synthetic data, and round-trips of the standard output format. And the propagator is
checked directly against real Sentinel-3A precise orbits.”*

---

# Part 10 — Problems, non-convergence, and how we solved them · ~15 min

*This is the most credible part of any engineering talk. Tell it as a story.*

**Say:** *“I want to be candid about what went wrong, because fixing these is where the real work was —
and one of them is the direct answer to 'why did some runs not converge?'”*

**10.1 Why some runs did not converge — stale broadcast navigation.** **Say:** *“Early on, certain
daily runs either failed to converge or converged with a badly inflated fit — eight to twelve metres
instead of two — and the system flagged them as possible satellite manoeuvres. The cause was subtle.
A six-hour measurement arc that starts before midnight crosses into the next day, and the broadcast
navigation comes in daily files. We were treating each daily file as a separate source, so for the
first couple of hours after midnight the software was still using the **previous day's** navigation
messages — stale positions for the GPS satellites. Stale transmitter positions mean biased
pseudorange residuals; biased residuals mean the single-point positioning fixes are pulled off, and
the orbit determination then either can't reconcile them — non-convergence — or converges to a
distorted orbit with a large residual jump, which tripped the manoeuvre detector.”*

**Say:** *“The fix was to **merge all the broadcast files into a single source** and, for every GPS
satellite and every epoch, pick the navigation message whose reference time is genuinely nearest —
across the union of files, not within one file. After that, the post-fit RMS dropped back to about two
metres, the false manoeuvre flags disappeared, and — to your question — **all sixty-one days
converged**. We added a regression test so it can't come back.”*

**Say:** *“So the honest, precise answer to 'why didn't some data converge' is: it wasn't the data
being bad, it was our software serving the wrong GPS satellite positions at day boundaries, which
corrupted the input to the estimator. Once the measurements were paired with the correct navigation,
convergence was universal.”*

**10.2 A reference orbit of the wrong satellite.** **Say:** *“A file named for Sentinel-3A actually
contained Sentinel-3B data — its own internal header said so. Because we compare the forecast against
the reference, two days showed an absurd thirteen-thousand-kilometre 'error'. The forecasts
themselves were fine; the reference was wrong. The fix is why the catalogue reads content, not
filenames: it now takes the satellite identity from each reference file's header and only compares
like with like.”*

**10.3 Rejected precise clocks.** **Say:** *“Our main precise products are from CODE, and their
high-rate clock files put one character of the header one column to the right of where the standard
specifies. Our reader was strict and rejected all sixty-nine of them — which would have silently
prevented the precise Final orbits from being used at all. We relaxed the reader to accept either
column, recovered all sixty-nine files, and covered it with a test.”*

**10.4 A Windows-only build failure.** **Say:** *“The build passed on Linux but failed on Windows — in
the test phase, not the code. Three tests created symbolic links to stage their input files, and
Windows refuses symbolic links without a special privilege. The fix: the tests fall back to copying
the file when a link isn't permitted. Portable builds matter when you deliver to whatever OS the
facility runs.”*

**10.5 Incomplete downloads.** **Say:** *“Some product downloads were interrupted and left truncated
files. Rather than let them corrupt a run, the catalogue quarantines anything it can't fully parse and
excludes it from coverage.”*

**10.6 Performance and rendering.** **Say:** *“Two honest performance notes. A full-fidelity run is
about two minutes and the sixty-one-day window runs them sequentially — about two hours end to end —
because the underlying library keeps some Earth-orientation and ephemeris state process-wide, so true
parallel execution needs that state isolated first; that's scoped as future work. And the development
machine had no graphics card, so the media you'll see were rendered frame by frame with the satellite
model enlarged for visibility.”*

**Say:** *“Every one of these came with a regression test, so the system got more robust with each
problem, not more fragile.”*

---

# Part 11 — Limitations, honesty, and future work · ~8 min

**Say:** *“Where would I push this next, and what should you not over-claim?”*

- **Real-time products.** A genuine forecast at the cut-off can only use broadcast and the predicted
  half of ultra-rapid; Final and Rapid arrive one to two weeks later and are for retrospective
  reconstruction. That's physics, not a bug.
- **The seven-day drift.** About twenty kilometres after seven days, dominated by unpredictable solar
  activity. Shortening the horizon or assimilating updated space weather is the lever.
- **Parallelism.** Isolating the process-wide frame/ephemeris state would let the window run across all
  CPU cores — hours to minutes — at full accuracy. Clear next step.
- **A Kalman/sequential mode** for real-time operations, alongside the batch estimator.
- **More atmosphere models** (JB2008 is in; NRLMSIS 2.0 / DTM2020 await licensing).
- **EOP and high-precision ephemerides** are optional inputs; supplying them tightens the frame
  conversion from metre to centimetre level.

**Say:** *“The design makes all of these additions local — a new force, atmosphere, integrator, or
estimator is one module behind one interface. Nothing else has to change.”*

---

# Part 12 — Closing · ~3 min

**Say:** *“To close where I began: this is a single, dependency-free, offline Java application that
turns a satellite's own GPS recordings into a trustworthy orbit and a forecast, in the international
standard format. On two months of real Sentinel-3A data, every one of sixty-one daily runs converged
and matched ESA's precise orbit to about two metres. Along the way we learned, concretely, that for
multi-day LEO forecasting the atmosphere — not the GPS product — is what you must get right, and we
built the system to make that, and its uncertainty, visible and honest. Thank you — I'm happy to take
questions.”*

---

# Appendix A — Anticipated questions and model answers

*Read these twice. They are the questions a scientific audience actually asks.*

**Q. Why batch least squares and not a Kalman filter?**
A. For a single fixed data arc, batch uses all observations at once and yields a clean epoch
covariance with no process-noise tuning. Kalman/UKF is the right tool for sequential real-time
estimation — a planned mode, not a replacement. Our finite-difference partials also make batch
force-model-agnostic, which suits a system built around swappable physics.

**Q. Why finite-difference partials instead of analytic or variational equations?**
A. Analytic partials are faster but must be re-derived for every force model; variational equations
are elegant but intrusive. Finite differences let us change any force and have the estimator and the
covariance propagation still work unchanged. The cost is extra propagations, which we accept and, for
covariance, make optional.

**Q. Why EGM96 to 70 and not EGM2008?**
A. At ~815 km the gravity signal above ~degree 70 is below our drag and EOP error floor, and harmonic
cost scales with degree squared. EGM2008 would be far slower for no measurable gain here, and EGM96 is
license-clean. The degree is a user setting for other regimes.

**Q. Why NRLMSISE-00 as the default atmosphere? Why not JB2008 or the newest models?**
A. NRLMSISE-00 is the community-standard empirical thermosphere model, responds to solar/geomagnetic
activity, and our implementation matches its official reference case to seven significant figures.
JB2008 is offered (stronger in storms). NRLMSIS 2.0 and DTM2020 are excluded only for licensing. All
models are exposed so users can compare rather than trust.

**Q. How do you know the orbit is right, not just internally consistent?**
A. We validate against ESA's independent POEORB precise orbits, which are never used as an input —
avoiding circular validation — and against more than 120 unit tests including an NRLMSISE-00 reference
case and recovery of known states from synthetic data.

**Q. Your forecast error is ~20 km at seven days — isn't that poor?**
A. It is expected for LEO without updated space weather, and it is dominated by unpredictable solar
activity driving drag, which we demonstrate by showing that the GPS product tier barely changes it.
At the cut-off and at one day the error is metres to tens of metres. The honest reporting of that
growth, via covariance, is a feature.

**Q. Why run everything offline — isn't that a limitation?**
A. It's a requirement for secure, air-gapped facilities, and it forces a clean, auditable,
dependency-free design. New data enters on approved media into a watched folder.

**Q. Why Java rather than C++ or Python?**
A. Java gives near-C++ numerical throughput via JIT with far fewer memory-safety pitfalls, strong
typing for correctness, true write-once-run-anywhere delivery, a mature orbital-mechanics reference
(Orekit) to map algorithms from, and self-contained packaging. Python would be too slow for the
integration inner loop and would drag in a dependency tree that undermines the air-gapped, auditable
goal; it's used only for small authoring scripts.

**Q. What about manoeuvres — the satellite does fire thrusters?**
A. We detect manoeuvre-like residual jumps and flag them; within a quiet arc the fit is clean.
Estimating manoeuvres explicitly is a natural extension.

**Q. How are the Earth-orientation and Sun/Moon handled?**
A. Full IAU-2006/2000B precession-nutation plus polar motion (validated against SOFA); optional IERS
EOP tightens it to centimetre level. Sun/Moon from analytical series by default, optional JPL DE440.

**Q. Can it handle other satellites / constellations?**
A. Yes — any satellite with GPS observations; the Sentinel-3A model is cosmetic, others get a generic
build. Multi-GNSS (Galileo, GLONASS) is a data-and-reader extension.

**Q. Is it operationally usable, or a prototype?**
A. It is a validated, standards-compliant, reproducible reference implementation with honest
limitations (real-time products, parallelism). Those are engineering extensions, not redesigns.

---

# Appendix B — Key numbers to memorise

- Sentinel-3A altitude **≈ 815 km**; period **≈ 100 min**; speed **≈ 7.5 km/s**; **~15 orbits/day**.
- Inclination **98.6°**, near-circular (e ≈ 0.001), **Sun-synchronous**.
- Campaign: **2 Sep – 2 Nov 2024**, **61** daily cut-offs; **6-hour** arc; **~73** fixes/run; **7-day** forecast.
- Result: **61/61** converged; median post-fit **2.35 m** (1.85–4.38); cut-off error **~0.5–2.2 m**; +1 day **~30–270 m**; +7 days **~20 km**.
- Product tiers post-fit RMS at 2024-10-15: broadcast **2.07**, ultra-rapid **2.56**, rapid **2.49**, final **2.45 m**.
- Gravity **EGM96 70×70**; integrator **Dormand–Prince 5(4)**; default drag **NRLMSISE-00**.
- **~950** catalogued input files; **120+** automated tests; **zero** runtime dependencies.
- Product accuracy: broadcast ~1–2 m, ultra-rapid ~5 cm, rapid ~2.5 cm, final ~2 cm.

---

# Appendix C — Glossary quick-reference (for the speaker)

LEO; orbit = free-fall; state vector vs Keplerian elements; epoch; GCRF/J2000 (inertial) vs ITRF
(Earth-fixed); EOP; GNSS/GPS; pseudorange; single-point positioning (SPP); fix; PDOP; GNSSRD; RINEX;
GPS products (broadcast/ultra-rapid/rapid/final); IGS; SP3/CLK; POEORB (reference, scoring only);
PROQUA (attitude); cut-off; forecast span; forecast vs reconstruction mode; spherical-harmonic gravity
(degree/order); J2/nodal regression; drag; density ρ; ballistic coefficient; scale height;
exponential / Harris–Priester / NRLMSISE-00 / JB2008; F10.7, Ap (space weather); SRP; umbra/penumbra;
third body; solid/ocean tides; Schwarzschild/Lense–Thirring; albedo; numerical integration; Cowell;
Dormand–Prince 5(4); adaptive step/tolerance; DSST; residual; least squares / Gauss–Newton; Jacobian /
finite differences; batch OD; post-fit RMS; variance factor; covariance; state-transition matrix;
CCSDS OEM; KML/CZML; SHA-256; air-gapped; RAG/BM25; manoeuvre flag.

*(Full definitions are in the project report, `docs/REPORT.md`.)*

---

# Appendix D — Timing plans

**90-minute default** — the durations in each header above.

**60-minute version** (tight): Part 1 (3) · Part 2 (5) · skip Part 3 · Part 4 (7) · Part 5 (8) ·
Part 6 (12, emphasise gravity + drag only) · Part 7 (8, SPP + integrator + OD headline) ·
Part 8 demo (12) · Part 9 (6) · Part 10 (5, lead with non-convergence) · Part 11/12 (4) · Q&A (remainder).

**120-minute version** (deep): keep all durations, include Part 3 in full (10), extend Part 6 to 30
(walk every force live in the Perturbations panel), extend Part 8 demo to 30 (run a live case
end-to-end and a live tier comparison), and allow 25–30 min of Q&A using Appendix A.

---

# Appendix E — Demo backup and troubleshooting

- **Have the report PDF open** at the matching figure for every demo step; if the live app stalls,
  narrate the screenshot instead — the audience won't notice.
- **Pre-compute runs** before the talk (open them from History) so you never watch a progress bar.
- If the globe is slow on the presentation machine, use the **2-D map** (it's light) and the
  screenshots; the map carries the same information.
- Know the start command cold: `java -jar target/leo-trajectory-0.1.0.jar gui`, then open the printed
  `http://127.0.0.1:…` address.
- If asked for the code live, show the three layers in the architecture figure rather than scrolling
  source.

---

*Companion materials: the full written report (`docs/REPORT.md` / the report PDF), the per-scene video
narration script, and the demonstration video.*
