package jesseg.ibmi.opensource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAliveType;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.utils.ConsoleQuestionAsker;
import jesseg.ibmi.opensource.utils.StringUtils;
import jesseg.ibmi.opensource.utils.StringUtils.TerminalColor;
import jesseg.ibmi.opensource.yaml.YamlServiceDefLoader;

public class ServiceInit {

    private static String getCurrentDir() {
        try {
            return new File(".").getCanonicalPath();
        } catch (final IOException e) {
            return System.getProperty("user.dir", ".");
        }
    }

    public static void main(final String[] _args) {
        if (0 == _args.length) {
            printUsageAndExit();
        }
        final AppLogger logger = new AppLogger.DefaultLogger(false);
        final ConsoleQuestionAsker console = new ConsoleQuestionAsker();
        final String currentDir = getCurrentDir();
        final boolean isGlobal = console.askBooleanQuestion(logger, "y", "Would you like this service to be available to all users?");
        final String shortName = console.askStringMatchingRegexQuestion(logger, null, "^([a-z\\\\-_0-9]+)$", "lowercase letters, numbers, hyphens, or underscores", "Short name:");
        final String friendlyName = console.askStringMatchingRegexQuestion(logger, null, "^[^\\:]+$", "a string not containing a colon", "Friendly name:");
        final boolean isCurrentDir = console.askBooleanQuestion(logger, "y", "Must the application be started in the current directory (%s)?", currentDir);
        final String startCmd = (1 == _args.length) ? _args[0] : StringUtils.arrayToSpaceSeparatedString(_args);
        final CheckAliveType checkAliveType = console.askEnumQuestion(logger, "How can the application be checked for liveliness?", CheckAliveType.class);
        String checkAliveCriteria;
        if (CheckAliveType.PORT == checkAliveType) {
            checkAliveCriteria = "" + console.askIntQuestion(logger, null, "Which port does your application run on?");
        } else {
            checkAliveCriteria = console.askStringMatchingRegexQuestion(logger, null, "(?i)^[a-z0-9#]+(\\/[a-z0-9#]+){0,1}$", "JOBNAME or SUBSYSTEM/JOBNAME", "What job does your application run in?");
        }
        final ServiceInit si = new ServiceInit(isGlobal, shortName, friendlyName, isCurrentDir ? getCurrentDir() : null, startCmd, checkAliveType, checkAliveCriteria);

        final boolean isBatch = console.askBooleanQuestion(logger, "n", "Will your application need to be submitted to batch?");
        si.setBatch(isBatch);
        if (isBatch) {
            final String jobName = console.askStringQuestion(logger, "", "What job name should be used? (leave blank for default)");
            if (!StringUtils.isEmpty(jobName)) {
                si.setBatchJobName(jobName);
            }
            final String sbmJobOpts = console.askStringQuestion(logger, "", "What custom SBMJOB options should be used? (leave blank for none)");
            if (!StringUtils.isEmpty(sbmJobOpts)) {
                si.setBatchSbmjobOpts(sbmJobOpts);
            }
        }

        si.addGroups(console.askListOfStringsQuestion(logger, "What group(s) would this application be a part of?"));
        si.addDeps(console.askListOfStringsQuestion(logger, "What service(s) does this application rely on?"));

        try {
            si.writeToFile(logger);
            final ServiceDefinitionCollection defs = new YamlServiceDefLoader().loadFromYamlFiles(new AppLogger.DeferredLogger(logger));
            logger.println(StringUtils.colorizeForTerminal("\n\nPrinting information about the newly-defined service", TerminalColor.GREEN));
            new OperationExecutor(Operation.INFO, si.m_shortName, defs, logger).execute();
            defs.checkForCheckaliveConflicts(logger);
        } catch (final SCException e) {
            logger.printExceptionStack_verbose(e);
            System.exit(46);
        }
    }

    private static void printUsageAndExit() {
        System.err.println("usage: scinit <program start command>");
        System.exit(-1);
    }

    private String m_batchJobName = null;
    private final String m_checkAliveCriteria;
    private final CheckAliveType m_checkAliveType;
    private final List<String> m_dependencies = new LinkedList<String>();
    private final String m_dir;
    private final String m_friendlyName;
    private final List<String> m_groups = new LinkedList<String>();
    private boolean m_isBatch = false;
    private final boolean m_isGlobal;
    private String m_sbmjobOpts = null;
    private final String m_shortName;
    private final String m_startCmd;
    private final String m_stopCmd = null;

    private ServiceInit(final boolean _isGlobal, final String _shortName, final String _friendlyName, final String _dir, final String _startCmd, final CheckAliveType _checkAliveType, final String _checkAliveCriteria) {
        m_isGlobal = _isGlobal;
        m_shortName = _shortName;
        m_dir = _dir;
        m_startCmd = _startCmd;
        m_checkAliveType = _checkAliveType;
        m_checkAliveCriteria = _checkAliveCriteria;
        m_friendlyName = _friendlyName;
    }

    private void addDeps(final List<String> _deps) {
        m_dependencies.addAll(_deps);
    }

    private void addGroups(final List<String> _groups) {
        m_groups.addAll(_groups);
    }

    private File getStorageDir() {
        if (m_isGlobal) {
            return AppDirectories.conf.getGlobalServicesDir();
        }
        return AppDirectories.conf.getUserServicesDir(true);
    }

    private void setBatch(final boolean _isBatch) {
        m_isBatch = _isBatch;
    }

    private void setBatchJobName(final String _jobName) {
        m_batchJobName = _jobName;
    }

    private void setBatchSbmjobOpts(final String _sbmJobOpts) {
        m_sbmjobOpts = _sbmJobOpts;
    }

    public void writeToFile(final AppLogger _logger) throws SCException {

        final LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("name", m_friendlyName);
        if (!StringUtils.isEmpty(m_dir)) {
            data.put("dir", m_dir);
        }
        data.put("start_cmd", m_startCmd);
        if (!StringUtils.isEmpty(m_stopCmd)) {
            data.put("stop_cmd", m_stopCmd);
        }
        data.put("check_alive", m_checkAliveType.name().toLowerCase());
        data.put("check_alive_criteria", m_checkAliveCriteria);

        data.put("batch_mode", "" + m_isBatch);
        if (m_isBatch) {
            if (!StringUtils.isEmpty(m_batchJobName)) {
                data.put("sbmjob_jobname", m_batchJobName);
            }
            if (!StringUtils.isEmpty(m_sbmjobOpts)) {
                data.put("sbmjob_opts", m_sbmjobOpts);
            }
        }

        if (!m_dependencies.isEmpty()) {
            data.put("service_dependencies", m_dependencies.toArray(new String[0]));
        }

        if (!m_groups.isEmpty()) {
            data.put("groups", m_groups.toArray(new String[0]));
        }

        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        final File destination = new File(getStorageDir(), m_shortName + ".yml");
        if (destination.exists()) {
            throw new SCException(_logger, FailureType.GENERAL_ERROR, "ERROR: destination file %s already exists", destination.getAbsolutePath());
        }
        try (final FileWriter output = new FileWriter(destination)) {
            new Yaml(options).dump(data, output);
            _logger.printfln("Written to file: %s", destination.getAbsolutePath());
        } catch (final IOException e) {
            throw new SCException(_logger, e, FailureType.GENERAL_ERROR, e.getLocalizedMessage());
        }
    }

}
