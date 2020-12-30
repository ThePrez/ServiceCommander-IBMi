package jesseg.ibmi.opensource;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.yaml.YamlServiceDefLoader;

/**
 * Main entry point for the application
 * 
 * @author Jesse Gorzinski
 */
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

    public static void main(final String... _args) {
        if (_args.length < 2) {
            printUsageAndExit();
        }

        final LinkedList<String> args = new LinkedList<String>(Arrays.asList(_args));
        final String service = args.removeLast().trim();
        final String operation = args.removeLast().trim();
        final AppLogger logger = new AppLogger(args.contains("-v"));

        logger.println_verbose("Verbose mode enabled");
        logger.println_verbose("--------------------");

        checkApplicationDependencies(logger);

        try {
            final Map<String, ServiceDefinition> serviceDefs = new YamlServiceDefLoader().loadFromYamlFiles(logger);
            final Operation op = Operation.valueOf(operation.toUpperCase().trim()); // TODO: better input validation

            if (service.toLowerCase().startsWith("group:")) {
                performOperationsOnServices(op, getServicesInGroup(service.substring("group:".length()).trim(), serviceDefs, logger), serviceDefs, logger);
            } else {
                performOperationsOnServices(op, Collections.singleton(service), serviceDefs, logger);
            }
        } catch (final SCException e) {
            System.exit(-3);
        }
        System.out.println("\n\nProgram completed successfully\n");
    }

    private static Set<String> getServicesInGroup(final String _group, final Map<String, ServiceDefinition> _serviceDefs, final AppLogger _logger) {
        _logger.printfln_verbose("Looking for services in group '%s'", _group);
        final LinkedHashSet<String> ret = new LinkedHashSet<String>();
        for (final ServiceDefinition svcDef : _serviceDefs.values()) {
            if ("all".equalsIgnoreCase(_group)) {
                ret.add(svcDef.getName());
                continue;
            }
            for (final String svcGroup : svcDef.getGroups()) {
                if (svcGroup.trim().equalsIgnoreCase(_group)) {
                    ret.add(svcDef.getName());
                }
            }
        }
        _logger.printfln_verbose("Services in group '%s' are: %s", _group, ret);
        return ret;
    }

    private static void performOperationsOnServices(final Operation _op, final Set<String> _services, final Map<String, ServiceDefinition> _serviceDefs, final AppLogger _logger) throws SCException {
        SCException lastExc = null;
        for (final String service : _services) {
            if (Operation.CHECK == _op) {
                _logger.printf_verbose("Performing operation '%s' on service '%s'\n", _op.name(), service);
            } else {
                _logger.printf("Performing operation '%s' on service '%s'\n", _op.name(), service);
            }
            try {
                final OperationExecutor executioner = new OperationExecutor(_op, service, _serviceDefs, _logger);
                executioner.execute();
            } catch (final SCException e) {
                lastExc = e;
            }
        }
        if (null != lastExc) {
            throw lastExc;
        }
    }

    private static void checkApplicationDependencies(final AppLogger _logger) {
        if (!System.getProperty("java.home", "").contains("/pkgs")) {
            _logger.println_err("ERROR: This product will only work with open source Java distributions");
            System.exit(-17);
        }
        if (!new File("/QOpenSys/pkgs/bin/db2util").canExecute()) {
            _logger.println_err("ERROR: Required tool 'db2util' not installed. Please install this RPM");
            System.exit(-18);
        }
    }

}
