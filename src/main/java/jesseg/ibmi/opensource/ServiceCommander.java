package jesseg.ibmi.opensource;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.yaml.YamlServiceDefLoader;

public class ServiceCommander {

    private static void printUsageAndExit() {
        // @formatter:off
		final String usage = "Usage: sc  [options] <operation> <service>\n" +
		"\n" 
		                + "    Valid options include:\n"
				+ "        -v: verbose mode\n" + "\n"
		                + "    Valid operations include:\n"
				+ "        start: start the service (and any dependencies)\n"
				+ "        stop: stop the service (and dependent services)\n"
				+ "        restart: restart the service\n"
				+ "        check: check status of the service\n";
		// @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

    public static void main(final String... _args) throws Exception {
        if (_args.length < 2) {
            printUsageAndExit();
        }

        final LinkedList<String> args = new LinkedList<String>(Arrays.asList(_args));
        final String service = args.removeLast();
        final String operation = args.removeLast();
        final AppLogger logger = new AppLogger(args.contains("-v"));

        logger.println_verbose("Verbose mode enabled");
        logger.println_verbose("--------------------");
        
        checkApplicationDependencies(logger);
        logger.printf("Performing operation '%s' on service '%s'\n", operation, service);

        final Map<String, ServiceDefinition> serviceDefs = new YamlServiceDefLoader().loadFromYamlFiles(logger);

        final OperationExecutor executioner = new OperationExecutor(Operation.valueOf(operation.toUpperCase().trim()), service, serviceDefs, logger);

        executioner.execute();
        System.out.println("\n\nMain task completed\n");
    }

    private static void checkApplicationDependencies(final AppLogger _logger) {
        if (!System.getProperty("java.home", "").contains("/pkgs")) {
            _logger.println_err("ERROR: This product will only work with open source Java distributions");
            System.exit(-1);
        }
        if (!new File("/QOpenSys/pkgs/bin/db2util").canExecute()) {
            _logger.println_err("ERROR: Required tool 'db2util' not installed. Please install this RPM");
            System.exit(-1);
        }
    }

}
