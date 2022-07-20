package jesseg.ibmi.opensource;

import java.io.File;
import java.io.IOException;

import com.github.theprez.jcmdutils.AppLogger;

import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.utils.QueryUtils;

/**
 * Just a place to keep track of where various directories are that the application uses.
 *
 * <br>
 *
 * Most notably, there are three places that the tool will search for service definition files:
 * <ul>
 * <li>A global directory (/QOpenSys/etc/sc/services)
 * <li>A user-specific directory($HOME/.sc/services)
 * <li>If defined, whatever the value of the <tt>services.dir</tt> system property is
 * </ul>
 *
 * @author Jesse Gorzinski
 *
 */
public enum AppDirectories {
    conf;
    private static File s_globalServicesDir = new File("/QOpenSys/etc/sc/services");
    private static File s_userServicesDir = new File(System.getProperty("user.home", "~") + "/.sc/services");

    private static int quickExec(final String... _cmd) throws InterruptedException, IOException { // TODO: move into JCmdUtils
        return Runtime.getRuntime().exec(_cmd).waitFor();
    }

    public File getCustomServicesDirOrNull() {
        final String servicesDir = System.getProperty("services.dir", null);
        if (null == servicesDir) {
            return null;
        }
        return validateDir(new File(servicesDir));
    }

    public File getGlobalServicesDir() {
        return s_globalServicesDir;
    }

    public File getGlobalServicesDirOrNull() {
        return validateDir(s_globalServicesDir);
    }

    public File getLogDirectoryForUser(final String _user, final AppLogger _logger) throws SCException {
        if (_user.equalsIgnoreCase(System.getProperty("user.name"))) {
            return getLogsDirectory();
        }

        final File homeDir = new File(QueryUtils.getHomeDir(_user, _logger));
        final File logsDir = new File(homeDir.getAbsolutePath() + "/.sc/logs");
        if (logsDir.isDirectory()) {
            _logger.printfln_verbose("Using directory '%s' for logs for user '%s'", logsDir.getAbsolutePath(), _user);
            return logsDir;
        }
        final File tmpLogsDir = new File(System.getProperty("java.io.tmpdir", "/tmp") + "/.sc_logs_" + _user.trim().toLowerCase());
        _logger.printfln_verbose("Using temporary directory '%s' for logs for user '%s'", tmpLogsDir.getAbsolutePath(), _user);
        if (!tmpLogsDir.mkdir()) {
            _logger.printfln_warn_verbose("WARNING: Unable to create log dir '%s'", tmpLogsDir.getAbsolutePath());
        }
        try {
            if (0 != quickExec("/QOpenSys/usr/bin/chown", _user.toLowerCase().trim(), tmpLogsDir.getAbsolutePath())) {
                throw new SCException(_logger, FailureType.GENERAL_ERROR, "ERROR: Unable to change ownership of log dir '%s'", tmpLogsDir.getAbsolutePath());
            }
        } catch (final Exception e) {
            _logger.printfln_warn_verbose( "WARNING: Runtime error attempting to change ownership of log dir '%s'", tmpLogsDir.getAbsolutePath());
        }
        return tmpLogsDir;
    }

    public File getLogsDirectory() {
        final File logDir = new File(System.getProperty("user.home", "~") + "/.sc/logs");
        if (!logDir.isDirectory()) {
            logDir.mkdirs();
        }
        return logDir;
    }

    public File getScriptsDirectory() {
        final File retDir = new File(System.getProperty("user.home", "~") + "/.sc/.scripts");
        if (!retDir.isDirectory()) {
            retDir.mkdirs();
        }
        return retDir;
    }

    public File getUserServicesDir(final boolean _create) {
        if (_create) {
            s_userServicesDir.mkdirs();
        }
        return s_userServicesDir;
    }

    public File getUserServicesDirOrNull() {
        return validateDir(s_userServicesDir);
    }

    private File validateDir(final File _dir) {
        if (null == _dir || !_dir.isDirectory() || !_dir.canRead() || !_dir.canExecute()) {
            return null;
        }
        return _dir;
    }

}
