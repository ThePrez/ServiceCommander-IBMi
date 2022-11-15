package jesseg.ibmi.opensource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ConsoleQuestionAsker;
import com.github.theprez.jcmdutils.StringUtils;
import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.BatchMode;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAlive;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAliveType;
import jesseg.ibmi.opensource.nginx.NginxConf;
import jesseg.ibmi.opensource.nginx.NginxConfNode;
import jesseg.ibmi.opensource.utils.ListUtils;
import jesseg.ibmi.opensource.utils.ProcessLauncher;
import jesseg.ibmi.opensource.utils.QueryUtils;
import jesseg.ibmi.opensource.utils.QueryUtils.DspJobDottedAttr;
import jesseg.ibmi.opensource.utils.SbmJobScript;
import jesseg.ibmi.opensource.utils.ColorSchemeConfig.ColorScheme;
import jesseg.ibmi.opensource.utils.ColorSchemeConfig;

/**
 * Where all the work happens
 *
 * @author Jesse Gorzinski
 */
public class OperationExecutor {

    public enum Operation {
        CHECK(false), FILE(false), GROUPS(false), INFO(false), JOBINFO(false), KILL(true), LIST(false), LOGINFO(false), PERFINFO(false), RELOAD(true), RESTART(true), SCRUNATTRS(false), START(true), STOP(true);
        public static Operation valueOfWithAliasing(final String _opStr) {
            final String lookupStr = _opStr.trim().toUpperCase();
            if (lookupStr.equals("STATUS")) {
                return CHECK;
            }
            return valueOf(lookupStr);
        }

        private final boolean m_isChangingSystemState;

        Operation(final boolean _isChangingSystemState) {
            m_isChangingSystemState = _isChangingSystemState;
        }

        public boolean isChangingSystemState() {
            return m_isChangingSystemState;
        }
    }

    static class PerfInfoFetcher extends Thread {
        private SCException m_exc = null;
        private final String m_eyecatcher;
        private final String m_job;
        private final AppLogger m_logger;
        protected LinkedHashMap<String, String> m_res = new LinkedHashMap<String, String>();
        private final float m_sampleTime;

        public PerfInfoFetcher(final String _job, final String _eyecatcher, final AppLogger _logger, final float _sampleTime) {
            super("PerformanceInfo-" + _job);
            m_job = _job;
            this.m_logger = _logger;
            m_sampleTime = _sampleTime;
            m_eyecatcher = _eyecatcher;
            start();
        }

        public Map<String, String> getResults() throws SCException {
            try {
                join();
            } catch (final InterruptedException e) {
                throw SCException.fromException(e, m_logger);
            }
            if (null != m_exc) {
                throw m_exc;
            }
            return m_res;
        }

        @Override
        public void run() {
            try {
                for (final DspJobDottedAttr l : QueryUtils.getJobDspJobDotted(m_job, "*RUNA", m_logger)) {
                    final String key;
                    if (StringUtils.isEmpty(l.getKeyword())) {
                        key = l.getDescription();
                    } else {
                        key = String.format("%s (%s)", l.getDescription(), l.getKeyword());
                    }
                    final String value = (StringUtils.isEmpty(l.getValue())) ? "" : l.getValue();
                    m_res.put(key, value);
                }
            } catch (final Exception e) {
                m_exc = SCException.fromException(e, m_logger);
            }
            try {
                m_res.putAll(QueryUtils.getJobPerfInfo(m_job, m_logger, m_sampleTime));
            } catch (final Exception e) {
                m_logger.println_warn("Could not gather all performance info (Job no longer exists or requisites not installed?)");
                m_logger.printExceptionStack_verbose(e);
            }
        }
    }

    private static class ServiceStatusInfo {
        public enum Status {
            NOT_RUNNING, PARTIALLY_RUNNING, RUNNING;

        }

        private final List<CheckAlive> m_allList = new LinkedList<CheckAlive>();
        private final List<CheckAlive> m_notRunningList = new LinkedList<CheckAlive>();
        private final List<CheckAlive> m_runningList = new LinkedList<CheckAlive>();

        public Status getStatus() {
            if (0 == m_runningList.size()) {
                return Status.NOT_RUNNING;
            } else if (m_notRunningList.isEmpty() && !m_runningList.isEmpty()) {
                return Status.RUNNING;
            }
            return Status.PARTIALLY_RUNNING;
        }

        public boolean isPartial() {
            return Status.PARTIALLY_RUNNING == getStatus();
        }

        public boolean isRunning() {
            return Status.RUNNING == getStatus();
        }

        public boolean isStopped() {
            return Status.NOT_RUNNING == getStatus();
        }
    }

    static final String PROP_BATCHOUTPUT_SPLF = "sc.batchoutput.splf";

    static final String PROP_SAMPLE_TIME = "sc.perfsamplingtime";

    private final static Pattern s_userNameInClPattern = Pattern.compile("^.*user\\s*\\(\\s*([a-z0-9$#@]+)\\s*\\).*$", Pattern.CASE_INSENSITIVE);

    private static boolean isEnvvarProhibitedFromInheritance(final String _var) {
        final List<String> prohibited = Arrays.asList("LIBPATH", "LD_LIBRARY_PATH", "JAVA_HOME", "SSH_TTY", "SSH_CLIENT", "SSH_CONNECTION", "SHELL", "SHLVL");
        return prohibited.contains(_var);
    }

    private final AppLogger m_logger;

    private final ServiceDefinition m_mainService;

    private final Operation m_op;

    private final ServiceDefinitionCollection m_serviceDefs;

    public OperationExecutor(final Operation _op, final ServiceDefinition _service, final ServiceDefinitionCollection _serviceDefs, final AppLogger _logger) {
        m_op = _op;
        m_serviceDefs = _serviceDefs;
        m_logger = _logger;
        m_mainService = _service;
    }

    public OperationExecutor(final Operation _op, final String _service, final ServiceDefinitionCollection _serviceDefs, final AppLogger _logger) throws SCException {
        this(_op, _serviceDefs.get(_service), _serviceDefs, _logger);
        if (null == m_mainService) {
            throw new SCException(m_logger, FailureType.MISSING_SERVICE_DEF, "Could not find definition for service '%s'", _service);
        }
    }

    public ScLogFile execute() throws SCException {
        final ScLogFile logFile = new ScLogFile(m_logger, m_op, m_mainService, getRuntimeUser());
        if (m_logger.isVerbose()) {
            logFile.tail(m_logger);
        }
        try {
            switch (m_op) {
                case START:
                    startService(logFile);
                    return logFile;
                case STOP:
                    stopService(logFile, false);
                    return logFile;

                case KILL:
                    stopService(logFile, true);
                    return logFile;
                case RELOAD:
                    reloadService(logFile);
                    return logFile;
                case CHECK:
                    printServiceStatus();
                    return null;
                case LIST:
                    m_logger.printf("%s (%s)\n", StringUtils.colorizeForTerminal(m_mainService.getName(), ColorSchemeConfig.get("INFO")), m_mainService.getFriendlyName());
                    return null;
                case INFO:
                    printInfo();
                    return null;
                case FILE:
                    printFile();
                    return null;
                case PERFINFO:
                    printPerfInfo();
                    return null;
                case JOBINFO:
                    printJobInfo();
                    return null;
                case LOGINFO:
                    printLogInfo();
                    return null;
                case SCRUNATTRS:
                    printRunAttrs();
                    return null;
                case RESTART:
                    stopService(logFile, false);
                    startService(logFile);
                    return logFile;
                default:
                    throw new SCException(m_logger, FailureType.UNSUPPORTED_OPERATION, "Unsupported operation %s", m_op.name());
            }
        } catch (final Exception e) {
            if (e instanceof SCException) {
                throw (SCException) e;
            }
            throw new SCException(m_logger, e, FailureType.GENERAL_ERROR, "A general error has occurred: %s", e.getLocalizedMessage());
        } finally {
            if (null != logFile) {
                if (0 < logFile.length()) {
                    m_logger.println("For details, see log file at: " + StringUtils.colorizeForTerminal(logFile.getAbsolutePath(), ColorSchemeConfig.get("INFO")));
                }
            }
        }
    }

    private List<ServiceDefinition> findKnownDependents() {
        final List<ServiceDefinition> ret = new LinkedList<ServiceDefinition>();
        for (final ServiceDefinition entry : m_serviceDefs.getServices()) {
            for (final String entryDependency : entry.getDependencies()) {
                if (entryDependency.equalsIgnoreCase(m_mainService.getName())) {
                    ret.add(entry);
                    continue;
                }
            }
        }
        return ret;
    }

    private List<String> getActiveClusterBackendJobsForService() throws SCException {
        try {
            final List<String> ret = new LinkedList<String>();
            for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
                for (final CheckAlive checkalive : backend.getCheckAlives()) {
                    if (CheckAliveType.PORT == checkalive.getType()) {
                        ret.addAll(QueryUtils.getListeningJobsByPort(checkalive.getValue(), m_logger));
                    } else {
                        ret.addAll(QueryUtils.getJobs(checkalive.getValue(), m_logger));
                    }
                }
            }
            return ListUtils.deduplicate(ret);
        } catch (final IOException ioe) {
            throw new SCException(m_logger, ioe, FailureType.ERROR_CHECKING_STATUS, "Error occurred while checking status of service '%s': %s", m_mainService.getFriendlyName(), ioe.getLocalizedMessage());
        } catch (final NumberFormatException nfe) {
            throw new SCException(m_logger, nfe, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", m_mainService.getFriendlyName(), m_mainService.getCheckAlivesHumanReadable());
        }
    }

    private List<String> getActiveJobsForService(final boolean _includeClusterBackends) throws SCException {
        try {
            final List<String> ret = new LinkedList<String>();
            for (final CheckAlive checkalive : m_mainService.getCheckAlives()) {
                if (CheckAliveType.PORT == checkalive.getType()) {
                    ret.addAll(QueryUtils.getListeningJobsByPort(checkalive.getValue(), m_logger));
                } else {
                    ret.addAll(QueryUtils.getJobs(checkalive.getValue(), m_logger));
                }
            }
            if (_includeClusterBackends) {
                ret.addAll(getActiveClusterBackendJobsForService());
            }
            return ListUtils.deduplicate(ret);
        } catch (final IOException ioe) {
            throw new SCException(m_logger, ioe, FailureType.ERROR_CHECKING_STATUS, "Error occurred while checking status of service '%s': %s", m_mainService.getFriendlyName(), ioe.getLocalizedMessage());
        } catch (final NumberFormatException nfe) {
            throw new SCException(m_logger, nfe, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", m_mainService.getFriendlyName(), m_mainService.getCheckAlivesHumanReadable());
        }
    }

    private String getBash() {
        final String scbash = "/QOpenSys/pkgs/lib/sc/native/scbash";
        if (new File(scbash).canExecute()) {
            return scbash;
        }
        m_logger.println_warn_verbose("WARNING: cannot find 'scbash' utility");
        return "/QOpenSys/pkgs/bin/bash";
    }

    private String getBatchUser() {
        if (!m_mainService.getBatchMode().isBatch()) {
            return null;
        }
        final String sbmJobOpts = m_mainService.getSbmJobOpts();
        // based on doc at https://www.ibm.com/docs/en/i/7.4?topic=version-user-profile-name-considerations
        final Pattern p = s_userNameInClPattern;
        final Matcher m = p.matcher(sbmJobOpts);
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    private String getClusterLocation() {
        return "/";// TODO: make this configurable? Move code into ServiceDefinition?
    }

    private String getCurrentUser() throws SCException {
        final String currentUser = System.getProperty("user.name");
        if (StringUtils.isNonEmpty(currentUser)) {
            return currentUser;
        }
        throw new SCException(m_logger, FailureType.GENERAL_ERROR, "ERROR: Unable to determine current user ID!");
    }

    public String getPossibleLogFile() {
        final File logDir = AppDirectories.conf.getLogsDirectory();
        File latest = null;
        for (final File logFile : logDir.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".log"))) {
            if (null == latest) {
                latest = logFile;
            } else {
                if (latest.lastModified() < logFile.lastModified()) {
                    latest = logFile;
                }
            }
        }
        m_logger.printf_verbose("possible log file is %s\n", null == latest ? "<null>" : latest.getAbsolutePath());
        return null == latest ? null : latest.getAbsolutePath();
    }

    private String getRuntimeUser() throws SCException {
        final String batchUser = getBatchUser();
        if (StringUtils.isNonEmpty(batchUser)) {
            return batchUser;
        }
        return getCurrentUser();
    }

    private String getSbmJobOptsForStopping() {
        String startOpts = m_mainService.getSbmJobOpts();
        startOpts = startOpts.replaceAll("(?i)jobq\\s*\\([^\\\\)]+\\)", " ");
        startOpts = startOpts.replaceAll("(?i)hold\\s*\\([^\\\\)]+\\)", " ");
        if (!isLikelyRunningAsAnotherUser()) {
            return "JOBQ(QUSRNOMAX) HOLD(*NO)";
        }
        final String userParm = m_mainService.getSbmJobOpts().replaceFirst("(?i).*user\\s*\\(", "USER(").replaceAll("\\).*", ")");
        return userParm + " JOBQ(QUSRNOMAX) HOLD(*NO)";
    }

    public ServiceStatusInfo getServiceStatus() throws SCException {
        try {
            final ServiceStatusInfo ret = new ServiceStatusInfo();
            final List<CheckAlive> checkalives = new LinkedList<CheckAlive>();
            checkalives.addAll(m_mainService.getCheckAlives());
            if (checkalives.isEmpty()) {
                throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", m_mainService.getFriendlyName(), m_mainService.getCheckAlivesHumanReadable());
            }
            for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
                checkalives.addAll(backend.getCheckAlives());
            }
            for (final CheckAlive checkalive : checkalives) {
                ret.m_allList.add(checkalive);
                final boolean isRunning;
                if (CheckAliveType.PORT == checkalive.getType()) {
                    isRunning = QueryUtils.isListeningOnPort(checkalive.getValue(), m_logger);
                } else if (CheckAliveType.JOBNAME == checkalive.getType()) {
                    isRunning = QueryUtils.isJobRunning(checkalive.getValue(), m_logger);
                } else {
                    throw new SCException(m_logger, FailureType.UNSUPPORTED_OPERATION, "Unsupported operation has been requested");
                }
                if (isRunning) {
                    ret.m_runningList.add(checkalive);
                } else {
                    ret.m_notRunningList.add(checkalive);
                }
            }
            return ret;
        } catch (final IOException ioe) {
            throw new SCException(m_logger, ioe, FailureType.ERROR_CHECKING_STATUS, "Error occurred while checking status of service '%s': %s", m_mainService.getFriendlyName(), ioe.getLocalizedMessage());
        } catch (final NumberFormatException nfe) {
            throw new SCException(m_logger, nfe, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", m_mainService.getFriendlyName(), m_mainService.getCheckAlivesHumanReadable());
        }
    }

    public List<String> getSpooledFiles() {
        final LinkedList<String> ret = new LinkedList<String>();
        return ret;

    }

    private boolean isLikelyRunningAsAnotherUser() {
        final String batchUser = getBatchUser();
        if (StringUtils.isEmpty(batchUser)) {
            return false;
        }
        return !batchUser.trim().equalsIgnoreCase(System.getProperty("user.name"));
    }

    private void populateNginxConfFile(final List<ServiceDefinition> _ignoredBackends, final boolean _callNginxReloadWhenDone) throws UnsupportedEncodingException, FileNotFoundException, IOException, InterruptedException {
        // TODO: properly synchronize this method
        final File nginxConf = new File(m_mainService.getEffectiveWorkingDirectory(), "cluster.conf");
        if (!nginxConf.canRead()) {
            try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(nginxConf), "UTF-8")) {
                //@formatter:off
                final String fileContents = String.format("pid nginx.pid;\n" +
                        "events {}\n" +
                        "stream {\n" +
                        "  error_log logs/error.log warn;\n" +
                        "  upstream sc_servers {\n" +
                        "  }\n" +
                        "  server {\n" +
                        "    listen %s  backlog=8096;\n" +
                        "      proxy_pass sc_servers;\n" +
                        "  }\n" +
                        "}", m_mainService.getCheckAlives().get(0).toString());
                //@formatter:on
                out.write(fileContents);
            }
        }
        final NginxConf conf = new NginxConf(nginxConf, m_logger);
        final List<String> upstreams = new LinkedList<String>();
        final List<ServiceDefinition> ignoredBackends = (null == _ignoredBackends) ? new LinkedList<ServiceDefinition>() : _ignoredBackends;
        for (final ServiceDefinition backendSvc : m_mainService.getClusterBackends()) {
            if (ignoredBackends.contains(backendSvc)) {
                m_logger.println_verbose("Ignoring backend: " + backendSvc.getName());
                continue;
            }
            m_logger.println_verbose("Processing backend: " + backendSvc.getName());
            final String upstream = "127.0.0.1:" + backendSvc.getCheckAlives().get(0).getValue();
            m_logger.println_verbose("Adding upstream: " + upstream);
            upstreams.add(upstream);
        }

        final String streamOrHttp = conf.getRoot().getChildren("stream").isEmpty() ? "http" : "stream";
        conf.overwrite(new String[] { streamOrHttp, "upstream sc_servers" }, "server", upstreams, true);
        if ("http".equals(streamOrHttp)) {
            conf.overwrite(new String[] { "http", "server", "location " + getClusterLocation() }, "proxy_pass", Collections.singletonList("http://sc_servers"), true);
            conf.overwrite(new String[] { "http", "server", }, "proxy_pass", null, true);
            conf.remove("stream");
        } else {
            conf.remove("stream", "server", "location /");
            conf.overwrite(new String[] { "stream", "server" }, "proxy_pass", Collections.singletonList("sc_servers"), true);
            conf.remove("http");
        }
        String portValue = m_mainService.getCheckAlives().get(0).getValue();
        if ("http".equalsIgnoreCase(streamOrHttp)) {
            try {
                final List<NginxConfNode> l = conf.getRoot().getChild("http").getChildren("server");
                if (1 < l.size()) {
                    portValue = "localhost:" + portValue.trim();
                }
            } catch (final Exception e) {
                m_logger.printExceptionStack_verbose(e);
            }
        }
        conf.overwrite(new String[] { streamOrHttp, "server" }, "listen", Collections.singletonList("" + portValue + "  backlog=8096"), true);

        try (PrintWriter ps = new PrintWriter(nginxConf, "UTF-8")) {
            conf.writeData(ps, 0);
        }
        final File logsDir = new File(nginxConf.getParentFile(), "logs");

        if (!logsDir.isDirectory() && !logsDir.mkdir()) {
            throw new IOException("Could not create log dir");
        }
        logsDir.setWritable(true);
        if (_callNginxReloadWhenDone) {
            final String reloadCmd = "/QOpenSys/pkgs/bin/nginx -p $(pwd) -c $(pwd)/cluster.conf -s reload";
            final File directory = new File(m_mainService.getEffectiveWorkingDirectory());
            final Process p = Runtime.getRuntime().exec(new String[] { getBash(), "-c", reloadCmd }, null, directory);
            ProcessLauncher.pipeStreamsToCurrentProcess("nginx reload", p, m_logger);
            final int rc = p.waitFor();
            if (0 != rc) {
                throw new IOException("nginx command returned error");
            }
            m_logger.println("Cluster configuration refreshed");
        }
    }

    private void printFile() {
        if (m_mainService.isAdHoc()) {
            return;
        }
        m_logger.println(m_mainService.getSource());
    }

    private void printInfo() {
        m_logger.println();
        m_logger.println();
        m_logger.println(StringUtils.colorizeForTerminal("---------------------------------------------------------------------", ColorSchemeConfig.get("PLAIN")));
        m_logger.println(StringUtils.colorizeForTerminal(m_mainService.getName(), ColorSchemeConfig.get("INFO")) + " (" + m_mainService.getFriendlyName() + ")");
        m_logger.println();
        m_logger.println();
        m_logger.println(StringUtils.colorizeForTerminal("Defined in: ", ColorSchemeConfig.get("INFO")) + m_mainService.getSource());
        m_logger.println();
        final String dir = m_mainService.getConfiguredWorkingDirectory();
        if (!StringUtils.isEmpty(dir)) {
            m_logger.println(StringUtils.colorizeForTerminal("Working Directory: ", ColorSchemeConfig.get("INFO")) + dir);
            m_logger.println();
        }
        m_logger.println(StringUtils.colorizeForTerminal("Startup Command: ", ColorSchemeConfig.get("INFO")) + m_mainService.getStartCommand());
        m_logger.println(StringUtils.colorizeForTerminal("Startup Wait Time (s): ", ColorSchemeConfig.get("INFO")) + m_mainService.getStartupWaitTime());
        m_logger.println();
        final String shutdownCommand = m_mainService.getStopCommand();
        if (!StringUtils.isEmpty(shutdownCommand)) {
            m_logger.println(StringUtils.colorizeForTerminal("Shutdown Command: ", ColorSchemeConfig.get("INFO")) + shutdownCommand);
        }
        m_logger.println(StringUtils.colorizeForTerminal("Shutdown Wait Time (s): ", ColorSchemeConfig.get("INFO")) + m_mainService.getShutdownWaitTime());
        m_logger.println();
        m_logger.println(StringUtils.colorizeForTerminal("Check-alive conditions: ", ColorSchemeConfig.get("INFO")) + m_mainService.getCheckAlivesHumanReadable());
        final BatchMode batchMode = m_mainService.getBatchMode();
        if (BatchMode.NO_BATCH == batchMode) {
            m_logger.println(StringUtils.colorizeForTerminal("Batch Mode: ", ColorSchemeConfig.get("INFO")) + "<not running in batch>");
        } else {
            m_logger.println(StringUtils.colorizeForTerminal("Batch Mode: ", ColorSchemeConfig.get("INFO")) + "<submitted to batch>");
            String batchJobName = m_mainService.getBatchJobName();
            if (StringUtils.isEmpty(batchJobName)) {
                batchJobName = "<default>";
            }
            m_logger.println(StringUtils.colorizeForTerminal("    Batch Job Name: ", ColorSchemeConfig.get("INFO")) + batchJobName);
            final String sbmjobOpts = m_mainService.getSbmJobOpts();
            if (!StringUtils.isEmpty(sbmjobOpts)) {
                m_logger.println(StringUtils.colorizeForTerminal("    SBMJOB options: ", ColorSchemeConfig.get("INFO")) + sbmjobOpts);
            }
        }
        final List<String> dependencies = m_mainService.getDependencies();
        if (!dependencies.isEmpty()) {
            m_logger.println();
            m_logger.println(StringUtils.colorizeForTerminal("Depends on the following services:", ColorSchemeConfig.get("INFO")));
            for (final String dependency : dependencies) {
                m_logger.println("    " + dependency);
            }
        }
        m_logger.println();
        m_logger.println(StringUtils.colorizeForTerminal("Inherits environment variables?: ", ColorSchemeConfig.get("INFO")) + m_mainService.isInheritingEnvironmentVars());
        final List<String> envVars = m_mainService.getEnvironmentVars();
        if (!envVars.isEmpty()) {
            m_logger.println(StringUtils.colorizeForTerminal("Custom environment variables:", ColorSchemeConfig.get("INFO")));
            for (final String envVar : envVars) {
                m_logger.println("    " + envVar);
            }
        }
        m_logger.println("---------------------------------------------------------------------");
        m_logger.println();
        m_logger.println();
    }

    private void printJobInfo() throws SCException, IOException {
        m_logger.println(StringUtils.colorizeForTerminal(m_mainService.getName(), ColorSchemeConfig.get("INFO")) + " (" + m_mainService.getFriendlyName() + "):");
        final List<String> jobs = getActiveJobsForService(false);
        if (jobs.isEmpty()) {
            m_logger.println("    " + StringUtils.colorizeForTerminal("NO JOB INFO (either not running, or running in kernel task)", ColorSchemeConfig.get("NOT_RUNNING")));
        } else {
            for (final String job : jobs) {
                m_logger.println("    " + job);
            }
        }
        for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
            m_logger.printf_verbose("Attempting to get jobinfo for backend job '%s'...\n", backend.getFriendlyName());
            try {
                new OperationExecutor(Operation.JOBINFO, backend.getName(), m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.GENERAL_ERROR, "ERROR: Could not get job info for backend job '%s' for cluster mode: %s", backend.getFriendlyName(), e.getLocalizedMessage());
            }
        }
    }

    private void printLogInfo() throws SCException {

        for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
            m_logger.printf_verbose("Attempting to get loginfo for backend job '%s'...\n", backend.getFriendlyName());
            try {
                new OperationExecutor(Operation.LOGINFO, backend.getName(), m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.GENERAL_ERROR, "ERROR: Could not get log info for backend job '%s' for cluster mode: %s", backend.getFriendlyName(), e.getLocalizedMessage());
            }
        }
        boolean isAnythingFound = false;
        final List<String> jobs = getActiveJobsForService(false);
        for (final String job : jobs) {
            try {
                for (final String splf : QueryUtils.getSplfsForJob(job, m_logger)) {
                    m_logger.println(m_mainService.getName() + ": " + StringUtils.colorizeForTerminal(splf, ColorSchemeConfig.get("INFO")));
                    isAnythingFound = true;
                }
            } catch (final Exception e) {
                m_logger.printExceptionStack_verbose(e);
            }
        }
        final Set<String> logFileCandidates = new HashSet<String>();
        for (final String job : jobs) {
            try {
                final String logFilePath = QueryUtils.getLogfileForJob(job, m_logger);
                if (StringUtils.isNonEmpty(logFilePath)) {
                    logFileCandidates.add(logFilePath);
                    isAnythingFound = true;
                }
            } catch (final Exception e) {
                m_logger.printExceptionStack_verbose(e);
            }
        }

        for (final String candidate : logFileCandidates) {

            final File logFile = new File(candidate);
            if (0 < logFile.length()) {
                m_logger.println(m_mainService.getName() + ": " + StringUtils.colorizeForTerminal(candidate, ColorSchemeConfig.get("INFO")));
                isAnythingFound = true;
            } else {
                m_logger.println(m_mainService.getName() + ": " + StringUtils.colorizeForTerminal(candidate, ColorSchemeConfig.get("INFO")) + StringUtils.colorizeForTerminal(" (no data)", ColorSchemeConfig.get("WARNING")));
            }
        }
        if (!isAnythingFound) {
            final ScLogFile f = new ScLogFile(m_logger, m_op, m_mainService, getRuntimeUser());
            final String iDunno = StringUtils.getShrugForOutput() + " (try checking in log directory " + f.getParentFile().getAbsolutePath() + ")";
            m_logger.printfln_err("%s: %s", m_mainService.getName(), iDunno);
        }
    }

    private void printPerfInfo() throws SCException, IOException {
        m_logger.println();
        m_logger.println(StringUtils.colorizeForTerminal("---------------------------------------------------------------------", ColorSchemeConfig.get("PLAIN")));

        m_logger.println(StringUtils.colorizeForTerminal(m_mainService.getName(), ColorSchemeConfig.get("INFO")) + " (" + m_mainService.getFriendlyName() + ")");
        final List<String> jobs = getActiveJobsForService(false);
        if (jobs.isEmpty()) {
            m_logger.println(StringUtils.colorizeForTerminal("NOT RUNNING", ColorSchemeConfig.get("NOT_RUNNING")));
        } else {
            m_logger.println();
            final List<PerfInfoFetcher> dataFetcherThreads = new LinkedList<PerfInfoFetcher>();
            for (final String job : jobs) {
                dataFetcherThreads.add(new PerfInfoFetcher(job, "Job", m_logger, Float.parseFloat(System.getProperty(PROP_SAMPLE_TIME, "1.0"))));
            }
            for (final String job : getActiveClusterBackendJobsForService()) {
                dataFetcherThreads.add(new PerfInfoFetcher(job, "Backend job", m_logger, Float.parseFloat(System.getProperty(PROP_SAMPLE_TIME, "1.0"))));
            }
            for (final PerfInfoFetcher dataFetcherThread : dataFetcherThreads) {
                m_logger.println(StringUtils.colorizeForTerminal(dataFetcherThread.m_eyecatcher + ": " + dataFetcherThread.m_job, ColorSchemeConfig.get("INFO")));
                final Map<String, String> perfInfo = dataFetcherThread.getResults();
                for (final Entry<String, String> pi : perfInfo.entrySet()) {
                    m_logger.println("    " + StringUtils.colorizeForTerminal(pi.getKey(), ColorSchemeConfig.get("INFO")) + ": " + pi.getValue());
                }
                m_logger.println();
            }
        }
        m_logger.println("---------------------------------------------------------------------");
        m_logger.println();
    }

    private void printRunAttrs() throws SCException {
        for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
            m_logger.printf_verbose("Attempting to get env for backend job '%s'...\n", backend.getFriendlyName());
            try {
                new OperationExecutor(Operation.SCRUNATTRS, backend.getName(), m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.GENERAL_ERROR, "ERROR: Could not get env for backend job '%s' for cluster mode: %s", backend.getFriendlyName(), e.getLocalizedMessage());
            }
        }
        final List<String> jobs = getActiveJobsForService(false);
        if (jobs.isEmpty()) {
            throw new SCException(m_logger, FailureType.GENERAL_ERROR, "ERROR: No running jobs for service '%s'", m_mainService.getName());
        }
        for (final String job : jobs) {
            final Map<String, String> envMap = QueryUtils.getJobEnvvars(job, m_logger);
            m_logger.println(StringUtils.colorizeForTerminal(m_mainService.getName() + ": " + job + ":", ColorSchemeConfig.get("INFO")));
            boolean isAnyFound = false;
            for (final Entry<String, String> l : envMap.entrySet()) {
                if (l.getKey().startsWith("SCOMMANDER_")) {
                    m_logger.printfln("    %s: %s", l.getKey().replaceFirst("^SCOMMANDER_", "").replace('_', ' ').toLowerCase(), l.getValue());
                    isAnyFound = true;
                }
            }
            if (!isAnyFound) {
                m_logger.printfln_err("    %s", StringUtils.getShrugForOutput());
            }
        }
    }

    private void printServiceStatus() throws NumberFormatException, IOException, SCException {
        final ServiceStatusInfo status = getServiceStatus();
        final String paddedStatusString;
        final String indent = m_mainService.isClusterBackend() ? "  " : "";
        TerminalColor statusColor = ColorSchemeConfig.get("STATUS");
        final int statusPadSize = 18;
        switch (status.getStatus()) {
            case RUNNING:
                paddedStatusString = StringUtils.colorizeForTerminal(StringUtils.spacePad(indent + "RUNNING", statusPadSize), statusColor = ColorSchemeConfig.get("RUNNING"));
                break;
            case NOT_RUNNING:
                paddedStatusString = StringUtils.colorizeForTerminal(StringUtils.spacePad(indent + "NOT RUNNING", statusPadSize), statusColor = ColorSchemeConfig.get("NOT_RUNNING"));
                break;
            default:
                final String statusString = String.format("" + indent + "PARTIAL (%d/%d)", status.m_runningList.size(), status.m_allList.size());
                paddedStatusString = StringUtils.colorizeForTerminal(StringUtils.spacePad(statusString, statusPadSize), statusColor = ColorSchemeConfig.get("WARNING"));
                break;
        }
        String partialInfo = "";
        if (status.isPartial()) {
            partialInfo += StringUtils.colorizeForTerminal("[not running at -->" + ListUtils.toString(status.m_notRunningList, ", ") + "]", ColorSchemeConfig.get("WARNING"));
        }
        m_logger.printfln("  %s | %s%s (%s) %s", paddedStatusString, indent, StringUtils.colorizeForTerminal(m_mainService.getName(), statusColor), m_mainService.getFriendlyName(), partialInfo);
    }

    private void reloadService(final ScLogFile _logFile) throws SCException, UnsupportedEncodingException, FileNotFoundException, IOException, InterruptedException {
        final List<ServiceDefinition> backends = m_mainService.getClusterBackends();
        if (2 > backends.size()) {
            throw new SCException(m_logger, FailureType.GENERAL_ERROR, "ERROR: reload operation requires a cluster with at least two workers defined. Maybe you meant to do a 'restart'?");
        }
        try {
            for (final ServiceDefinition backend : backends) {
                try {
                    populateNginxConfFile(Collections.singletonList(backend), true);
                } catch (final Exception e) {
                    throw new SCException(m_logger, FailureType.GENERAL_ERROR, "ERROR: could not reload nginx config", e);
                }
                new OperationExecutor(Operation.STOP, backend, m_serviceDefs, m_logger).execute();
                new OperationExecutor(Operation.START, backend, m_serviceDefs, m_logger).execute();
            }
        } finally {
            populateNginxConfFile(null, true);
            if (!getServiceStatus().isRunning()) {
                startService(_logFile);
            }
        }

    }

    private boolean shouldOutputGoToSplf() throws SCException {
        // User asked for it, so....
        if (m_mainService.getBatchMode().isBatch() && Boolean.getBoolean(PROP_BATCHOUTPUT_SPLF)) {
            return true;
        }
        // User didn't ask for spooled file, and we're not submitting to batch, so log file it is!
        if (!m_mainService.getBatchMode().isBatch()) {
            return false;
        }
        // So if we're submitting to batch as another user, it's unlikely that the other user can access the sc logs directory (private to the current user, so splf it is!)
        return isLikelyRunningAsAnotherUser();
    }

    private void startService(final ScLogFile _logFile) throws InterruptedException, IOException, SCException {

        // If running in batch, double-check that we're not running as a non-existent user
        verifyBatchUser();

        // Start all dependencies before starting this one
        for (final String dependencyName : m_mainService.getDependencies()) {
            final ServiceDefinition dependency = m_serviceDefs.get(dependencyName);
            if (null == dependency) {
                throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "ERROR: Service '%s' has unresolved dependency '%s'", m_mainService.getFriendlyName(), dependencyName);
            }
            try {
                m_logger.printf("Attempting to start service dependency '%s' (%s)...\n", dependencyName, dependency.getFriendlyName());
                new OperationExecutor(Operation.START, dependencyName, m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.ERROR_STARTING_DEPENDENCY, "ERROR: Could not start dependency '%s' for service '%s': %s", dependencyName, m_mainService.getFriendlyName(), e.getLocalizedMessage());
            }
        }

        // If running cluster mode, dynamically configure nginx and start our cluster backends first
        if (m_mainService.isClusterMode()) {
            m_logger.printfln_verbose("Starting service '%s' in cluster mode", m_mainService.getFriendlyName());
            populateNginxConfFile(null, false);
            m_logger.printfln_verbose("Cluster configuration refreshed");

            // If running cluster mode, stop all the backend jobs (concurrently)
            final AsyncOperationSet backendKillers = new AsyncOperationSet(m_logger);
            for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
                m_logger.printf("Attempting to asynchronously start backend job '%s'...\n", backend.getFriendlyName());
                final String exceptionMsg = String.format("ERROR: Could not start backend job '%s' for cluster mode", backend.getFriendlyName());
                backendKillers.start(Operation.START, backend.getName(), m_serviceDefs, exceptionMsg);
            }
            backendKillers.join();
        }

        final ServiceStatusInfo currentStatus = getServiceStatus();
        if (ServiceStatusInfo.Status.RUNNING == currentStatus.getStatus()) {
            m_logger.printf("Service '%s' is already running\n", m_mainService.getFriendlyName());
            return;
        }
        if (ServiceStatusInfo.Status.PARTIALLY_RUNNING == currentStatus.getStatus() && !m_mainService.isClusterMode()) {
            m_logger.printf_warn("Service '%s' is already partially running. You may need to restart if this operation fails.\n", m_mainService.getFriendlyName());
        }
        String command = m_mainService.getStartCommand();
        if (StringUtils.isEmpty(command)) {
            throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "No start command specified for service '%s'", m_mainService.getFriendlyName());
        }
        if (m_mainService.isClusterMode()) {
            command = "/QOpenSys/pkgs/bin/nginx -p $(pwd) -c $(pwd)/cluster.conf";
        }

        final File directory = new File(m_mainService.getEffectiveWorkingDirectory());
        if (!directory.exists() || !directory.canExecute()) {
            throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "Cannot access configured directory %s", directory.getAbsolutePath());
        }
        // Set up the environment variable list for the child process
        final ArrayList<String> envp = new ArrayList<String>();
        if (m_mainService.isInheritingEnvironmentVars()) {
            for (final Entry<String, String> l : System.getenv().entrySet()) {
                if (!isEnvvarProhibitedFromInheritance(l.getKey())) {
                    envp.add(l.getKey() + "=" + l.getValue());
                }
            }
        }
        for (final String var : m_mainService.getEnvironmentVars()) {
            envp.add(var);
        }

        // If there's only one checkalive, and it's a port checkalive, then add the PORT and PORT_PLUS_n envvars
        int numPortCheckalives = 0;
        int checkAlivePort = -1;
        for (final CheckAlive ca : m_mainService.getCheckAlives()) {
            if (CheckAliveType.PORT == ca.getType()) {
                numPortCheckalives++;
                checkAlivePort = Integer.valueOf(ca.getValue());
            }
        }
        if (1 == numPortCheckalives) {
            envp.add("PORT=" + checkAlivePort);
            for (int i = 1; i < 9; ++i) {
                envp.add("PORT_PLUS_" + i + "=" + (checkAlivePort + i));
            }
        }
        if (m_mainService.isClusterBackend()) {
            envp.add("HOSTNAME=localhost");
        }

        // A set of "eyecatchers" that will show up in DSPJOB (helps us for tracking down
        // log files with sc, and seeing other stuff manually in DSPJOB). Must start with
        // "SCOMMANDER_" as that will cause the "scbash" executable to set them.
        if (!shouldOutputGoToSplf()) {
            envp.add("SCOMMANDER_LOGFILE=" + _logFile.getAbsolutePath());
        }
        envp.add("SCOMMANDER_TIMESTAMP=" + _logFile.getTimestamp());
        envp.add("SCOMMANDER_SUBMITTER=" + System.getProperty("user.name"));
        envp.add("SCOMMANDER_NAME=" + m_mainService.getName());
        envp.add("SCOMMANDER_FRIENDLY_NAME=" + m_mainService.getFriendlyName());
        envp.add("SCOMMANDER_DEFINED_AT=" + m_mainService.getSource());

        envp.add("PASE_FORK_JOBNAME=" + m_mainService.getName().replaceAll("[^a-zA-Z0-9]", ""));

        final String bashCommand;
        if (BatchMode.NO_BATCH == m_mainService.getBatchMode()) {
            // If we're not submitting to batch, it's a simple nohup and redirect to our log file.
            bashCommand = command + " >> " + _logFile.getAbsolutePath() + " 2>&1";
        } else {
            // Submitting to batch, which means we will go to the SBMJOB command, which means....
            command = command.replace("'", "''");

            // If we're submitting to batch, we stuff special values into the SBMJOB_JOBNAME and SBMJOB_OPTS environment
            // variables that are ultimately used by our helper script (see the SbmJobScript class)
            final String batchJobName = m_mainService.getBatchJobName();
            if (!StringUtils.isEmpty(batchJobName)) {
                m_logger.printfln_verbose("using custom batch job name: " + batchJobName);
                envp.add("SBMJOB_JOBNAME=" + validateJobName(batchJobName.trim().toUpperCase()));
            }
            final String sbmJobOpts = m_mainService.getSbmJobOpts();
            if (!StringUtils.isEmpty(sbmJobOpts)) {
                m_logger.printfln_verbose("using custom sbmJobOpts: " + sbmJobOpts);
                envp.add("SBMJOB_OPTS=" + sbmJobOpts.trim());
            }

            if (shouldOutputGoToSplf()) {
                bashCommand = ("exec " + SbmJobScript.getQp2() + " " + command);
            } else {
                final char quoteChar = command.contains("'") ? '\"' : '\'';
                bashCommand = ("exec " + SbmJobScript.getQp2() + " " + quoteChar + command + " >> " + _logFile.getAbsolutePath() + " 2>&1" + quoteChar);
            }

        }
        m_logger.println_verbose("envp of the child is " + envp.toString());

        // Now we're ready to actually launch our new process. We take advantage of the shell here by
        // explicitly launching bash and nohup to let the user specify bashisms (for instance, multiple
        // semicolon-separated commands) in the start command for the service.
        m_logger.println_verbose("running command: " + bashCommand);
        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/nohup", getBash(), "-c", bashCommand }, envp.toArray(new String[0]), directory);
        final long startTime = new Date().getTime();
        final OutputStream stdin = p.getOutputStream();
        ProcessLauncher.pipeStreamsToCurrentProcess(m_mainService.getName(), p, m_logger);
        stdin.flush();
        stdin.close();

        // Now, it's just time to wait...
        if (m_mainService.getBatchMode().isBatch()) {
            // Just to make sure the submitted job has some "sticking power"
            Thread.sleep(5000L);
        } else {
            Thread.sleep(1000L);
        }

        final int secondsToWait = m_mainService.getStartupWaitTime();
        while (true) {
            final ServiceStatusInfo status = getServiceStatus();
            if (status.isRunning()) {
                m_logger.printf_success("Service '%s' successfully started\n", m_mainService.getFriendlyName());
                return;
            }
            final long currentTime = new Date().getTime();
            if ((currentTime - startTime) > (1000 * secondsToWait)) {
                if (status.isPartial()) {
                    final String partialInfo = "[not running at -->" + ListUtils.toString(status.m_notRunningList, ", ") + "]";
                    m_logger.printf_warn("WARNING: Service '%s' only %d/%d started [failed to start --> %s]\n", m_mainService.getFriendlyName(), status.m_runningList.size(), status.m_allList.size(), partialInfo);
                }
                throw new SCException(m_logger, FailureType.TIMEOUT_ON_SERVICE_STARTUP, "ERROR: Timed out waiting for service '%s' to start\n", m_mainService.getFriendlyName());
            }
            try {
                Thread.sleep(2000L);
            } catch (final InterruptedException e) {
                m_logger.exception(e);
            }
        }
    }

    private void stopService(final ScLogFile logFile, final boolean _isKillingForcefully) throws IOException, InterruptedException, NumberFormatException, SCException {

        // Stop all dependent services before stopping this one.
        for (final ServiceDefinition dependentService : findKnownDependents()) {
            m_logger.printf("Attempting to stop dependent service '%s'...\n", dependentService.getFriendlyName());
            try {
                new OperationExecutor(_isKillingForcefully ? Operation.KILL : Operation.STOP, dependentService.getName(), m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.ERROR_STOPPING_DEPENDENT, "ERROR: Could not stop dependent service '%s' in order to stop service '%s': %s", dependentService.getFriendlyName(), m_mainService.getFriendlyName(), e.getLocalizedMessage());
            }
        }

        // If running cluster mode, stop all the backend jobs (concurrently)
        final AsyncOperationSet backendKillers = new AsyncOperationSet(m_logger);
        for (final ServiceDefinition backend : m_mainService.getClusterBackends()) {
            m_logger.printf("Attempting to asynchronously stop backend job '%s'...\n", backend.getFriendlyName());
            final String exceptionMsg = String.format("ERROR: Could not stop backend job '%s' for cluster mode", backend.getFriendlyName());
            backendKillers.start(Operation.STOP, backend.getName(), m_serviceDefs, exceptionMsg);
        }
        backendKillers.join();

        // If the service is already stopped, hey, we're done! WOOHOO!!
        if (getServiceStatus().isStopped()) {
            m_logger.printf("Service '%s' is already stopped\n", m_mainService.getFriendlyName());
            return;
        }

        // Log the start time, because we check against this for the timeout condition
        final long startTime = new Date().getTime();

        // Keep track of jobs (in case there we resort to endjob *IMMED, we don't have to query jobs twice)
        final List<String> knownJobList = new LinkedList<String>();

        String command = m_mainService.getStopCommand();
        if (m_mainService.isClusterMode()) {
            command = "nginx -p $(pwd) -c $(pwd)/cluster.conf -s stop";
        } else if ("<backend".equals(m_mainService.getSource())) {
            command = "";
        }
        if (_isKillingForcefully || StringUtils.isEmpty(command)) {
            // If the user doesn't provide a custom stop command, that's OK. We go directly to ENDJOB.
            // Same thing if doing the 'kill' operation. Straight to ENDJOB
            knownJobList.addAll(stopViaEndJob(m_mainService.getShutdownWaitTime(), StringUtils.isNonEmpty(m_mainService.getStopCommand())));
        } else {
            // If the user provided a custom stop command, let's go try to execute it.
            final File directory = new File(m_mainService.getEffectiveWorkingDirectory());

            final ArrayList<String> envp = new ArrayList<String>();
            if (m_mainService.isInheritingEnvironmentVars()) {
                for (final Entry<String, String> l : System.getenv().entrySet()) {
                    envp.add(l.getKey() + "=" + l.getValue());
                }
            }
            for (final String var : m_mainService.getEnvironmentVars()) {
                envp.add(var);
            }

            final String bashCommand;
            if (BatchMode.NO_BATCH == m_mainService.getBatchMode()) {
                m_logger.println_verbose("running command: " + command);
                bashCommand = command + " >> " + logFile.getAbsolutePath() + " 2>&1";
            } else {
                // If we submitted to batch with custom batch options, let's try ending the job the same way.
                // The "stop" command may need to run in a similar environment as the start command, most commonly
                // as the same user
                final String sbmJobOpts = getSbmJobOptsForStopping();
                if (!StringUtils.isEmpty(sbmJobOpts)) {
                    m_logger.printfln_verbose("using custom sbmJobOpts: " + sbmJobOpts);
                    envp.add("SBMJOB_OPTS=" + sbmJobOpts.trim());
                }

                if (shouldOutputGoToSplf()) {
                    final char quoteChar = command.contains("'") ? '\"' : '\'';
                    bashCommand = ("exec " + SbmJobScript.getQp2() + " " + quoteChar + command + quoteChar);
                } else {
                    final char quoteChar = command.contains("'") ? '\"' : '\'';
                    bashCommand = ("exec " + SbmJobScript.getQp2() + " " + quoteChar + command + " >> " + logFile.getAbsolutePath() + " 2>&1" + quoteChar);
                }
            }
            final Process p = Runtime.getRuntime().exec(new String[] { getBash(), "-c", bashCommand }, envp.toArray(new String[0]), directory);
            final OutputStream stdin = p.getOutputStream();
            ProcessLauncher.pipeStreamsToCurrentProcess(m_mainService.getName(), p, m_logger);
            stdin.flush();
            stdin.close();
        }

        // Now, we've tried to end the job. Let's wait for the service to die...
        int secondsToWait = m_mainService.getShutdownWaitTime();

        // If an ENDJOB with OPTION(*CNTRLD) fails, or if the custom stop command fails, then we keep track of it here, because we fall baco to ENDJOB with OPTION(*IMMED)
        boolean hasEndJobImmedBeenTried = false;
        while (true) {
            if (getServiceStatus().isStopped()) {
                // HOORAY!!
                m_logger.printf_success("Service '%s' successfully stopped\n", m_mainService.getFriendlyName());
                return;
            }

            final long currentTime = new Date().getTime();
            if ((currentTime - startTime) > (1000 * secondsToWait)) {
                if (hasEndJobImmedBeenTried) {
                    throw new SCException(m_logger, FailureType.TIMEOUT_ON_SERVICE_STOP, "ERROR: Timed out waiting for service '%s' to stop. Giving up\n", m_mainService.getFriendlyName());
                } else {
                    // OK, we've timed out, so let's try ENDJOB with OPTION(*IMMED) and give it another 20 seconds (arbitrarily hardcoded by programmer)
                    m_logger.printf_warn("WARNING: Timed out waiting for service '%s' to stop. Will try harder\n", m_mainService.getFriendlyName());
                    hasEndJobImmedBeenTried = true;
                    if (knownJobList.isEmpty()) {
                        stopViaEndJob(0, StringUtils.isNonEmpty(m_mainService.getStopCommand()));
                    } else {
                        stopViaEndJob(knownJobList, 0);
                    }
                    secondsToWait += 20;
                }
            }
            try {
                Thread.sleep(2500L);
            } catch (final InterruptedException e) {
                m_logger.exception(e);
            }
        }
    }

    private List<String> stopViaEndJob(final int _waitTime, final boolean _showUserEndjob) throws IOException, NumberFormatException, SCException {
        final List<String> jobs = getActiveJobsForService(false);
        if (jobs.isEmpty()) {
            throw new SCException(m_logger, FailureType.GENERAL_ERROR, "Unable to determine job");
        }
        if (8 <= jobs.size() && 0 != _waitTime) {
            m_logger.printfln_warn("WARNING: %d jobs were found!! Those jobs were: ", jobs.size());
            for (final String job : jobs) {
                m_logger.println_warn("    " + job);
            }
            final boolean isEndingAnyway = new ConsoleQuestionAsker().askBooleanQuestion(m_logger, "y", "Are you sure you want to end all of these jobs?");
            if (isEndingAnyway) {
                m_logger.printfln_warn("WARNING: ending %d jobs", jobs.size());
            } else {
                throw new SCException(m_logger, FailureType.GENERAL_ERROR, "Too many jobs found");
            }
        }
        if (_showUserEndjob) {
            m_logger.println("Stopping via endjob");
        }
        stopViaEndJob(jobs, _waitTime);
        return jobs;
    }

    private void stopViaEndJob(final List<String> _jobs, final int _waitTime) throws IOException {
        final String optionString = (0 >= _waitTime) ? "OPTION(*IMMED)" : ("OPTION(*CNTRLD) DELAY(" + _waitTime + ")");
        final String db2util = "/QOpenSys/pkgs/bin/db2util";
        final String db2util_opts = "-o space";
        final String start_qcmdexc = "CALL QSYS2.QCMDEXC('";
        final String end_qcmdexc = "')";

        for (final String job : _jobs) {
            final String endjob = "ENDJOB JOB(" + job + ") " + optionString;
            String command = start_qcmdexc;
            // batch mode is on so run command endjob under sbmjob
            if (isLikelyRunningAsAnotherUser()) {
                command += "SBMJOB " + getSbmJobOptsForStopping() + " CMD(" + endjob + ")";
            } else {
                command += endjob;
            }
            command += end_qcmdexc;
            m_logger.println_verbose("Ending job with: " + db2util + " " + db2util_opts + " " + command);
            final Process p = Runtime.getRuntime().exec(new String[] { db2util, "-o", "space", command });
            try {
                p.waitFor();
            } catch (final InterruptedException e) {
                m_logger.exception(e);
            }
        }
    }

    private String validateJobName(final String _jobName) throws SCException {
        if (!_jobName.matches("^[0-9A-Z_#]{1,10}$")) {
            throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "Invalid custom job name '%s' specified", _jobName);
        }
        return _jobName;
    }

    private void verifyBatchUser() throws SCException {
        final String batchUser = getBatchUser();
        if (StringUtils.isEmpty(batchUser)) {
            return;
        }
        final File usrprfChecker = new File("/qsys.lib/" + batchUser + ".usrprf");
        if (!usrprfChecker.exists()) {
            throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "ERROR: Service '%s' is configured to run as user '%s' but that profile doesn't exist or you do not have sufficient authorities!!", m_mainService.getName(), batchUser);
        }
    }
}
