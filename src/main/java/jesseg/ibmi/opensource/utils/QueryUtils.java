package jesseg.ibmi.opensource.utils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.SCException.FailureType;

/**
 * A class that uses the <tt>db2util</tt> utility to query the system. This is used for critical queries to check, for
 * instance, if services are alive.
 * 
 * @author Jesse Gorzinski
 */
public class QueryUtils {

    private static List<String> deduplicate(final List<String> _in) {
        final HashSet<String> s = new HashSet<String>();
        s.addAll(_in);
        return Arrays.asList(s.toArray(new String[0]));
    }

    public static List<String> getJobs(final String _job, final AppLogger _logger) throws IOException {
        final boolean isSbsQualified = _job.contains("/");
        final List<String> jobs;
        if (isSbsQualified) {
            final String[] split = _job.split("/");
            jobs = getJobsMatchingNameAndSbs(split[1], split[0], _logger);
        } else {
            jobs = getJobsMatchingName(_job, _logger);
        }
        return deduplicate(jobs);
    }

    private static List<String> getJobsMatchingName(final String _jobName, final AppLogger _logger) throws IOException {
        final List<String> ret = new LinkedList<String>();

        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/db2util", "-o", "space", "-p", _jobName.trim().toUpperCase(), "SELECT JOB_NAME FROM TABLE(QSYS2.ACTIVE_JOB_INFO(JOB_NAME_FILTER => ?)) as X" });
        final List<String> queryResults = ProcessUtils.getStdout("db2util", p, _logger);

        for (final String queryResult : queryResults) {
            ret.add(queryResult.replace("\"", "").trim());
        }
        return deduplicate(ret);
    }

    private static List<String> getJobsMatchingNameAndSbs(final String _jobName, final String _sbs, final AppLogger _logger) throws IOException {
        final List<String> ret = new LinkedList<String>();

        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/db2util", "-o", "space", "-p", "" + _jobName.trim().toUpperCase(), "-p", _sbs.trim().toUpperCase(), "SELECT JOB_NAME FROM TABLE(QSYS2.ACTIVE_JOB_INFO(JOB_NAME_FILTER => ?, SUBSYSTEM_LIST_FILTER => ?)) as X" });
        final List<String> queryResults = ProcessUtils.getStdout("db2util", p, _logger);

        for (final String queryResult : queryResults) {
            ret.add(queryResult.replace("\"", "").trim());
        }
        return deduplicate(ret);
    }

    private static List<String> getListeningJobsByPort(final int _port, final AppLogger _logger) throws IOException {
        final List<String> ret = new LinkedList<String>();

        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/db2util", "-o", "space", "-p", "" + _port, "SELECT JOB_NAME from QSYS2.NETSTAT_JOB_INFO where LOCAL_PORT = ?" });
        final List<String> queryResults = ProcessUtils.getStdout("db2util", p, _logger);
        for (final String queryResult : queryResults) {
            ret.add(queryResult.replace("\"", "").trim());
        }
        return deduplicate(ret);
    }

    public static List<String> getListeningJobsByPort(final String _port, final AppLogger _logger) throws NumberFormatException, IOException {
        return getListeningJobsByPort(Integer.valueOf(_port), _logger);
    }

    public static boolean isJobRunning(final String _job, final AppLogger _logger) throws IOException {
        return !getJobs(_job, _logger).isEmpty();
    }

    public static boolean isListeningOnPort(final int _port, final AppLogger _logger) throws IOException {
        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/db2util", "-o", "space", "-p", "" + _port, "SELECT COUNT(*) FROM QSYS2.NETSTAT_INFO WHERE LOCAL_PORT = ? and TCP_STATE = 'LISTEN'" });
        final List<String> queryResults = ProcessUtils.getStdout("db2util", p, _logger);
        final String firstLine = queryResults.get(0);
        return !firstLine.contains("0") && firstLine.matches("^\\\"[0-9]+\\\"");
    }

    public static boolean isListeningOnPort(final String _port, final AppLogger _logger) throws NumberFormatException, IOException {
        return isListeningOnPort(Integer.valueOf(_port.trim()), _logger);
    }

    public static SortedMap<String, String> getJobPerfInfo(final String _job, final AppLogger _logger, final int _sampleTime) throws IOException, SCException {
        _logger.println_verbose("Getting performance info for job " + _job);
        final File pythonInterpreter = new File("/QOpenSys/pkgs/bin/python3");
        if (!pythonInterpreter.canExecute()) {
            throw new SCException(_logger, FailureType.UNSUPPORTED_OPERATION, "This operation requires Python 3 to be installed");
        } else if (!new File("/QOpenSys/pkgs/lib/python3.6/site-packages/ibm_db.so").exists()) {
            throw new SCException(_logger, FailureType.UNSUPPORTED_OPERATION, "This operation requires the python3-ibm_db RPM to be installed");
        }
        final String simpleJobName = _job.replaceAll(".*/", "").trim().toUpperCase();
        final String jobName = _job.toUpperCase().trim();
//@formatter:off
        final String pythonCode = String.format(
                "import ibm_db_dbi as db2\n"
              + "from ibm_db import SQL_ATTR_TXN_ISOLATION, SQL_TXN_NO_COMMIT\n"
              + "import time\n" + "jobname = \"%s\"\n" + "full_jobname = \"%s\"\n"
              + "sql = \"\"\"SELECT ELAPSED_TIME, THREAD_COUNT, ELAPSED_TOTAL_DISK_IO_COUNT, TOTAL_DISK_IO_COUNT, ELAPSED_CPU_PERCENTAGE, TEMPORARY_STORAGE, JOB_ACTIVE_TIME FROM TABLE(QSYS2.ACTIVE_JOB_INFO(JOB_NAME_FILTER => ?, RESET_STATISTICS => 'NO', DETAILED_INFO => 'ALL')) as X WHERE JOB_NAME = ?\"\"\"\n" 
              + "conn = db2.connect()\n"
              + "conn.set_option({ SQL_ATTR_TXN_ISOLATION: SQL_TXN_NO_COMMIT })\n"
              + "cursor = conn.cursor()\n"
              + "try:\n"
              + "    cursor.execute(sql, (jobname, full_jobname))\n"
              + "    cursor.fetchall()\n"
              + "    time.sleep(%d)\n"
              + "    cursor.execute(sql, (jobname, full_jobname))\n"
              + "    for row in cursor:\n"
              + "        for item in row:\n"
              + "            print(str(item))\n"
              + "finally:\n"
              + "    cursor.close()\n"
              + "    conn.close()",
                simpleJobName, jobName, _sampleTime);
//@formatter:on
        final Process p = Runtime.getRuntime().exec(pythonInterpreter.getAbsolutePath());
        final OutputStream stdin = p.getOutputStream();
        stdin.write(pythonCode.getBytes("UTF-8"));
        stdin.flush();
        stdin.close();

        final List<String> queryResults = ProcessUtils.getStdout("python3", p, _logger);
        if (queryResults.isEmpty()) {
            throw new SCException(_logger, FailureType.ERROR_CHECKING_STATUS, "Unable to retrieve performance data for job %s. The job may no longer be active, or you may be missing required operating system support. The required operating system capability is included in IBM i 7.4. For IBM i 7.3, it is available with group PTF SF99703 Level 11. For IBM i 7.2, it is available with group PTF SF99702 Level 23.", _job);
        }
        final TreeMap<String, String> ret = new TreeMap<>();
        ret.put("->Sampling time (s)", queryResults.get(0));
        ret.put("Thread Count", queryResults.get(1));
        ret.put("Disk I/O operations during sampling time", queryResults.get(2));
        ret.put("Total Disk I/O operations", queryResults.get(3));
        ret.put("CPU Usage (%)", queryResults.get(4));
        ret.put("Temporary Storage (MB)", queryResults.get(5));
        ret.put("Job active since", queryResults.get(6));

        // Now fetch JVM properties!

        final Process pJava = Runtime.getRuntime().exec(new String[] { "/QOpenSys/pkgs/bin/db2util", "-o", "space", "-p", "" + jobName, "SELECT CURRENT_HEAP_SIZE, IN_USE_HEAP_SIZE, MAX_HEAP_SIZE, SHARED_CLASS_SIZE, MALLOC_MEMORY_SIZE, JIT_MEMORY_SIZE, GC_CYCLE_NUMBER, TOTAL_GC_TIME from QSYS2.JVM_INFO where JOB_NAME =  ?" });
        final List<String> javaQueryResults = ProcessUtils.getStdout("db2util", pJava, _logger);
        if (javaQueryResults.isEmpty()) {
            return ret;
        }
        final List<String> javaPerfData = Arrays.asList(javaQueryResults.get(0).replace("\"", "").split("\\s"));
        final NumberFormat formatter = NumberFormat.getInstance();
        ret.put("Java Heap Current Size (MB)", formatter.format(Long.parseLong(javaPerfData.get(0))));
        ret.put("Java Heap In Use (Kb)",    formatter.format(Long.parseLong(javaPerfData.get(1))));
        ret.put("Java Heap Maximum Size (Kb)", formatter.format(Long.parseLong(javaPerfData.get(2))));
        ret.put("Java Shared Class Size (Kb)", formatter.format(Long.parseLong(javaPerfData.get(3))));
        ret.put("Malloc'ed Memory estimate (Kb)", formatter.format(Long.parseLong(javaPerfData.get(4))));
        ret.put("Java JIT Memory (KB)", formatter.format(Long.parseLong(javaPerfData.get(5))));
        ret.put("Java GC Cycle Number", javaPerfData.get(6));
        ret.put("Java GC Total Time (ms)", javaPerfData.get(7));
        return ret;
    }
}
