#!/bin/sh
# usage: build.sh <docs-dir> <out.html> <out.pdf>   (the exact layout of the printed report)
S=/tmp/claude-0/-home-user-URSC/61dc98cb-9bea-52d0-a2f6-f89d8472166c/scratchpad
cd "$1" && LC_ALL=C.UTF-8 LANG=C.UTF-8 pandoc -f gfm+tex_math_dollars REPORT.md -s --toc --toc-depth=2 --mathml \
  --embed-resources --css $S/report.css --resource-path=. \
  -M title="GNSS Based Orbit Determination and Trajectory Prediction" \
  -M subtitle="Project report — LEO orbit determination and forecasting from a satellite's own GPS data" \
  -M author="Internship project · Test satellite: ESA Sentinel-3A" -M date="November 2026" -o "$2" 2>/dev/null
cd $S && node topdf.cjs "$2" "$3" >/dev/null 2>&1
