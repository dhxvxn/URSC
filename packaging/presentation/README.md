# Ready-to-run saved presentation

Download **URSC-saved-presentation.zip** from this folder and extract it fully.
Java 17 or newer is required. Maven and raw input files are not required.

- **Windows:** double-click `start-presentation.cmd`.
- **Linux:** open a terminal in the extracted folder and run
  `sh start-presentation.sh`.
- **Either:** `java -jar leo-trajectory-presentation.jar gui --presentation`.

The local browser opens on **Saved presentation**. If it does not open, copy the
complete URL printed in the terminal into your browser on the same computer.
The page includes all 427 attempted cases and 385 archived outputs, grouped by
experiment and GPS product tier. Select a row to view state samples and recorded
one-day/seven-day errors; download individual OEMs, all outputs or the explanation
PDF. Daily cutoffs run from 3 September to 2 November 2024.

No OD, propagation, new campaign or raw-data indexing runs at startup. Do not use
Run/Window to start a new experiment during the presentation. Existing results are
a separate read-only archive rather than new entries in local History. The same
archive is included when you build the source normally with Maven.

The bundle uses your installed Java runtime. It does not include Java itself.
It works offline after extraction. Keep the terminal open while presenting;
stop with Ctrl+C or the application's Quit button. This launcher opens normal
application controls too, so explicitly clicking Run still starts a new calculation.

Daily benchmark samples are not continuous orbit tracks. Retrospective products
and maneuver replays remain labelled, and failed dates are not filled in.
The included OEM explanation report gives the full interpretation limits.
