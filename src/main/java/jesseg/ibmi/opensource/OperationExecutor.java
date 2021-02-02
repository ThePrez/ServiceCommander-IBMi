package jesseg.ibmi.opensource;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.ServiceDefinition.BatchMode;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAliveType;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.utils.ProcessUtils;
import jesseg.ibmi.opensource.utils.QueryUtils;
import jesseg.ibmi.opensource.utils.SbmJobScript;
import jesseg.ibmi.opensource.utils.StringUtils;
import jesseg.ibmi.opensource.utils.StringUtils.TerminalColor;

/**
 * Where all the work happens
 * 
 * @author Jesse Gorzinski
 */
public class OperationExecutor {

    public enum Operation {
        START(true), STOP(true), RESTART(true), CHECK(false), INFO(false), PERFINFO(false);
        private final boolean m_isChangingSystemState;

        Operation(final boolean _isChangingSystemState) {
            m_isChangingSystemState = _isChangingSystemState;
        }

        public boolean isChangingSystemState() {
            return m_isChangingSystemState;
        }
    }

    static final String PROP_BATCHOUTPUT_SPLF = "sc.batchoutput.splf";
    static final String PROP_SAMPLE_TIME = "sc.perfsamplingtime";

    private final Operation m_op;
    private final String m_mainService;
    private final Map<String, ServiceDefinition> m_serviceDefs;
    private final AppLogger m_logger;

    public OperationExecutor(final Operation _op, final String _service, final Map<String, ServiceDefinition> serviceDefs, final AppLogger _logger) {
        m_op = _op;
        m_mainService = _service;
        m_serviceDefs = serviceDefs;
        m_logger = _logger;
    }

    private List<ServiceDefinition> findKnownDependents(final ServiceDefinition _service) {
        final List<ServiceDefinition> ret = new LinkedList<ServiceDefinition>();
        for (final ServiceDefinition entry : m_serviceDefs.values()) {
            for (final String entryDependency : entry.getDependencies()) {
                if (entryDependency.equalsIgnoreCase(_service.getName())) {
                    ret.add(entry);
                    continue;
                }
            }
        }
        return ret;
    }

    public File execute() throws SCException {
        final File logDir = AppDirectories.conf.getLogsDirectory();
        final String logFileName = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()) + "." + getMainService().getName() + ".log";
        final File logFile = new File(logDir.getAbsolutePath() + "/" + logFileName);
        try {
            switch (m_op) {
                case START:
                    startService(getMainService(), logFile);
                    return logFile;
                case STOP:
                    stopService(getMainService(), logFile);
                    return logFile;
                case CHECK:
                    printServiceStatus(getMainService(), m_logger);
                    return null;
                case INFO:
                    printInfo(getMainService(), m_logger);
                    return null;
                case PERFINFO:
                    printPerfInfo(getMainService(), m_logger);
                    return null;
                case RESTART:
                    stopService(getMainService(), logFile);
                    startService(getMainService(), logFile);
                    return logFile;
                default:
                    return null;
            }
        } catch (final Exception e) {
            if (e instanceof SCException) {
                throw (SCException) e;
            }
            throw new SCException(m_logger, FailureType.GENERAL_ERROR, "A general error has occurred: %s", e.getLocalizedMessage());
        } finally {
            if (null != logFile) {
                if (logFile.exists()) {
                    if (0 >= logFile.length()) {
                        logFile.delete();
                        logFile.deleteOnExit();
                    } else {
                        m_logger.println("For details, see log file at: " + StringUtils.colorizeForTerminal(logFile.getAbsolutePath(), TerminalColor.CYAN));
                    }
                }
            }
        }
    }

    private static void printInfo(final ServiceDefinition _svc, final AppLogger _logger) {
        _logger.println();
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("---------------------------------------------------------------------", TerminalColor.WHITE));
        _logger.println(StringUtils.colorizeForTerminal(_svc.getName(), TerminalColor.CYAN) + " (" + _svc.getFriendlyName() + ")");
        _logger.println();
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("Defined in: ", TerminalColor.CYAN) + _svc.getSource());
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("Working Directory: ", TerminalColor.CYAN) + _svc.getWorkingDirectory());
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("Startup Command: ", TerminalColor.CYAN) + _svc.getStartCommand());
        _logger.println(StringUtils.colorizeForTerminal("Startup Wait Time (s): ", TerminalColor.CYAN) + _svc.getStartupWaitTime());
        _logger.println();
        final String shutdownCommand = _svc.getStopCommand();
        if (!StringUtils.isEmpty(shutdownCommand)) {
            _logger.println(StringUtils.colorizeForTerminal("Shutdown Command: ", TerminalColor.CYAN) + shutdownCommand);
        }
        _logger.println(StringUtils.colorizeForTerminal("Shutdown Wait Time (s): ", TerminalColor.CYAN) + _svc.getShutdownWaitTime());
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("Check-alive type: ", TerminalColor.CYAN) + _svc.getCheckAliveType().name());
        _logger.println(StringUtils.colorizeForTerminal("Check-alive condition: ", TerminalColor.CYAN) + _svc.getCheckAliveCriteria());
        final BatchMode batchMode = _svc.getBatchMode();
        if (BatchMode.NO_BATCH == batchMode) {
            _logger.println(StringUtils.colorizeForTerminal("Batch Mode: ", TerminalColor.CYAN) + "<not running in batch>");
        } else {
            _logger.println(StringUtils.colorizeForTerminal("Batch Mode: ", TerminalColor.CYAN) + "<submitted to batch>");
            String batchJobName = _svc.getBatchJobName();
            if (StringUtils.isEmpty(batchJobName)) {
                batchJobName = "<default>";
            }
            _logger.println(StringUtils.colorizeForTerminal("    Batch Job Name: ", TerminalColor.CYAN) + batchJobName);
            final String sbmjobOpts = _svc.getSbmJobOpts();
            if (!StringUtils.isEmpty(sbmjobOpts)) {
                _logger.println(StringUtils.colorizeForTerminal("    SBMJOB options: ", TerminalColor.CYAN) + sbmjobOpts);
            }
        }
        final List<String> dependencies = _svc.getDependencies();
        if (!dependencies.isEmpty()) {
            _logger.println();
            _logger.println(StringUtils.colorizeForTerminal("Depends on the following services:", TerminalColor.CYAN));
            for (final String dependency : dependencies) {
                _logger.println("    " + dependency);
            }
        }
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("Inherits environment variables?: ", TerminalColor.CYAN) + _svc.isInheritingEnvironmentVars());
        final List<String> envVars = _svc.getEnvironmentVars();
        if (!envVars.isEmpty()) {
            _logger.println(StringUtils.colorizeForTerminal("Custom environment variables:", TerminalColor.CYAN));
            for (final String envVar : envVars) {
                _logger.println("    " + envVar);
            }
        }
        _logger.println("---------------------------------------------------------------------");
        _logger.println();
        _logger.println();
    }

    private static List<String> getActiveJobsForService(final ServiceDefinition _svc, final AppLogger _logger) throws SCException {
        try {
            if (CheckAliveType.PORT == _svc.getCheckAliveType()) {
                return QueryUtils.getListeningJobsByPort(_svc.getCheckAliveCriteria(), _logger);
            } else {
                return QueryUtils.getJobs(_svc.getCheckAliveCriteria(), _logger);
            }
        } catch (final IOException ioe) {
            throw new SCException(_logger, FailureType.ERROR_CHECKING_STATUS, "Error occurred while checking status of service '%s': %s", _svc.getFriendlyName(), ioe.getLocalizedMessage());
        } catch (final NumberFormatException nfe) {
            throw new SCException(_logger, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", _svc.getFriendlyName(), _svc.getCheckAliveCriteria());
        }
    }

    static class PerfInfoFetcher extends Thread {
        protected SortedMap<String, String> m_res = null;
        private String m_job;
        private AppLogger m_logger;
        private float m_sampleTime;
        private SCException m_exc = null;

        public PerfInfoFetcher(final String _job, AppLogger _logger, float _sampleTime) {
            super("PerformanceInfo-"+_job);
            m_job = _job;this.m_logger=_logger;m_sampleTime=_sampleTime;
            start();
        }
        @Override
        public void run() {
            try {
                m_res=QueryUtils.getJobPerfInfo(m_job, m_logger, m_sampleTime);
            }catch(Exception e) {
                m_exc = SCException.fromException(e, m_logger);
            }
        }
        public SortedMap<String, String> getResults() throws SCException {
            try {
                join();
            } catch (InterruptedException e) {
               throw SCException.fromException(e, m_logger);
            }
            if(null != m_exc) {
                throw m_exc;
            }
            return m_res;
        }
    }
    private static void printPerfInfo(final ServiceDefinition _svc, final AppLogger _logger) throws SCException, IOException {
        _logger.println();
        _logger.println(StringUtils.colorizeForTerminal("---------------------------------------------------------------------", TerminalColor.WHITE));

        _logger.println(StringUtils.colorizeForTerminal(_svc.getName(), TerminalColor.CYAN) + " (" + _svc.getFriendlyName() + ")");
        if (!isServiceRunning(_svc, _logger)) {
            _logger.println(StringUtils.colorizeForTerminal("NOT RUNNING", TerminalColor.PURPLE));
        }
        _logger.println();
        List<PerfInfoFetcher> dataFetcherThreads = new LinkedList<PerfInfoFetcher>();
        for (final String job : getActiveJobsForService(_svc, _logger)) {
            dataFetcherThreads.add(new PerfInfoFetcher(job, _logger, Float.parseFloat(System.getProperty(PROP_SAMPLE_TIME, "1.0"))));
        }
        for (final PerfInfoFetcher dataFetcherThread : dataFetcherThreads) {
            _logger.println(StringUtils.colorizeForTerminal("Job: " + dataFetcherThread.m_job, TerminalColor.CYAN));
            final SortedMap<String, String> perfInfo = dataFetcherThread.getResults();
            for (final Entry<String, String> pi : perfInfo.entrySet()) {
                _logger.println("    " + StringUtils.colorizeForTerminal(pi.getKey(), TerminalColor.CYAN) + ": " + pi.getValue());
            }
            _logger.println();
        }
        _logger.println("---------------------------------------------------------------------");
        _logger.println();
    }

    private static boolean printServiceStatus(final ServiceDefinition _svc, final AppLogger _logger) throws NumberFormatException, IOException, SCException {
        final boolean isRunning = isServiceRunning(_svc, _logger);
        final String paddedStatusString;
        if (isRunning) {
            paddedStatusString = StringUtils.colorizeForTerminal(StringUtils.spacePad("RUNNING", 23), TerminalColor.GREEN);
        } else {
            paddedStatusString = StringUtils.colorizeForTerminal(StringUtils.spacePad("NOT RUNNING", 23), TerminalColor.PURPLE);
        }
        _logger.printfln("  %s | %s (%s)", paddedStatusString, _svc.getName(), _svc.getFriendlyName());
        return isRunning;
    }

    private ServiceDefinition getMainService() throws SCException {
        final ServiceDefinition mainService = m_serviceDefs.get(m_mainService);
        if (null == mainService) {
            throw new SCException(m_logger, FailureType.MISSING_SERVICE_DEF, "Could not find definition for service '%s'", m_mainService);
        }
        return mainService;
    }

    private void stopService(final ServiceDefinition _svc, final File _logFile) throws IOException, InterruptedException, NumberFormatException, SCException {

        // Stop all dependent services before stopping this one.
        for (final ServiceDefinition dependentService : findKnownDependents(_svc)) {
            m_logger.printf("Attempting to stop dependent service '%s'...\n", dependentService.getFriendlyName());
            try {
                new OperationExecutor(Operation.STOP, dependentService.getName(), m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, e, FailureType.ERROR_STOPPING_DEPENDENT, "ERROR: Could not start dependent service '%s' in order to stop service service '%s': %s", dependentService.getFriendlyName(), _svc.getFriendlyName(), e.getLocalizedMessage());
            }
        }

        // If the service is already stopped, hey, we're done! WOOHOO!!
        if (!isServiceRunning(_svc, m_logger)) {
            m_logger.printf("Service '%s' is already stopped\n", _svc.getFriendlyName());
            return;
        }

        // Log the start time, because we check against this for the timeout condition
        final long startTime = new Date().getTime();

        final String command = _svc.getStopCommand();
        if (StringUtils.isEmpty(command)) {
            // If the user doesn't provide a custom stop command, that's OK. We go directly to ENDJOB.
            stopViaEndJob(_svc, _svc.getShutdownWaitTime(), m_logger);
        } else {
            // If the user provided a custom stop command, let's go try to execute it.
            final File directory = new File(_svc.getWorkingDirectory());

            final ArrayList<String> envp = new ArrayList<String>();
            if (_svc.isInheritingEnvironmentVars()) {
                for (final Entry<String, String> l : System.getenv().entrySet()) {
                    envp.add(l.getKey() + "=" + l.getValue());
                }
            }
            for (final String var : _svc.getEnvironmentVars()) {
                envp.add(var);
            }
            m_logger.println_verbose("running command: " + command);
            final String bashCommand = command + " >> " + _logFile.getAbsolutePath() + " 2>&1";
            final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/bash", "-c", bashCommand }, envp.toArray(new String[0]), directory);
            final OutputStream stdin = p.getOutputStream();
            ProcessUtils.pipeStreamsToCurrentProcess(_svc.getName(), p, m_logger);
            stdin.flush();
            stdin.close();
        }

        // Now, we've tried to end the job. Let's wait for the service to die...
        int secondsToWait = _svc.getShutdownWaitTime();

        // If an ENDJOB with OPTION(*CNTRLD) fails, or if the custom stop command fails, then we keep track of it here, because we fall baco to ENDJOB with OPTION(*IMMED)
        boolean hasEndJobImmedBeenTried = false;
        while (true) {
            if (!isServiceRunning(_svc, m_logger)) {
                // HOORAY!!
                m_logger.printf_success("Service '%s' successfully stopped\n", _svc.getFriendlyName());
                return;
            }

            final long currentTime = new Date().getTime();
            if ((currentTime - startTime) > (1000 * secondsToWait)) {
                if (hasEndJobImmedBeenTried) {
                    throw new SCException(m_logger, FailureType.TIMEOUT_ON_SERVICE_STOP, "ERROR: Timed out waiting for service '%s' to stop. Giving up\n", _svc.getFriendlyName());
                } else {
                    // OK, we've timed out, so let's try ENDJOB with OPTION(*IMMED) and give it another 20 seconds (arbitrarily hardcoded by programmer)
                    m_logger.printf_warn("WARNING: Timed out waiting for service '%s' to stop. Will try harder\n", _svc.getFriendlyName());
                    hasEndJobImmedBeenTried = true;
                    stopViaEndJob(_svc, 0, m_logger);
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

    private void startService(final ServiceDefinition _svc, final File _logFile) throws InterruptedException, IOException, SCException {

        // Start all dependencies before starting this one
        for (final String dependencyName : _svc.getDependencies()) {
            final ServiceDefinition dependency = m_serviceDefs.get(dependencyName);
            if (null == dependency) {
                throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "ERROR: Service '%s' has unresolved dependency '%s'", _svc.getFriendlyName(), dependencyName);
            }
            try {
                m_logger.printf("Attempting to start service dependency '%s' (%s)...\n", dependencyName, dependency.getFriendlyName());
                new OperationExecutor(Operation.START, dependencyName, m_serviceDefs, m_logger).execute();
            } catch (final Exception e) {
                throw new SCException(m_logger, FailureType.ERROR_STARTING_DEPENDENCY, "ERROR: Could not start dependency '%s' for service '%s': %s", dependencyName, _svc.getFriendlyName(), e.getLocalizedMessage());
            }
        }

        if (isServiceRunning(_svc, m_logger)) {
            m_logger.printf("Service '%s' is already running\n", _svc.getFriendlyName());
            return;
        }
        final String command = _svc.getStartCommand();
        final File directory = new File(_svc.getWorkingDirectory());

        // Set up the environment variable list for the child process
        final ArrayList<String> envp = new ArrayList<String>();
        if (_svc.isInheritingEnvironmentVars()) {
            for (final Entry<String, String> l : System.getenv().entrySet()) {
                if (!isEnvvarProhibitedFromInheritance(l.getKey())) {
                    envp.add(l.getKey() + "=" + l.getValue());
                }
            }
        }
        for (final String var : _svc.getEnvironmentVars()) {
            envp.add(var);
        }

        final String bashCommand;
        if (BatchMode.NO_BATCH == _svc.getBatchMode()) {
            // If we're not submitting to batch, it's a simple nohup and redirect to our log file.
            bashCommand = command + " >> " + _logFile.getAbsolutePath() + " 2>&1";
        } else {
            // If we're submitting to batch, we stuff special values into the SBMJOB_JOBNAME and SBMJOB_OPTS environment
            // variables that are ultimately used by our helper script (see the SbmJobScript class)
            final String batchJobName = _svc.getBatchJobName();
            if (!StringUtils.isEmpty(batchJobName)) {
                m_logger.printfln_verbose("using custom batch job name: " + batchJobName);
                envp.add("SBMJOB_JOBNAME=" + validateJobName(batchJobName.trim().toUpperCase()));
            }
            final String sbmJobOpts = _svc.getSbmJobOpts();
            if (!StringUtils.isEmpty(sbmJobOpts)) {
                m_logger.printfln_verbose("using custom sbmJobOpts: " + sbmJobOpts);
                envp.add("SBMJOB_OPTS=" + sbmJobOpts.trim());
            }

            // In the case of submitting to batch, it's unclear whether the user would want to redirect the output of the command
            // to spooled files or whether they'd prefer the more i-traditional approach of output going to spooled files,
            // which is arguably more natural for batch jobs.
            if (Boolean.getBoolean(PROP_BATCHOUTPUT_SPLF)) {
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
        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/nohup", "/QOpenSys/pkgs/bin/bash", "-c", bashCommand }, envp.toArray(new String[0]), directory);
        final long startTime = new Date().getTime();
        final OutputStream stdin = p.getOutputStream();
        ProcessUtils.pipeStreamsToCurrentProcess(_svc.getName(), p, m_logger);
        stdin.flush();
        stdin.close();

        // Now, it's just time to wait...
        if (BatchMode.BATCH_QP2SHELL2 == _svc.getBatchMode()) {
            // Just to make sure the submitted job has some "sticking power"
            Thread.sleep(5000L);
        } else {
            Thread.sleep(1000L);
        }

        final int secondsToWait = _svc.getStartupWaitTime();
        while (true) {
            if (isServiceRunning(_svc, m_logger)) {
                m_logger.printf_success("Service '%s' successfully started\n", _svc.getFriendlyName());
                return;
            }
            final long currentTime = new Date().getTime();
            if ((currentTime - startTime) > (1000 * secondsToWait)) {
                throw new SCException(m_logger, FailureType.TIMEOUT_ON_SERVICE_STARTUP, "ERROR: Timed out waiting for service '%s' to start\n", _svc.getFriendlyName());
            }
            try {
                Thread.sleep(2000L);
            } catch (final InterruptedException e) {
                m_logger.exception(e);
            }
        }
    }

    private String validateJobName(final String _jobName) throws SCException {
        if (!_jobName.matches("^[0-9A-Z#]{1,10}$")) {
            throw new SCException(m_logger, FailureType.INVALID_SERVICE_CONFIG, "Invalid custom job name '%s' specified", _jobName);
        }
        return _jobName;
    }

    private static boolean isEnvvarProhibitedFromInheritance(final String _var) {
        final List<String> prohibited = Arrays.asList("LIBPATH", "LD_LIBRARY_PATH", "JAVA_HOME", "SSH_TTY", "SSH_CLIENT", "SSH_CONNECTION", "SHELL", "SHLVL");
        return prohibited.contains(_var);
    }

    private static void stopViaEndJob(final ServiceDefinition _svc, final int _waitTime, final AppLogger _logger) throws IOException {
        if (CheckAliveType.PORT == _svc.getCheckAliveType()) {
            final List<String> jobs = QueryUtils.getListeningJobsByPort(_svc.getCheckAliveCriteria(), _logger);
            stopViaEndJob(jobs, _waitTime, _logger, _svc);
        } else if (CheckAliveType.JOBNAME == _svc.getCheckAliveType()) {
            _logger.println("Stopping via endjob");
            final List<String> jobs = QueryUtils.getJobs(_svc.getCheckAliveCriteria(), _logger);
            if (jobs.isEmpty()) {
                return;
            } else if (1 == jobs.size()) {
                stopViaEndJob(jobs, _waitTime, _logger, _svc);
            } else {
                _logger.println_err("ERROR: Multiple jobs found matching job name criteria!! Those jobs were: ");
                for (final String job : jobs) {
                    _logger.println_err("    " + job);
                }
                return;
            }
        }
    }

    private static void stopViaEndJob(final List<String> _jobs, final int _waitTime, final AppLogger _logger, final ServiceDefinition _svc) throws IOException {
        final String optionString = (0 >= _waitTime) ? "OPTION(*IMMED)" : ("OPTION(*CNTRLD) DELAY(" + _waitTime + ")");
        final String db2util = "/QOpenSys/pkgs/bin/db2util";
        final String db2util_opts = "-o space";
        String start_qcmdexc = "CALL QSYS2.QCMDEXC('";
        final String end_qcmdexc = "')";

        for (final String job : _jobs) {
            final String endjob = "ENDJOB JOB(" + job + ") " + optionString;
            String command = start_qcmdexc;
            // batch mode is on so run command endjob under sbmjob
            if (BatchMode.NO_BATCH != _svc.getBatchMode()) {
                command += "SBMJOB " +_svc.getSbmJobOpts() + " CMD("+ endjob + ")";
            } else {
                command += endjob;
            }
            command += end_qcmdexc;
            _logger.println("Ending job with: " + db2util + " " + db2util_opts + " "  + command);
            final Process p = Runtime.getRuntime().exec(new String [] {db2util, "-o", "space", command });
            try {
                p.waitFor();
            } catch (final InterruptedException e) {
                _logger.exception(e);
            }
        }
    }

    public static boolean isServiceRunning(final ServiceDefinition _svc, final AppLogger _logger) throws SCException {
        final CheckAliveType checkType = _svc.getCheckAliveType();
        try {
            if (CheckAliveType.PORT == checkType) {
                return QueryUtils.isListeningOnPort(_svc.getCheckAliveCriteria(), _logger);
            } else if (CheckAliveType.JOBNAME == checkType) {
                return QueryUtils.isJobRunning(_svc.getCheckAliveCriteria(), _logger);
            }
        } catch (final IOException ioe) {
            throw new SCException(_logger, FailureType.ERROR_CHECKING_STATUS, "Error occurred while checking status of service '%s': %s", _svc.getFriendlyName(), ioe.getLocalizedMessage());
        } catch (final NumberFormatException nfe) {
            throw new SCException(_logger, FailureType.INVALID_SERVICE_CONFIG, "Invalid data for port number or job name criteria for service '%s': %s", _svc.getFriendlyName(), _svc.getCheckAliveCriteria());
        }
        throw new SCException(_logger, FailureType.UNSUPPORTED_OPERATION, "Unsupported operation has been requested");
    }
}
