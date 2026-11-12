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
sudo apt install -y git openjdk-17-jdk maven

# 2. Check they are installed (both commands should print a version)
java -version
javac -version
mvn -version

# 3. Get the project
git clone --branch claude/leo-trajectory-prediction-java-ihupky https://github.com/dhxvxn/URSC.git
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
# 1. Install Git
winget install --id Git.Git -e

# 1a. Install Java 17+ (Microsoft OpenJDK) with winget
winget install --id Microsoft.OpenJDK.21 -e

# 1b. Install Maven. winget often does not carry Maven, so install it by hand:
#     - Download the "Binary zip archive" from https://maven.apache.org/download.cgi
#     - Unzip it to a folder you control, e.g.  C:\Users\<you>\tools\apache-maven-3.9.9
#     - Add its \bin folder to your PATH, OR call it by full path as shown below.
#     Close and reopen PowerShell after installing so PATH changes take effect.

# 2. Check they are installed (both should print a version)
java -version
javac -version
mvn -version
#    If 'mvn' is not recognised, use the full path instead, e.g.:
#    C:\Users\<you>\tools\apache-maven-3.9.9\bin\mvn -version

# 3. Get the project
git clone --branch claude/leo-trajectory-prediction-java-ihupky https://github.com/dhxvxn/URSC.git
cd URSC

# 4. Build the program (compiles and runs the tests; a few minutes the first time)
mvn clean package
#    …or skip the tests to build faster:
#    mvn -q clean package -DskipTests

# 5. Run it — this starts the browser interface
java -jar target\leo-trajectory-0.1.0.jar gui
```

### Requirements and environment setup

- **JDK 17 or newer**, including `javac`; Maven 3.9.x is recommended. Check
  `mvn -version` reports the intended Java installation.
- **Git** for cloning and updates; a current browser for the local interface.
- The core application needs **no Python, Ollama, external model server, Node.js,
  or Orekit installation**. Maven downloads build plugins and JUnit test dependencies.
  Research/report Python scripts have separate dependencies; inspect their imports
  before running them. They are not required to launch the Java application.
- Internet is needed for the initial clone, Maven dependency downloads, and any new
  input downloads. Prepared builds and local inputs run offline. A fresh Maven
  installation cannot build offline until its required artifacts are cached.
- Storage depends on the input campaign: the archived input collection alone is
  several GB. Leave additional space for Git history, build files and run results.
  Runtime memory and processing time depend on arc length, sampling and batch size;
  no universal minimum or accuracy guarantee is established here.

On Linux, if Maven selects the wrong Java, select the JDK with your distribution's
Java alternatives tools, then set `JAVA_HOME` to the actual JDK directory (not `bin`)
in your shell profile. For example, after substituting your installed path:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

On Fedora/RHEL-family systems, use `sudo dnf install git java-17-openjdk-devel maven`
where those packages are available. Distribution package versions can differ.

On Windows, open **Edit environment variables for your account**. Set `JAVA_HOME`
to the installed JDK directory, add `%JAVA_HOME%\bin` and the extracted Maven
`bin` directory to the user `Path`, and reopen PowerShell. Do not set `JAVA_HOME`
to the JRE or to the JDK's `bin` directory. For a temporary session:

```powershell
# Replace both paths with your actual installed directories.
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:Path"
Get-Command git, java, javac, mvn
mvn -version
# Quoted executable paths require PowerShell's call operator:
& "$env:USERPROFILE\tools\apache-maven-3.9.9\bin\mvn.cmd" -version
```

If `winget` is unavailable, install Git from <https://git-scm.com/downloads>,
a JDK from <https://adoptium.net/>, and Maven from <https://maven.apache.org/download.cgi>.
Use the current supported Maven 3.9.x binary release; the directory above is an example.

### Verify and run a small scenario

From the repository root, on either operating system:

```text
mvn clean package
java -jar target/leo-trajectory-0.1.0.jar help
java -jar target/leo-trajectory-0.1.0.jar propagate sample-scenario.properties --quiet
java -jar target/leo-trajectory-0.1.0.jar gui
```

The Maven build must report `BUILD SUCCESS`; the JAR is written under `target/`.
The scenario is a smoke check of execution, not a Sentinel-3A accuracy assessment.
For later updates, commit or stash local edits before `git pull --ff-only`, then rebuild.

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
- **[docs/PROMPT_DEVELOPMENT_REPORT.md](docs/PROMPT_DEVELOPMENT_REPORT.md)** — the polished, reproducible prompt history (Codex + Claude) that built the project.
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


## Input selection, forecasts and OEM output

In **Data**, add/watch the local folders containing your files, then choose the inputs
and UTC cutoff in **Run**. Folder paths belong to the user running the application;
there is no requirement to reproduce the developer's laptop paths.

| Input | Purpose |
|---|---|
| Sentinel-3A GNSSRD observations | Satellite GPS measurements used for position fixes and OD over the pre-cutoff arc. |
| GPS Broadcast navigation or precise SP3 + clock products | GPS satellite positions and clocks used to interpret the observations. Prefer compatible orbit/clock products from the same provider and solution. |
| PROQUA/available attitude data | Spacecraft orientation for attitude-dependent forces; check actual packet coverage. GPS constellation attitude OBX products are not Sentinel-3A attitude. |
| EOP and space-weather drivers | Earth-frame transforms and atmosphere inputs. Forecast mode must restrict data to what was available at cutoff and use the configured future-input policy. |
| Gravity field and spacecraft properties | Force coefficients, mass, drag/SRP area and optical assumptions. Some defaults/tables are bundled; record every override. |
| Sentinel-3A POEORB reference | Independent scoring after prediction in the GNSS workflow. The separate seed/fit CLI workflows deliberately use precise states and must be labelled accordingly. |

Product priority is **Final → Rapid → Ultra-Rapid → Broadcast** among eligible,
compatible products covering each satellite/epoch. Priority does not override the
forecast cutoff: final products produced after cutoff are retrospective information.
Choose **Forecast** for cutoff-restricted experiments and **Reconstruction** for
retrospective processing. Inspect run flags, defaults and product provenance; a
historical filename or sample epoch alone does not prove publication before cutoff.
Future actual weather, reference states and inferred future maneuvers must not enter
a forecast. Accuracy claims need an explicit forecast/reconstruction label.

After a successful GUI run, open **Results** and download **OEM**. The existing
exporter writes **CCSDS OEM 2.0, KVN**, UTC epochs, Earth centre, the selected frame,
position in km and velocity in km/s. Formal covariance is included when available;
it is not a guarantee of forecast accuracy. Preserve the stored configuration and
input provenance with the export. For two runs, download one OEM for each and name
them with satellite, cutoff, horizon and model/profile to avoid mixing results.
CSV-only CLI commands do not automatically produce an OEM.

## Adding and plugging in another model

New Java classes are compiled into this application. There is no automatic discovery
of arbitrary plugin JARs or external atmosphere implementations. Adding a name to a
configuration file alone does not install its physics or coefficient data.

| Extension | Interface / integration point |
|---|---|
| New acceleration | `forces/ForceModel.java`: `acceleration(SpacecraftState)` and `getName()`; register with `NumericalPropagator.addForceModel`. |
| New density model | `forces/drag/Atmosphere.java`: `getDensity(date, positionBodyFixed)` and `getName()`; pass to `PropagationConfig.drag(atmosphere, cd, area)`. |
| Weather / Earth orientation | `SpaceWeatherProvider` / `EopProvider`; provide cutoff-safe inputs and document time conventions. |
| Integrator / events / sampled output | `ODEIntegrator`, `EventDetector`, `StepHandler`; see [architecture](docs/ARCHITECTURE.md) for contracts. |

### Minimal force-model example

Create `src/main/java/org/ursc/trajectory/forces/ExampleAcceleration.java`:

```java
package org.ursc.trajectory.forces;

import org.ursc.trajectory.math.Vector3D;
import org.ursc.trajectory.propagation.SpacecraftState;

/** Demonstration only: a prescribed constant inertial acceleration. */
public final class ExampleAcceleration implements ForceModel {
    @Override public Vector3D acceleration(SpacecraftState state) {
        return new Vector3D(1e-9, 0.0, 0.0); // m/s^2 in the state's frame
    }
    @Override public String getName() { return "ExampleAcceleration"; }
}
```

In Java code, after constructing a numerical propagator (as in the
[configuration API](src/main/java/org/ursc/trajectory/config/PropagationConfig.java)):

```java
propagator.addForceModel(new ExampleAcceleration());
```

Import the example class in your caller. Register it once; never add a second copy
of an existing force. This artificial acceleration demonstrates the interface and
is not an accuracy recommendation.

### Make it selectable in the actual workflow

1. Implement the interface under the appropriate `src/main/java` package. Use SI
   units internally: metres, seconds, kg; density kg/m³; acceleration m/s² in the
   state's inertial frame. `Atmosphere` receives an Earth-fixed position. Transform
   body/RTN vectors explicitly and use the project's date/time APIs.
2. For a new atmosphere, load the published coefficients and required solar or
   geomagnetic drivers. An MSIS 2.x adapter requires the actual implementation,
   data and licence; renaming NRLMSISE-00 does not implement MSIS 2.x. If it needs a
   native library, document Windows/Linux installation and packaging separately.
3. For scenario CLI selection, wire the choice into
   `config/ScenarioLoader.java` (for density, `buildAtmosphere()`), and extend
   `config/PropagationConfig.java` where a new builder option is necessary.
4. For GNSS OD + forecast / GUI runs, wire it into
   `pipeline/ForceModelBuilder.java`; add configuration fields/validation in
   `pipeline/RunConfig.java` and inputs in `pipeline/Environment.java` as needed.
   Update GUI parameter metadata in `src/main/resources/gui/params.json` and any
   corresponding controls. These are separate entry points: changing only the
   scenario loader does not change the GUI pipeline.
5. Apply the same configured force model to the OD and forecast factories unless
   the experiment deliberately specifies otherwise. Save model identity,
   coefficients, driver files and fallback behavior with the run. Do not silently
   substitute another density model when requested inputs are missing.
6. Add meaningful tests under `src/test/java`: authoritative model reference cases,
   units/frame checks, missing-data behavior and cutoff compliance. Run
   `mvn clean package`, then a small end-to-end case before the full campaign.
7. Compare baseline and new model on identical initial inputs, cutoffs and scoring
   epochs. Report one-day/seven-day errors, convergence and failures together;
   hold out dates from tuning. More detailed physics does not ensure smaller errors.

Only add a dependency to `pom.xml` if the new implementation needs it; record its
version, licence and offline requirements. Rebuild the JAR and offline bundle after
code or bundled-data changes. See [USAGE](docs/USAGE.md) for supported configuration
keys rather than assuming every Java extension already has a CLI flag.

## Troubleshooting

| Symptom | Action |
|---|---|
| `git`, `java` or `mvn` not found | Install it, check `PATH`, reopen the terminal. On Windows use `Get-Command`; on Linux use `command -v`. |
| `release version 17 not supported` | Maven is using an older JDK; check `mvn -version` and correct `JAVA_HOME`. |
| Dependency download fails | Check connectivity, proxy and Maven `settings.xml`; do not disable TLS verification. Retry after correcting access. |
| JAR missing or inaccessible | Run from the repository root and confirm `mvn clean package` succeeded. Quote paths containing spaces. |
| GUI did not open | Copy the complete printed loopback URL, including its token, into a browser on the same machine. |
| Missing input coverage | Inspect the catalogue, satellite IDs, validity interval and cutoff eligibility; add the corresponding files. Do not treat incomplete `.crdownload` files as inputs. |
| OD rejected or errors large | Inspect quality flags and observation coverage, frame/time conventions and force assumptions. Keep failed dates in the report; inspect the detailed run log. |


## Two-month campaign OEM outputs

The archived Sentinel-3A campaign OEMs and their field-by-field explanation are in
[the OEM output report](docs/reports/two-month-oem-export/OEM_OUTPUT_REPORT.md).
The package contains daily benchmark samples, not dense orbital tracks; nominal
predictions and retrospective maneuver replays are labelled separately.
