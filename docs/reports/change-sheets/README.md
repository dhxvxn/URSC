# How the change sheets were made

* `build.sh` reproduces the layout of the printed 26-page report exactly. Pandoc 3.1.3 renders
  the report with `-f gfm+tex_math_dollars`, `--toc --toc-depth=2 --mathml`, and `report.css`;
  Chromium (`topdf.cjs`) then prints it. A rebuild of commit `bac1c40` is text-identical to the
  printout on all 26 pages.
* `changesheets.py` renders the updated report. It forces page breaks before the elements that
  begin printed pages 12 and 23 (`cfg1.json`), so that the pages after each changed block match
  the printout again. It then aligns old and new pages (`blocks.json`).
* `assemble.py` writes `../../Report-Change-Sheets.pdf`: a cover page with the swap instructions,
  and the replacement pages, each labelled in the top margin.
* Verification: the printed report, with the blocks swapped as instructed, is page-for-page
  identical to `../../Report-Full-Updated.pdf`.
