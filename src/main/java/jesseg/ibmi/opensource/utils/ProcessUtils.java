package jesseg.ibmi.opensource.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility functions for making handling of {@link Process} operations more digestible. 
 *  
 * @author Jesse Gorzinski
 */
public class ProcessUtils {

    public static List<String> getStdout(final String _eyecatcher, final Process _p, final AppLogger _logger) throws UnsupportedEncodingException, IOException {
        final List<String> ret = new LinkedList<String>();
        final Thread stderrThread = new Thread() {
            @Override
            public void run() {
                handleStream(_eyecatcher, _p.getErrorStream(), _logger, true);
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(_p.getInputStream(), "UTF-8"))) {
            String line;
            while (null != (line = br.readLine())) {
                ret.add(line);
            }
        }
        //_logger.println_verbose("stdout was: " + ret);
        return ret;
    }

    public static void pipeStreams(final String _eyecatcher, final Process _p, final AppLogger _logger) {
        final Thread stderrThread = new Thread() {
            @Override
            public void run() {
                handleStream(_eyecatcher, _p.getErrorStream(), _logger, true);
            };
        };
        stderrThread.setDaemon(true);
        stderrThread.start();
        final Thread stdoutThread = new Thread() {
            @Override
            public void run() {
                handleStream(_eyecatcher, _p.getInputStream(), _logger, false);
            };
        };
        stdoutThread.setDaemon(true);
        stdoutThread.start();
    }

    private static void handleStream(final String _eyeCatcher, final InputStream _stream, final AppLogger _logger, final boolean _isError) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(_stream))) {
            String read;
            while (null != (read = br.readLine())) {
                synchronized (_logger) {
                    if (_isError) {
                        _logger.println_err_verbose("child process " + _eyeCatcher + ":" + read);
                    } else {
                        _logger.println_verbose("child process " + _eyeCatcher + ":" + read);
                    }
                }
            }
        } catch (final IOException e) {
            synchronized (_logger) {
                _logger.exception(e);
            }
        }
    }
}
