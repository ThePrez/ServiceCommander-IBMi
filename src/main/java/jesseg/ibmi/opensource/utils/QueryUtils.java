package jesseg.ibmi.opensource.utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * A class that uses the <tt>db2util</tt> utility to query the system. This is used for critical queries to check, for
 * instance, if services are alive. 
 * 
 * @author Jesse Gorzinski
 */
public class QueryUtils {

    private static List<String> deduplicate(List<String> _in) {
        HashSet<String> s = new HashSet<String>();
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
}
