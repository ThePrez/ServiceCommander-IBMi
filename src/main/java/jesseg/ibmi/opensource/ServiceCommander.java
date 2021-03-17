package jesseg.ibmi.opensource;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAliveType;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.utils.AppLogger.DeferredLogger;
import jesseg.ibmi.opensource.utils.StringUtils;
import jesseg.ibmi.opensource.yaml.YamlServiceDefLoader;

/**
 * Main entry point for the application
 *
 * @author Jesse Gorzinski
 */
public class ServiceCommander {

    /**
     * Checks key dependencies that the application cannot function without. In the case of a missing dependency,
     * this function will cause JVM exit.
     *
     * @param _logger
     */
    private static void checkApplicationDependencies(final AppLogger _logger) {
        // Why does this program require OSS Java distributions?
        // Why can't it just work with JV1?
        //
        // Well, the answer lies in how JV1 implements java.lang.Runtime.exec(). JV1's implementation, for
        // legacy compatibility reasons, unconditionally spawns an ILE job that will then (if needed) call
        // back into PASE. In this design, handling of environment variables between the parent and child
        // jobs is unpredictable and things can get "lost" in the transition from Java to ILE, then back to PASE
        // (remember that ILE and PASE maintain their own environment variable table).
        //
        // The only way to cleanly implement and control the environment variable set for child processes
        // is to rely on the much more "normal" implementation in OpenJDK, which follows MUCH closer to
        // UNIX-style expectations.
        if (!System.getProperty("java.home", "").contains("/pkgs")) {
            _logger.println_err("ERROR: This product will only work with open source Java distributions");
            System.exit(-17);
        }

        // Why does this program require db2util?
        // Why can't it just work with JT400?
        //
        // Well, the answer lies within authentication. At the time of authorship of this tool, the JtOpen
        // project (aka "JT400," among other names) does not support connecting using the special value
        // '*CURRENT' as the userid/password with the open source Java implementation. Rather than
        // requiring the user to log in somehow to make database queries (used to check for job liveliness),
        // it's a lot easier to use db2util.
        if (!new File("/QOpenSys/pkgs/bin/db2util").canExecute()) {
            _logger.println_err("ERROR: Required tool 'db2util' not installed. Please install this RPM");
            System.exit(-18);
        }
        if (!new File("/QOpenSys/pkgs/bin/nohup").canExecute()) {
            _logger.println_err("ERROR: Required tool 'nohup' not installed. Please install coreutils-gnu RPM");
            System.exit(-19);
        }
    }

    private static ServiceDefinition getAdHocServiceDef(final String _desc, final Map<String, ServiceDefinition> _configuredServiceDefs, final AppLogger _logger) {
        final CheckAliveType caType = _desc.toLowerCase().trim().startsWith("port:") ? CheckAliveType.PORT : CheckAliveType.JOBNAME;
        final String caCriteria = _desc.toLowerCase().trim().replaceFirst(".*:", "").trim();
        for (final ServiceDefinition svc : _configuredServiceDefs.values()) {
            if (caType == svc.getCheckAliveType() && caCriteria.equalsIgnoreCase(svc.getCheckAliveCriteria().trim())) {
                _logger.printfln_verbose("Found pre-existing service for ad hoc specs: %s", svc.getFriendlyName());
                return svc;
            }
        }
//@formatter:off
        final String friendlyName = "Ad hoc service running at " + _desc.replace(':', ' ');
        _logger.printfln_verbose("Creating ad hoc service for %s", _desc);
        final String shortName = "ad_hoc_"+_desc.replace(':', '_').replace('/', '_');
        return new ServiceDefinition() {
            @Override public String getCheckAliveCriteria()     { return caCriteria;   }
            @Override public CheckAliveType getCheckAliveType() { return caType;       }
            @Override public String getFriendlyName()           { return friendlyName; }
            @Override public String getName()                   { return shortName;    }
            @Override public String getSource()                 { return "<ad hoc>";   }
            @Override public String getStartCommand()           { return "";           }
        };
//@formatter:on

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

    public static void main(final String... _args) {
        if (_args.length < 2) {
            printUsageAndExit();
        }

        final LinkedList<String> args = new LinkedList<String>(Arrays.asList(_args));
        String service = args.removeLast().trim();
        final String operation = args.removeLast().trim();

        // Process all bool-type arguments, including "-v" to initialize our logger
        System.setProperty(StringUtils.PROP_DISABLE_COLORS, "" + Boolean.valueOf(args.remove("--disable-colors")));
        System.setProperty(OperationExecutor.PROP_BATCHOUTPUT_SPLF, "" + Boolean.valueOf(args.remove("--splf")));
        System.setProperty(YamlServiceDefLoader.PROP_IGNORE_GLOBALS, "" + Boolean.valueOf(args.remove("--ignore-globals")));
        if (args.remove("-h") || args.remove("--help")) {
            printUsageAndExit();
        }
        final AppLogger logger = new AppLogger.DefaultLogger(args.remove("-v"));

        for (final String remainingArg : args) {
            if (remainingArg.startsWith("--sampletime=")) {
                try {
                    final float value = Float.parseFloat(remainingArg.replaceAll(".*=", ""));
                    System.setProperty(OperationExecutor.PROP_SAMPLE_TIME, String.format("%.2f", value));
                } catch (final Exception e) {
                    logger.printfln_warn("WARNING: Value specified for sample time argument is not valid: %s", remainingArg);
                }
            } else {
                logger.printfln_warn("WARNING: Argument '%s' unrecognized and will be ignored", remainingArg);
            }
        }

        logger.println_verbose("Verbose mode enabled");
        logger.println_verbose("--------------------");

        checkApplicationDependencies(logger);

        try {
            final Map<String, ServiceDefinition> serviceDefs = new YamlServiceDefLoader().loadFromYamlFiles(logger);
            final Operation op;
            try {
                op = Operation.valueOfWithAliasing(operation);
            } catch (final IllegalArgumentException e) {
                throw new SCException(logger, e, FailureType.UNSUPPORTED_OPERATION, "Unsupported operation '%s' requested", operation);
            }
            if (service.trim().equalsIgnoreCase("all") && null == serviceDefs.get("all")) { // let "all" be shorthand for "group:all"
                service = "group:all";
            }
            if (service.toLowerCase().startsWith("group:")) {
                performOperationsOnServices(op, getServicesInGroup(service.substring("group:".length()).trim(), serviceDefs, logger), serviceDefs, logger);
            } else if (service.toLowerCase().startsWith("port:") || service.toLowerCase().startsWith("job:")) {
                new OperationExecutor(op, getAdHocServiceDef(service, serviceDefs, logger), serviceDefs, logger).execute();
            } else {
                performOperationsOnServices(op, Collections.singleton(service), serviceDefs, logger);
            }
        } catch (final SCException e) {
            logger.printExceptionStack_verbose(e);
            System.exit(-3);
        }
        logger.println();
    }

    private static void performOperationsOnServices(final Operation _op, final Set<String> _services, final Map<String, ServiceDefinition> _serviceDefs, final AppLogger _logger) throws SCException {
        final Stack<SCException> exceptions = new Stack<SCException>();
        if (!_op.isChangingSystemState()) {
            if (Operation.PERFINFO == _op) { // this one's treated special because it might take a very long time.f
                _logger.println("Gathering performance information...");
            }
            final LinkedHashMap<Thread, AppLogger.DeferredLogger> outputList = new LinkedHashMap<Thread, AppLogger.DeferredLogger>();
            for (final String service : _services) {
                final DeferredLogger deferredLogger = new DeferredLogger(_logger);
                deferredLogger.printf_verbose("Performing operation '%s' on service '%s'\n", _op.name(), service);
                final Thread t = new Thread((Runnable) () -> {
                    try {
                        new OperationExecutor(_op, service, _serviceDefs, deferredLogger).execute();
                    } catch (final SCException e) {
                        exceptions.push(e);
                    }
                }, "PerfInfoThread-" + service);
                outputList.put(t, deferredLogger);
                t.start();
            }
            for (final Entry<Thread, DeferredLogger> output : outputList.entrySet()) {
                try {
                    output.getKey().join();
                    output.getValue().close();
                } catch (final Exception e) {
                    exceptions.push(SCException.fromException(e, _logger));
                }
            }
        } else {
            for (final String service : _services) {
                _logger.printf("Performing operation '%s' on service '%s'\n", _op.name(), service);
                try {
                    final OperationExecutor executioner = new OperationExecutor(_op, service, _serviceDefs, _logger);
                    executioner.execute();
                } catch (final SCException e) {
                    exceptions.push(e);
                }
            }
        }
        if (!exceptions.isEmpty()) {
            throw exceptions.pop();
        }
    }

    private static void printUsageAndExit() {
        // @formatter:off
		final String usage = "Usage: sc  [options] <operation> <service>\n"
		                        + "\n"
		                        + "    Valid options include:\n"
                                + "        -v: verbose mode\n"
                                + "        --disable-colors: disable colored output\n"
                                + "        --splf: send output to *SPLF when submitting jobs to batch (instead of log)\n"
                                + "        --sampletime=x.x: sampling time(s) when gathering performance info (default is 1)\n"
                                + "        --ignore-globals: ignore globally-configured services\n"
                                + "\n"
		                        + "    Valid operations include:\n"
                				+ "        start: start the service (and any dependencies)\n"
                				+ "        stop: stop the service (and dependent services)\n"
                				+ "        restart: restart the service\n"
                                + "        check: check status of the service\n"
                                + "        info: print configuration info about the service\n"
                                + "        jobinfo: print basic performance info about the service\n"
                                + "        perfinfo: print basic performance info about the service\n"
                                + "        loginfo: get log file info for the service (best guess only)\n"
                                + "        list: print service short name and friendly name\n"
                                + "\n"
                                + "    Valid formats of the <service(s)> specifier include:\n"
                                + "        - the short name of a configured service\n"
                                + "        - A special value of \"all\" to represent all configured services (same as \"group:all\")\n"
                                + "        - A group identifier (e.g. \"group:groupname\")\n"
                                + "        - An ad hoc service specification by port (for instance, \"port:8080\")\n"
                                + "        - An ad hoc service specification by job name (for instance, \"job:ZOOKEEPER\")\n"
                                + "        - An ad hoc service specification by subsystem and job name (for instance, \"job:QHTTPSVR/ADMIN2\")\n"
                                ;
		// @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

}
