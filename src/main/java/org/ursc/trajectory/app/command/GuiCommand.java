package org.ursc.trajectory.app.command;

import java.nio.file.Path;
import java.nio.file.Paths;
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
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("URSC GUI running (this computer only, no internet used).");
        System.out.println("Workspace: " + workspace.toAbsolutePath());
        System.out.println("Open:      " + server.url());
        System.out.println("Stop with Ctrl+C or the Quit button.");
        if (!args.has("no-browser")) {
            openBrowser(server.url());
        }
        server.awaitShutdown();
        return 0;
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
