package org.ursc.trajectory.app.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.ursc.trajectory.app.Args;
import org.ursc.trajectory.app.gui.GuiServer;

/** Starts the offline local web GUI. */
public final class GuiCommand implements Command {

    @Override
    public String name() {
        return "gui";
    }

    @Override
    public String description() {
        return "start the offline web GUI on this computer (no internet needed)";
    }

    @Override
    public int run(final Args args) throws Exception {
        final int port = args.getInt("port", 0);
        final Path workspace = Paths.get(args.get("workspace",
                Paths.get(System.getProperty("user.home"), "URSC-workspace").toString()));
        final GuiServer server = new GuiServer(port, workspace);
        server.start();
        final List<String> data = new ArrayList<>();
        if (args.has("data")) {
            for (final String f : args.get("data", "").split("[;,]")) {
                if (!f.isBlank()) {
                    data.add(Paths.get(f.trim()).toAbsolutePath().toString());
                }
            }
        } else if (server.catalogEmpty() && !args.has("no-project-data")) {
            data.addAll(projectData());
        }
        if (!data.isEmpty()) {
            System.out.println("Indexing:  " + String.join(", ", data) + " (in the background)");
            server.registerInBackground(data);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("GNSS Based Orbit Determination and Trajectory Prediction\n(running on this computer only, no internet used).");
        System.out.println("Workspace: " + workspace.toAbsolutePath());
        System.out.println("Open:      " + server.url());
        System.out.println("Stop with Ctrl+C or the Quit button.");
        if (!args.has("no-browser")) {
            openBrowser(server.url());
        }
        server.awaitShutdown();
        return 0;
    }

    /** Folders of the project checkout that hold run inputs (tested-window data, EOP, space weather). */
    static List<String> projectData() {
        final List<Path> roots = new ArrayList<>();
        roots.add(Paths.get("").toAbsolutePath());
        try {
            final Path jar = Paths.get(GuiCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.getParent() != null && jar.getParent().getParent() != null) {
                roots.add(jar.getParent().getParent());
            }
        } catch (final Exception ignored) {
            // no code-source location
        }
        for (final Path r : roots) {
            if (Files.isDirectory(r.resolve("data").resolve("gnss"))) {
                final List<String> out = new ArrayList<>();
                out.add(r.resolve("data").toString());
                final Path extra = r.resolve("docs/reports/perturbation-ladder-truestate-7day/inputs");
                if (Files.isDirectory(extra)) {
                    out.add(extra.toString());
                }
                return out;
            }
        }
        return List.of();
    }

    /** Best-effort: opens the default browser without needing the java.desktop module. */
    static void openBrowser(final String url) {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
        final String[] cmd = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[] {"open", url} : new String[] {"xdg-open", url};
        try {
            new ProcessBuilder(cmd).inheritIO().start();
        } catch (final Exception e) {
            System.out.println("(could not open a browser automatically; paste the URL above)");
        }
    }
}
