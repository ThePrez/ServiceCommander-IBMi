package jesseg.ibmi.opensource.utils;

/**
 * Simple string utilities. Not much here.
 *
 * @author Jesse Gorzinski
 *
 */
public class StringUtils {
    public enum TerminalColor {
        GREEN("\u001B[32m"), PURPLE("\u001B[35m"), RED("\u001B[31m"), BRIGHT_RED("\u001b[31;1m"), YELLOW("\u001B[33m"), WHITE("\u001B[37m"), CYAN("\u001B[36m"), BLUE("\u001b[34m");

        public static String stripCodesFromString(final String _str) {
            if (!s_isTerminalColorsSupported) {
                return _str;
            }
            String ret = _str;
            for (final TerminalColor color : values()) {
                ret = ret.replace(color.m_code, "");
            }
            ret.replace(TERM_COLOR_RESET, "");
            return ret;
        }

        private final String m_code;

        TerminalColor(final String _code) {
            m_code = _code;
        }

        String getCode() {
            return m_code;
        }
    }

    /**
     * System property that can be used for disabling terminal colorizations
     */
    public static final String PROP_DISABLE_COLORS = "sc.disablecolors";

    private static final String LOTSA_SPACES = "                                             ";

    // SSH_TTY will be unset in non-SSH environments, and System.console() returns null when output is being piped
    private static final boolean s_isTerminalColorsSupported = (null != System.console() && !isEmpty(System.getenv("SSH_TTY")) && !Boolean.getBoolean(PROP_DISABLE_COLORS));

    private static final String TERM_COLOR_RESET = "\u001B[0m";

    public static String colorizeForTerminal(final String _str, final TerminalColor _color) {
        if (s_isTerminalColorsSupported) {
            return _color.getCode() + _str + TERM_COLOR_RESET;
        } else {
            return _str;
        }
    }

    public static boolean isEmpty(final String _str) {
        return (null == _str) || (_str.trim().isEmpty());
    }

    public static String spacePad(final String _str, final int _len) {
        if (0 == _len) {
            return "";
        }
        String ret = _str + LOTSA_SPACES;
        while (ret.length() < _len) {
            ret += LOTSA_SPACES;
        }
        return ret.substring(0, _len);
    }
}
