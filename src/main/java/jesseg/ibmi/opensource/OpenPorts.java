package jesseg.ibmi.opensource;

import java.util.Arrays;
import java.util.LinkedList;

import com.github.theprez.jcmdutils.AppLogger;

/**
 * Main entry point for the application
 *
 * @author Jesse Gorzinski
 */
public class OpenPorts {

    public static void main(final String... _args) {
        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));
        if (args.remove("-h") || args.remove("--help")) {
            printUsageAndExit();
        }
        final AppLogger logger = new AppLogger.DefaultLogger(args.remove("-v"));
        ServiceCommander.checkApplicationDependencies(logger);
        try {
            ServiceCommander.listOpenPorts(logger, args);
        } catch (final SCException e) {
            logger.printExceptionStack_verbose(e);
            System.exit(-3);
        }
    }

    private static void printUsageAndExit() {
        // @formatter:off
		final String usage = "Usage: scopenports  [options]\n"
		                        + "\n"
		                        + "    Valid options include:\n"
                                + "        -v: verbose mode\n"
                                + "        --mine: only show ports that you have listening"
                                + "\n"
                                ;
		// @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

}
