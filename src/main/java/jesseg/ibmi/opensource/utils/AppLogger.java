package jesseg.ibmi.opensource.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import jesseg.ibmi.opensource.utils.StringUtils.TerminalColor;

/**
 * Used to encapsulate console logging activity in verbose and non-verbose mode. Eventually,
 * this may also encapsulate writing to log files, alerting, or some other features.
 *
 * @author Jesse Gorzinski
 */
public abstract class AppLogger {

    public static class DefaultLogger extends AppLogger {
        private final OutputHandler m_err;
        private final OutputHandler m_out;
        private final boolean m_verbose;

        public DefaultLogger(final boolean _verbose) {
            m_out = (_fmt, _args) -> System.out.printf(_fmt, _args);
            m_err = (_fmt, _args) -> System.err.printf(_fmt, _args);
            m_verbose = _verbose;
            // TODO: have options to write verbose output to file or log4j or something (that's the whole point of this class)
        }

        @Override
        protected OutputHandler getErr() {
            return m_err;
        }

        @Override
        protected OutputHandler getOut() {
            return m_out;
        }

        @Override
        protected boolean isVerbose() {
            return m_verbose;
        }
    }

    public static class DeferredLogger extends AppLogger implements Flushable, Closeable {
        private final OutputHandler m_deferredErr;
        private final LinkedList<Runnable> m_deferredEvents = new LinkedList<Runnable>();
        private final OutputHandler m_deferredOut;
        private final AppLogger m_parent;

        public DeferredLogger(final AppLogger _parent) {
            m_parent = _parent;
            m_deferredOut = (_fmt, _args) -> m_deferredEvents.add(() -> m_parent.getOut().printf(_fmt, _args));
            m_deferredErr = (_fmt, _args) -> m_deferredEvents.add(() -> m_parent.getErr().printf(_fmt, _args));
        }

        @Override
        public void close() {
            flush();
        }

        @Override
        public void flush() {
            while (true) {
                try {
                    m_deferredEvents.removeFirst().run();
                } catch (final NoSuchElementException e) {
                    return;
                }
            }
        }

        @Override
        protected OutputHandler getErr() {
            return m_deferredErr;
        }

        @Override
        protected OutputHandler getOut() {
            return m_deferredOut;
        }

        @Override
        protected boolean isVerbose() {
            return m_parent.isVerbose();
        }
    }

    private interface OutputHandler {

        void printf(String _fmt, Object... _args);

        default void println(final String _str) {
            printf("%s\n", _str);
        }
    }

    public void exception(final Throwable _exc) {
        _exc.printStackTrace(System.err);
    }

    protected abstract OutputHandler getErr();

    protected abstract OutputHandler getOut();

    protected abstract boolean isVerbose();

    public void printExceptionStack_verbose(final Exception _e) {
        if (!isVerbose()) {
            return;
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final PrintWriter pw = new PrintWriter(baos, false);
        _e.printStackTrace(pw);
        pw.flush();
        getErr().println(new String(baos.toByteArray()));
    }

    public void printf(final String _fmt, final Object... _args) {
        getOut().printf(_fmt, _args);
    }

    public void printf_err(final String _fmt, final Object... _args) {
        getErr().printf(StringUtils.colorizeForTerminal(_fmt, TerminalColor.BRIGHT_RED), _args);
    }

    public void printf_err_verbose(final String _fmt, final Object... _args) {
        if (!isVerbose()) {
            return;
        }
        getErr().printf(StringUtils.colorizeForTerminal(_fmt, TerminalColor.BRIGHT_RED), _args);
    }

    public void printf_success(final String _fmt, final Object... _args) {
        printf(StringUtils.colorizeForTerminal(_fmt, TerminalColor.GREEN), _args);
    }

    public void printf_verbose(final String _fmt, final Object... _args) {
        if (!isVerbose()) {
            return;
        }
        getOut().printf(_fmt, _args);
    }

    public void printf_warn(final String _fmt, final Object... _args) {
        getErr().printf(StringUtils.colorizeForTerminal(_fmt, TerminalColor.YELLOW), _args);
    }

    public void printf_warn_verbose(final String _fmt, final Object... _args) {
        if (!isVerbose()) {
            return;
        }
        getErr().printf(StringUtils.colorizeForTerminal(_fmt, TerminalColor.YELLOW), _args);
    }

    public void printfln(final String _fmt, final Object... _args) {
        printf(_fmt + "\n", _args);
    }

    public void printfln_err(final String _fmt, final Object... _args) {
        printf_err(_fmt + "\n", _args);
    }

    public void printfln_err_verbose(final String _fmt, final Object... _args) {
        printf_err_verbose(_fmt + "\n", _args);
    }

    public void printfln_verbose(final String _fmt, final Object... _args) {
        printf_verbose(_fmt + "\n", _args);
    }

    public void printfln_warn(final String _fmt, final Object... _args) {
        printf_warn(_fmt + "\n", _args);
    }

    public void printfln_warn_verbose(final String _fmt, final Object... _args) {
        printf_warn_verbose(_fmt + "\n", _args);
    }

    public void println() {
        printf("\n");
    }

    public void println(final String _str) {
        getOut().println(_str);
    }

    public void println_err() {
        printf_err("\n");
    }

    public void println_err(final String _str) {
        getErr().println(StringUtils.colorizeForTerminal(_str, TerminalColor.BRIGHT_RED));
    }

    public void println_err_verbose(final String _msg) {
        if (!isVerbose()) {
            return;
        }
        getErr().println(StringUtils.colorizeForTerminal(_msg, TerminalColor.BRIGHT_RED));
    }

    public void println_verbose(final String _msg) {
        if (!isVerbose()) {
            return;
        }
        getOut().println(_msg);
    }

    public void println_warn(final String _str) {
        getErr().println(StringUtils.colorizeForTerminal(_str, TerminalColor.YELLOW));
    }

    public void println_warn_verbose(final String _msg) {
        if (!isVerbose()) {
            return;
        }
        getErr().println(StringUtils.colorizeForTerminal(_msg, TerminalColor.YELLOW));
    }
}
