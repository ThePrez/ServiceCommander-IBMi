package jesseg.ibmi.opensource;

import java.io.File;
import java.text.Collator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.AppLogger.DeferredLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAliveType;
import jesseg.ibmi.opensource.utils.QueryUtils;
import jesseg.ibmi.opensource.yaml.YamlServiceDef;
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
    static void checkApplicationDependencies(final AppLogger _logger) {
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

    private static ServiceDefinition getAdHocServiceDef(final String _desc, final ServiceDefinitionCollection serviceDefs, final AppLogger _logger) {
        final CheckAliveType caType = _desc.toLowerCase().trim().startsWith("port:") ? CheckAliveType.PORT : CheckAliveType.JOBNAME;
        final String caCriteria = _desc.toLowerCase().trim().replaceFirst(".*:", "").trim();
        for (final ServiceDefinition svc : serviceDefs.getServices()) {
            if (svc.getCheckAlives().contains(new ServiceDefinition.SimpleCheckAlive(caType, caCriteria))) {
                _logger.printfln_verbose("Found pre-existing service for ad hoc specs: %s", svc.getFriendlyName());
                return svc;
            }
            for (final ServiceDefinition backend : svc.getClusterBackends()) {
                if (backend.getCheckAlives().contains(new ServiceDefinition.SimpleCheckAlive(caType, caCriteria))) {
                    _logger.printfln_verbose("Found pre-existing service cluster backend for ad hoc specs: %s", backend.getFriendlyName());
                    return backend;
                }
            }
        }
//@formatter:off
        final String friendlyName = "Ad hoc service running at " + _desc.replace(':', ' ');
        _logger.printfln_verbose("Creating ad hoc service for %s", _desc);
        final String shortName = "ad_hoc_"+_desc.replace(':', '_').replace('/', '_');
        return new ServiceDefinition() {
            @Override public List<CheckAlive> getCheckAlives()  {return Collections.singletonList(new SimpleCheckAlive(caType, caCriteria)); }
            @Override public String getFriendlyName()           { return friendlyName; }
            @Override public String getName()                   { return shortName;    }
            @Override public String getSource()                 { return "<ad hoc>";   }
            @Override public String getStartCommand()           { return "";           }
            @Override public boolean isAdHoc()                  { return true;         }
        };
//@formatter:on

    }

    public static void listOpenPorts(final AppLogger _logger, final LinkedList<String> _args) throws SCException {
        try {
            final ServiceDefinitionCollection serviceDefs = new YamlServiceDefLoader().loadFromYamlFiles(_logger, false);
            serviceDefs.checkForCheckaliveConflicts(_logger);
            final List<String> addrs = QueryUtils.getListeningAddrs(_logger, _args.contains("--mine"));
            _logger.println("Address          Port    'sc' service name (and friendly name)");
            _logger.println("---------------  ------  --------------------------------------------");
            for (final String addr : addrs) {
                final Integer port = Integer.valueOf(addr.replaceAll(".*:", ""));
                final String ip = addr.replaceAll(":[^:]+", "");
                final ServiceDefinition svcDef = getAdHocServiceDef("port:" + port, serviceDefs, _logger);
                String line = StringUtils.spacePad(ip, 17);
                line += StringUtils.spacePad("" + port, 8);
                line += svcDef.isClusterBackend() ? "  " : "";
                if (svcDef.isAdHoc()) {
                    line += StringUtils.colorizeForTerminal("port:" + port, TerminalColor.CYAN);
                } else {
                    line += StringUtils.colorizeForTerminal(svcDef.getName(), TerminalColor.CYAN);
                    line += " (" + svcDef.getFriendlyName() + ")";
                }
                _logger.println(line);
            }
        } catch (final Exception e) {
            throw SCException.fromException(e, _logger);
        }
    }
    
    public static void listServiceGroups(ServiceDefinitionCollection serviceDefs, boolean isIgnoreGlobals, AppLogger logger) throws SCException {
    	TreeSet<String> groups = new TreeSet<String>();
    	if (!isIgnoreGlobals) {
    		groups.add("system");
    	}
    	for (ServiceDefinition serviceDefinition : serviceDefs.getServices()) {
    		for (String serviceGroup : serviceDefinition.getGroups()) {
    			groups.add(serviceGroup);
    		}
    	}
    	for (String group : groups) {
    		logger.println(group);
    	}
    }

    private static boolean looksLikeFilename(final String _svc) {
        if (_svc.startsWith("/") || _svc.contains(".")) {
            return true;
        }
        return new File(_svc).canRead();
    }

    public static void main(final String... _args) {
        if (_args.length < 1) {
            printUsageAndExit();
        }

        final LinkedList<String> args = new LinkedList<String>();
        final String optsEnvVar = System.getenv("SC_OPTIONS");
        if (!StringUtils.isEmpty(optsEnvVar)) {
            args.addAll(Arrays.asList(optsEnvVar.trim().split("\\s+")));
        }
        args.addAll(Arrays.asList(_args));

        // Process all bool-type arguments, including "-v" to initialize our logger
        System.setProperty(StringUtils.PROP_DISABLE_COLORS, "" + Boolean.valueOf(args.remove("--disable-colors")));
        System.setProperty(OperationExecutor.PROP_BATCHOUTPUT_SPLF, "" + Boolean.valueOf(args.remove("--splf")));

        // initialize default behaviors for ignoring global (system-wide) settings and ignored groups
        boolean isIgnoreGlobals = false;
        String[] ignoreGroups = new String[] { "system" };

        if (args.remove("-h") || args.remove("--help")) {
            printUsageAndExit();
        }
        final LinkedList<String> nonDashedArgs = new LinkedList<String>();
        final AppLogger logger = new AppLogger.DefaultLogger(args.remove("-v"));
        for (final String remainingArg : args) {
            if (remainingArg.startsWith("--sampletime=")) {
                try {
                    final float value = Float.parseFloat(remainingArg.replaceAll(".*=", ""));
                    System.setProperty(OperationExecutor.PROP_SAMPLE_TIME, String.format("%.2f", value));
                } catch (final Exception e) {
                    logger.printfln_warn("WARNING: Value specified for sample time argument is not valid: %s", remainingArg);
                }
            } else if (remainingArg.equalsIgnoreCase("--ignore-globals")) {
                isIgnoreGlobals = true;
            } else if (remainingArg.equalsIgnoreCase("-q")) {
                logger.setWarningSuppression(true);
            } else if (remainingArg.equalsIgnoreCase("--all") || remainingArg.equalsIgnoreCase("-a")) {
                isIgnoreGlobals = false;
                ignoreGroups = new String[0];
            } else if (remainingArg.startsWith("--ignore-groups=")) {
                try {
                    ignoreGroups = remainingArg.replaceAll(".*=", "").split("\\s*,\\s*");
                } catch (final Exception e) {
                    logger.printfln_warn("WARNING: Value specified for sample time argument is not valid: %s", remainingArg);
                }
            } else if (remainingArg.startsWith("-")) {
                logger.printfln_warn("WARNING: Argument '%s' unrecognized and will be ignored", remainingArg);
            } else {
                nonDashedArgs.add(remainingArg);
            }
        }
        if (nonDashedArgs.isEmpty()) {
            printUsageAndExit();
        }
        final String operation = nonDashedArgs.removeFirst().trim();

        // TODO: push this attributes into the `Operation` enum
        if (0 == nonDashedArgs.size() && (operation.equalsIgnoreCase("check") || operation.equalsIgnoreCase("list") || operation.equalsIgnoreCase("status") || operation.equalsIgnoreCase("groups"))) {
            nonDashedArgs.add("group:all");
        }
        if (0 == nonDashedArgs.size()) {
            printUsageAndExit();
        }
        String service = nonDashedArgs.removeFirst().trim();
        for (final String extraArg : nonDashedArgs) {
            logger.printfln_warn("WARNING: Argument '%s' unrecognized and will be ignored", extraArg);
        }
        logger.println_verbose("Verbose mode enabled");
        logger.println_verbose("--------------------");

        checkApplicationDependencies(logger);

        try {
            final ServiceDefinitionCollection serviceDefs = new YamlServiceDefLoader().loadFromYamlFiles(logger, isIgnoreGlobals);
            if (service.toLowerCase().startsWith("group:")) {
                final String groupName = service.substring("group:".length()).trim();
                for (int i = 0; i < ignoreGroups.length; ++i) {
                    if (ignoreGroups[i].equalsIgnoreCase(groupName)) {
                        ignoreGroups[i] = "<redacted>";
                    }
                }
            }
            serviceDefs.removeServicesInGroup(service, ignoreGroups);
            serviceDefs.checkForCheckaliveConflicts(logger);
            final Operation op;
            try {
                op = Operation.valueOfWithAliasing(operation);
            } catch (final IllegalArgumentException e) {
                throw new SCException(logger, e, FailureType.UNSUPPORTED_OPERATION, "Unsupported operation '%s' requested", operation);
            }
            if (op == Operation.GROUPS) {
            	listServiceGroups(serviceDefs, isIgnoreGlobals, logger);
            } else {
	            if (service.trim().equalsIgnoreCase("all") && null == serviceDefs.get("all")) { // let "all" be shorthand for "group:all"
	                service = "group:all";
	            }
	            if (service.toLowerCase().startsWith("group:")) {
	                final String groupName = service.substring("group:".length()).trim();
	                performOperationsOnServices(op, serviceDefs.getServicesInGroup(groupName, logger), serviceDefs, logger);
	            } else if (service.toLowerCase().startsWith("port:") || service.toLowerCase().startsWith("job:")) {
	                final ServiceDefinition adHoc = getAdHocServiceDef(service, serviceDefs, logger);
	                serviceDefs.put(adHoc);
	                performOperationsOnServices(op, Collections.singleton(adHoc.getName()), serviceDefs, logger);
	            } else if (looksLikeFilename(service)) {
	                final YamlServiceDef def = new YamlServiceDef(null, new File(service), logger);
	                serviceDefs.put(def);
	                performOperationsOnServices(op, Collections.singleton(def.getName()), serviceDefs, logger);
	            } else {
	                performOperationsOnServices(op, Collections.singleton(service), serviceDefs, logger);
	            }
            }
        } catch (final SCException e) {
            logger.printExceptionStack_verbose(e);
            System.exit(-3);
        }
        logger.println();
    }

    private static void performOperationsOnServices(final Operation _op, final Set<String> _services, final ServiceDefinitionCollection _serviceDefs, final AppLogger _logger) throws SCException {
        final Stack<SCException> exceptions = new Stack<SCException>();
        if (!_op.isChangingSystemState()) {
            if (Operation.PERFINFO == _op) { // this one's treated special because it might take a very long time.f
                _logger.println("Gathering performance information...");
            }
            final LinkedHashMap<Thread, AppLogger.DeferredLogger> outputList = new LinkedHashMap<Thread, AppLogger.DeferredLogger>();
            for (final String service : _services) {
                final ServiceDefinition svcDef = _serviceDefs.get(service);
                if (null == svcDef || (Operation.LIST == _op && svcDef.isClusterBackend())) {
                    continue;
                }
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
            _serviceDefs.validateNoCircularDependencies(_logger);
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
                                + "        -q: quiet mode (suppress warnings). Ignored when '-v' is specified\n"
                                + "        --disable-colors: disable colored output\n"
                                + "        --splf: send output to *SPLF when submitting jobs to batch (instead of log)\n"
                                + "        --sampletime=x.x: sampling time(s) when gathering performance info (default is 1)\n"
                                + "        --ignore-globals: ignore globally-configured services\n"
                                + "        --ignore-groups=x,y,z: ignore services in the specified groups (default is 'system')\n"
                                + "        --all/-a: don't ignore any services. Overrides --ignore-globals and --ignore-groups\n"
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
                                + "        groups: print an overview of all groups"
                                + "\n"
                                + "    Valid formats of the <service(s)> specifier include:\n"
                                + "        - the short name of a configured service\n"
                                + "        - A special value of \"all\" to represent all configured services (same as \"group:all\")\n"
                                + "        - A group identifier (e.g. \"group:groupname\")\n"
                                + "        - the path to a YAML file with a service configuration\n"
                                + "        - An ad hoc service specification by port (for instance, \"port:8080\")\n"
                                + "        - An ad hoc service specification by job name (for instance, \"job:ZOOKEEPER\")\n"
                                + "        - An ad hoc service specification by subsystem and job name (for instance, \"job:QHTTPSVR/ADMIN2\")\n"
                                ;
		// @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

}
