/*
 *
 */
package jesseg.ibmi.opensource.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;
import com.github.theprez.jcmdutils.StringUtils.TerminalColor;

/**
 * Makes it easier to launch processes and process their output
 */
public class ProcessLauncher {

    /**
     * Encapsulates the result of a process invocation.
     */
    public static class ProcessResult {

        private final int m_exitStatus;

        private final List<String> m_stderr;

        private final List<String> m_stdout;

        ProcessResult(final List<String> m_stdout, final List<String> m_stderr, final int m_exitStatus) {
            super();
            this.m_stdout = m_stdout;
            this.m_stderr = m_stderr;
            this.m_exitStatus = m_exitStatus;
        }

        /**
         * Gets the exit status.
         *
         * @return the exit status
         */
        public int getExitStatus() {
            return m_exitStatus;
        }

        /**
         * Gets the stderr.
         *
         * @return the stderr
         */
        public List<String> getStderr() {
            return m_stderr;
        }

        /**
         * Gets the stdout.
         *
         * @return the stdout
         */
        public List<String> getStdout() {
            return m_stdout;
        }

        /**
         * Pretty print, formatting the stdout in green and the stderr in red. Note that this will always
         * be ordered such that all of the stdout precedes all of the stderr, regardless of the process's
         * output order.
         */
        public void prettyPrint() {
            for (final String stdout : m_stdout) {
                System.out.println(StringUtils.colorizeForTerminal(stdout, ColorSchemeConfig.get("SUCCESS")));
            }
            for (final String stderr : m_stderr) {
                System.out.println(StringUtils.colorizeForTerminal(stderr, ColorSchemeConfig.get("ERROR")));
            }
        }

    }

    /**
     * Execute a command!
     *
     * @param _cmd
     *            the cmd
     * @return the process result
     * @throws UnsupportedEncodingException
     *             the unsupported encoding exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     */
    public static ProcessResult exec(final String _cmd) throws UnsupportedEncodingException, IOException {
        final Process p = Runtime.getRuntime().exec(_cmd);
        final List<String> stdout = new LinkedList<String>();
        final List<String> stderr = new LinkedList<String>();
        final Thread stderrThread = new Thread("execStderrThread") {
            @Override
            public void run() {
                try {
                    stderr.addAll(getStreamDataFromProcess(p, p.getErrorStream()));
                } catch (final Exception e) {
                    if (p.isAlive()) {
                        e.printStackTrace();
                    }
                }
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
        p.getOutputStream().close();
        stderr.addAll(getStreamDataFromProcess(p, p.getErrorStream()));
        int rc;
        try {
            rc = p.waitFor();
        } catch (final InterruptedException e) {
            throw new IOException(e);
        }
        return new ProcessResult(stdout, stderr, rc);
    }

    public static ProcessResult exec(final String... _cmd) throws UnsupportedEncodingException, IOException {
        final Process p = Runtime.getRuntime().exec(_cmd);
        final List<String> stdout = new LinkedList<String>();
        final List<String> stderr = new LinkedList<String>();
        final Thread stderrThread = new Thread() {
            @Override
            public void run() {
                try {
                    stderr.addAll(getStreamDataFromProcess(p, p.getErrorStream()));
                } catch (final Exception e) {
                    if (p.isAlive()) {
                        e.printStackTrace();
                    }
                }
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
        p.getOutputStream().close();
        stdout.addAll(getStreamDataFromProcess(p, p.getInputStream()));
        int rc;
        try {
            rc = p.waitFor();
        } catch (final InterruptedException e) {
            throw new IOException(e);
        }
        return new ProcessResult(stdout, stderr, rc);
    }

    /**
     * Utility function to run a process and return the stdout. Note that it will also log this info in the {@link AppLogger} instance that is passed in
     *
     * @param _eyecatcher
     *            the eyecatcher
     * @param _p
     *            the p
     * @param _logger
     *            the logger
     * @return the stdout
     * @throws UnsupportedEncodingException
     *             the unsupported encoding exception
     * @throws IOException
     *             Signals that an I/O exception has occurred.
     * @throws InterruptedException
     */
    public static List<String> getStdout(final String _eyecatcher, final Process _p, final AppLogger _logger) throws UnsupportedEncodingException, IOException {
        final Thread stderrThread = new Thread("getStdoutThread") {
            @Override
            public void run() {
                handleStream(_p, _eyecatcher, _p.getErrorStream(), _logger, true);
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
        return getStreamDataFromProcess(_p, _p.getInputStream());
    }

    private static List<String> getStreamDataFromProcess(final Process _p, final InputStream _stream) throws IOException {
        final List<String> ret = new LinkedList<String>();

        // You may be thinking, "I think some of the stream handling is convoluted and
        // there are more approbated patterns for this, and you'd be right! This mess is to work
        // around a suspected JDK bug. When the system is under stress, it exposed a timing window
        // whereby the BufferedReader.readLine() call waited to receive output from a process,
        // but if that process didn't send a trailing newline ('\n') before it ended, this would
        // sometimes get "stuck" and wait forever. The process exit didn't break the readLine()
        // out of the blocking read. This was also experienced when using an InputStreamReader
        // directly. Under rare circumstances, it would continue to block after process exit!
        // One approach I tried was to explicitly close the BufferedReader object when the process
        // exits, but that didn't work since the close() function is synchronized and the hung
        // thread is holding the lock!
        //
        // This convoluted loop guarantees that BufferedReader.readLine() is only called when
        // One of the following is true:
        // - there is data already able to be consumed without blocking
        // - the process is already ended, avoiding the suspected bug and deadlock scenario
        try (BufferedReader br = new BufferedReader(new InputStreamReader(_stream, "UTF-8"))) {
            while (true) {
                if (!br.ready()) {
                    if (_p.isAlive()) {
                        Thread.sleep(40);
                        continue;
                    } else {
                        _p.waitFor();
                    }
                }
                final String line = br.readLine();
                if (null == line) {
                    break;
                }
                ret.add(line);
            }
        } catch (final InterruptedException e) {
            throw new IOException(e);
        } catch (final IOException e) {
            if (_p.isAlive()) {
                throw e;
            }
        }
        return ret;
    }

    private static void handleStream(final Process _p, final String _eyeCatcher, final InputStream _stream, final AppLogger _logger, final boolean _isError) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(_stream, "UTF-8"))) {
            while (true) {
                if (!br.ready()) {
                    if (_p.isAlive()) {
                        Thread.sleep(40);
                        continue;
                    } else {
                        _p.waitFor();
                    }
                }
                final String line = br.readLine();
                if (null == line) {
                    break;
                }
                if (_isError) {
                    _logger.println_err_verbose("child process " + _eyeCatcher + ":" + line);
                } else {
                    _logger.println_verbose("child process " + _eyeCatcher + ":" + line);
                }
            }
        } catch (final Exception e) {
            _logger.printExceptionStack_verbose(e);
        }
    }

    /**
     * Run the process, but route the child's stdout and stderr to this process
     *
     * @param _eyecatcher
     *            the eyecatcher
     * @param _p
     *            the p
     * @param _logger
     *            the logger
     */
    public static void pipeStreamsToCurrentProcess(final String _eyecatcher, final Process _p, final AppLogger _logger) {
        final Thread stderrThread = new Thread("piper-stderr") {
            @Override
            public void run() {
                handleStream(_p, _eyecatcher, _p.getErrorStream(), _logger, true);
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
        final Thread stdoutThread = new Thread("piper-stdout") {
            @Override
            public void run() {
                handleStream(_p, _eyecatcher, _p.getInputStream(), _logger, false);
            };
        };
        stdoutThread.setDaemon(true);
        stdoutThread.start();
    }
}
