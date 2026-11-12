# GNSS Based Orbit Determination and Trajectory Prediction

A self-contained Java application that determines the orbit of a **Low Earth Orbit (LEO)
satellite** from its own **GPS measurements** and **predicts where it will be** over the
coming days. It is built and validated on the ESA **Sentinel-3A** satellite.

In plain terms: a satellite in orbit records the GPS signals it receives. This program reads
those recordings, works out exactly where the satellite was (orbit determination), then runs a
physics simulation of gravity, atmospheric drag, Sun/Moon pull and more to forecast where it
will be next (trajectory prediction). Every result is written as a standard **CCSDS OEM**
ephemeris file.

It runs entirely on **one computer with no internet connection** (suitable for secure,
air-gapped facilities): the 3D globe, maps, documentation and search are all bundled inside the
program. It has **no runtime dependencies** — the physics core is plain, auditable Java. A local
web interface (opened in your browser) provides data management, run setup, a 3D globe and 2D
ground-track map, diagnostic plots, product comparisons and downloads.

**New to the project? Read [`docs/REPORT.md`](docs/REPORT.md)** — a full illustrated report that
explains the problem, the method, the features and the results, with a glossary of every term.

---

## Installation and running

You need two free tools: a **Java Development Kit (JDK) 17 or newer** and **Apache Maven** (the
build tool). Below is the complete process on both Linux and Windows, from installing those tools
to seeing the program run in your browser.

### Linux

```bash
# 1. Install Java 17+ and Maven (Debian/Ubuntu shown; use your distro's package manager)
sudo apt update
sudo apt install -y openjdk-17-jdk maven

# 2. Check they are installed (both commands should print a version)
java -version
mvn -version

# 3. Get the project
git clone https://github.com/dhxvxn/URSC.git
cd URSC

# 4. Build the program (compiles and runs the tests; a few minutes the first time)
mvn clean package
#    …or skip the tests to build faster:
#    mvn -q clean package -DskipTests

# 5. Run it — this starts the browser interface
java -jar target/leo-trajectory-0.1.0.jar gui
```

### Windows (PowerShell)

Windows has no built-in package for these tools, so install them once, then the steps match
Linux.

```powershell
# 1a. Install Java 17+ (Microsoft OpenJDK) with winget
winget install --id Microsoft.OpenJDK.21 -e

# 1b. Install Maven. winget often does not carry Maven, so install it by hand:
#     - Download the "Binary zip archive" from https://maven.apache.org/download.cgi
#     - Unzip it to a folder you control, e.g.  C:\Users\<you>\tools\apache-maven-3.9.9
#     - Add its \bin folder to your PATH, OR call it by full path as shown below.
#     Close and reopen PowerShell after installing so PATH changes take effect.

# 2. Check they are installed (both should print a version)
java -version
mvn -version
#    If 'mvn' is not recognised, use the full path instead, e.g.:
#    C:\Users\<you>\tools\apache-maven-3.9.9\bin\mvn -version

# 3. Get the project
git clone https://github.com/dhxvxn/URSC.git
cd URSC

# 4. Build the program (compiles and runs the tests; a few minutes the first time)
mvn clean package
#    …or skip the tests to build faster:
#    mvn -q clean package -DskipTests

# 5. Run it — this starts the browser interface
java -jar target\leo-trajectory-0.1.0.jar gui
```

### What you should see

When it starts, it prints a line like:

```
Open:  http://127.0.0.1:XXXXX/#t=<token>
```

and opens your browser there automatically. If it does not open, copy that exact address
(the token matters) into your browser. Everything is in that page:

- **Data** — the catalogue of input files (it recognises each file by its content).
- **Run** — set up and launch an orbit determination + forecast (it opens on the tested
  Sentinel-3A window by default). Buttons: **▶ Run**, **⚖ Compare product tiers**.
- **Results** — the 3D globe, the interactive 2D ground-track map (hover to scrub time), the
  diagnostic plots, and the OEM / KML / CZML / report downloads.
- **Window** — run a whole date range (e.g. the 61-day campaign) and see the trends.
- **Help** — ask a question and get an answer from the bundled documentation, offline.

Stop the program with **Ctrl+C** in the terminal, or the ⏻ button at the top-right of the page.

> **Notes.** Run the `gui` command from the project folder so it finds the `data` directory.
> The first launch spends about a minute cataloguing the ~950 bundled input files. For
> air-gapped machines, `packaging/build-bundle.sh` (Linux/macOS) or `packaging/build-bundle.ps1`
> (Windows) builds a bundle that carries its own Java runtime — see
> [`packaging/README-OFFLINE.md`](packaging/README-OFFLINE.md).

---

## How it works (one paragraph)

For a chosen cut-off time, the program takes the satellite's GPS observations over a short arc
(6 hours by default), computes a position fix at each epoch by **single-point positioning**,
fits a single consistent orbit to those fixes by **batch least-squares orbit determination**,
and then **numerically propagates** that orbit forward (gravity field, atmospheric drag, solar
radiation, Sun/Moon, tides, relativity) to produce the forecast. Where a precise reference orbit
(**POEORB**) exists, it also reports the error against it — for display only; it is never fed
back into the computation. Full detail, with pictures and a glossary, is in
[`docs/REPORT.md`](docs/REPORT.md).

---

## Documentation

- **[docs/REPORT.md](docs/REPORT.md)** — illustrated end-to-end report (problem → results) with a glossary.
- **[docs/PRESENTATION.md](docs/PRESENTATION.md)** — a 60–120 minute speaker's script and demo guide.
- **[docs/GNSS-OD-demo.mp4](docs/GNSS-OD-demo.mp4)** — a 43-second demonstration video (opens in GitHub's video player), with its narration in **[docs/VIDEO-NARRATION.md](docs/VIDEO-NARRATION.md)**.
- **[docs/USAGE.md](docs/USAGE.md)** — every CLI command and flag, and the scenario `.properties` reference.
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — package map and how to add a force model / atmosphere / integrator / propagator.
- **[docs/PHYSICS.md](docs/PHYSICS.md)** — every model with its formula, references and accuracy caveats.
- **[docs/OREKIT_DATA.md](docs/OREKIT_DATA.md)** — supplying orekit-data EOP and gravity models.

The in-app **Guide** (the *Guide* button in the interface, or
`src/main/resources/gui/guide/`) walks through every feature with screenshots.

---

## What is implemented

A merged pipeline **GPS observations → single-point positioning → batch orbit determination →
numerical propagation → CCSDS OEM**, with:

- **GPS products**, in priority order **IGS Final → Rapid → Ultra-rapid → Broadcast**, chosen
  per satellite and per epoch; works in cut-off-safe *Forecast* mode or retrospective
  *Reconstruction* mode.
- **Force models** (each independently on/off with a model choice): spherical-harmonic gravity
  (EGM96 to degree/order 70), atmospheric drag (exponential / Harris-Priester / NRLMSISE-00 /
  JB2008), solar radiation pressure with eclipse, Sun and Moon third bodies, solid and ocean
  tides, general relativity (Schwarzschild + Lense-Thirring), Earth albedo/IR, empirical
  acceleration.
- **Integrators:** Dormand-Prince 5(4) adaptive (default) and RK4; a semi-analytical DSST
  mean-element propagator for long runs.
- **Formal covariance** propagated from the orbit-determination solution to the output epochs.
- **Outputs:** CCSDS OEM 2.0 (with covariance), KML, CZML, a printable HTML report, and a log —
  every run is reproducible from its stored configuration and input checksums.
- **Offline web interface:** 3D globe (with a Sentinel-3A build and a generic build), interactive
  2D ground-track map, diagnostic plots, run history, multi-run comparison, product-tier
  comparison, parameter sweeps, ground-station passes, and an offline retrieval-augmented (RAG)
  help search.

See [docs/REPORT.md](docs/REPORT.md) for the full feature tour and the validation results on the
Sentinel-3A two-month campaign.

---

## Verification

`mvn test` runs the full suite (120+ tests): loss-free orbit conversions, energy conservation,
J2 nodal regression, the time-scale/leap-second table, NRLMSISE-00 against its official reference
case, DSST agreement, batch-OD recovery of a known state and drag coefficient, POEORB parsing,
CCSDS OEM round-trip, and the data-catalogue content recognition. The pipeline is additionally
checked end-to-end against real **Sentinel-3A** precise orbits (POEORB).

---

## Data

The bundled dataset covers the tested window (Sentinel-3A, 2 September – 2 November 2024):
GNSSRD GPS observations, PROQUA attitude, POEORB reference orbits, and GPS products (Broadcast,
IGS Final, Rapid, Ultra-rapid from several analysis centres), each with checksums. See the
[input-data table](data/README.md) and the [machine-readable manifest](data/input-manifest.json).
Incomplete downloads are quarantined under `data/unconfirmed-downloads/` and are not used.

---

*Licence note: the NRLMSIS 2.0 and DTM2020 atmosphere models and their coefficient files are not
included for licensing reasons; the shipped drag options are exponential, Harris-Priester,
NRLMSISE-00 and JB2008.*
