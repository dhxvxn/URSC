# Offline operation

This application is designed for networks without internet access.

- The program runs a small web server **bound to this computer only** (127.0.0.1); the browser is just the window. Nothing is exposed to the network.
- Every page asset is inside the application: the 3D globe engine (three.js), country borders (Natural Earth), plots, help pages and the search index. No fonts, maps or scripts are fetched from outside.
- Bundled models: EGM96 gravity, FES2004 ocean tides, JB2008 solar indices, analytic Sun/Moon.
- New data (GNSS, IGS products, POEORB, EOP, weather) arrives on approved media and is copied into a watched folder.
- The self-contained package includes its own Java runtime, so no installation is needed on the target machine.

## Help search and AI answers
Help search is always offline: it ranks passages from the help pages, every setting's explanation, the project documentation and any notes you place in the workspace `knowledge` folder, and composes an answer from the best sentences with citations. Two further answer providers are prepared but disabled in this release: a **local model via Ollama** (also offline) and a **cloud model with an API key** (Claude or OpenAI-compatible; needs internet, never used on an air-gapped network). They are configured in `ai.json` in the workspace; the API key is read from an environment variable and never stored.
