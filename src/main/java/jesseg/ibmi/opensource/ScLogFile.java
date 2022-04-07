package jesseg.ibmi.opensource;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ProcessLauncher;
import com.github.theprez.jcmdutils.StringUtils;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.SCException.FailureType;
import jesseg.ibmi.opensource.utils.QueryUtils;

public class ScLogFile {
    private static String getLogSuffix(final Operation _op, final ServiceDefinition _def) {
        if (Operation.START == _op) {
            return "." + _def.getName() + ".log";
        } else {
            return "." + _def.getName() + "." + _op.name() + ".log";
        }
    }

    private final File m_file;

    public ScLogFile(final AppLogger _logger, final Operation _op, final ServiceDefinition _def, final String _runtimeUser) throws SCException {
        final String logFileName;
        if (_op.isChangingSystemState()) {
            try {
                logFileName = QueryUtils.getCurrentTime(_logger) + getLogSuffix(_op, _def);
            } catch (final IOException e1) {
                throw new SCException(_logger, e1, FailureType.GENERAL_ERROR, "Unable to determine current time");
            }
        } else {
            logFileName = new SimpleDateFormat(QueryUtils.DB_TIMESTAMP_FORMAT).format(new Date()) + getLogSuffix(_op, _def); // should be unused since we only use log files for state-changing stuff
        }
        final String serviceLogDir = _def.getEffectiveLogDirectory();
        if (StringUtils.isEmpty(serviceLogDir)) {
            final File logDir = AppDirectories.conf.getLogDirectoryForUser(_runtimeUser, _logger);
            m_file = new File(logDir, logFileName);
        } else {
            File logDir = new File(serviceLogDir);
            if (!logDir.exists()) {
                logDir.mkdirs();
                if (!logDir.isDirectory()) {
                    logDir = AppDirectories.conf.getLogDirectoryForUser(_runtimeUser, _logger);
                }
            }

            m_file = new File(logDir, logFileName);
        }
    }

    public boolean delete() {
        return m_file.delete();
    }

    public void deleteOnExit() {
        m_file.deleteOnExit();
    }

    public boolean exists() {
        return m_file.exists();
    }

    public String getAbsolutePath() {
        return m_file.getAbsolutePath();
    }

    public File getParentFile() {
        return m_file.getParentFile();
    }

    public long length() {
        return m_file.length();
    }

    public void tail(final AppLogger _logger) {
        final Thread t = new Thread(() -> {
            try {
                while (true) {
                    if (0 < m_file.length()) {
                        tail0(_logger);
                        return;
                    }
                    Thread.sleep(100);
                }
            } catch (final Exception e) {
                _logger.printExceptionStack_verbose(e);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void tail0(final AppLogger _logger) {
        try {
            final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/usr/bin/tail", "-f", m_file.getAbsolutePath() });
            ProcessLauncher.pipeStreamsToCurrentProcess(m_file.getName(), p, _logger);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> p.destroy()));
        } catch (final Exception e) {
            _logger.printExceptionStack_verbose(e);
        }
    }

}
