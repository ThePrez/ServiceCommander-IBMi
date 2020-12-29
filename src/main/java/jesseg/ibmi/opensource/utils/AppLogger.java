package jesseg.ibmi.opensource.utils;

import java.io.PrintStream;

public class AppLogger {

    private final PrintStream m_out;
    private final PrintStream m_err;
    private final boolean m_verbose;

    public AppLogger(final boolean _verbose) {
        m_out = System.out;
        m_err = System.err;
        m_verbose = _verbose;
        // TODO: have options to write verbose output to file or log4j or something (that's the whole point of this class)
    }

    public PrintStream printf(final String _fmt, final Object... _args) {
        return m_out.printf(_fmt, _args);
    }

    public PrintStream println() {
        return printf("\n");
    }

    public PrintStream printfln(final String _fmt, final Object... _args) {
        return printf(_fmt + "\n", _args);
    }

    public PrintStream printf_err(final String _fmt, final Object... _args) {
        return m_err.printf(_fmt, _args);
    }

    public PrintStream println_err() {
        return printf_err("\n");
    }

    public PrintStream printfln_err(final String _fmt, final Object... _args) {
        return printf_err(_fmt + "\n", _args);
    }

    public PrintStream printf_verbose(final String _fmt, final Object... _args) {
        if (!m_verbose) {
            return m_out;
        }
        return m_out.printf(_fmt, _args);
    }

    public void println_verbose(final String _msg) {
        if (!m_verbose) {
            return;
        }
        m_out.println(_msg);
    }

    public PrintStream printfln_verbose(final String _fmt, final Object... _args) {
        return printf_verbose(_fmt + "\n", _args);
    }

    public PrintStream printf_err_verbose(final String _fmt, final Object... _args) {
        if (!m_verbose) {
            return m_err;
        }
        return m_err.printf(_fmt, _args);
    }

    public void println_err_verbose(final String _msg) {
        if (!m_verbose) {
            return;
        }
        m_err.println(_msg);
    }

    public PrintStream printfln_err_verbose(final String _fmt, final Object... _args) {
        return printf_err_verbose(_fmt + "\n", _args);
    }

    public void println(String _str) {
        m_out.println(_str);
    }

    public void println_err(String _str) {
        m_err.println(_str);
    }

    public void exception(Throwable _exc) {
        _exc.printStackTrace(System.err);
    }
}
