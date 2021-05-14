package jesseg.ibmi.opensource.utils;

import java.io.Console;
import java.util.LinkedList;
import java.util.List;

import jesseg.ibmi.opensource.utils.StringUtils.TerminalColor;

public class ConsoleQuestionAsker {

    private final Console m_sysConsole;

    public ConsoleQuestionAsker() {
        m_sysConsole = System.console();
        if (null == m_sysConsole) {
            throw new RuntimeException("ERROR: Unable to allocate console for user input");
        }
    }

    public boolean askBooleanQuestion(final AppLogger _logger, final String _dft, final String _fmt, final Object... _args) {
        return askNonEmptyStringQuestion(_logger, _dft, _fmt, _args).matches("(?i)^(y.*|(tr).*|[1-9]+.*)$");
    }

    public <T extends Enum> T askEnumQuestion(final AppLogger _logger, final String _question, final Class<T> _type) {
        final T[] constants = _type.getEnumConstants();
        final String fmt = _question + " (one of: " + StringUtils.arrayToSpaceSeparatedString(constants).replace(' ', '/') + ")";

        while (true) {
            final String response = askNonEmptyStringQuestion(_logger, null, fmt);
            for (final T constant : constants) {
                if (response.trim().equalsIgnoreCase(constant.name())) {
                    return constant;
                }
            }
            _logger.printfln_err("User response does not match criteria. Must be '%s'.", "one of: " + StringUtils.arrayToSpaceSeparatedString(constants).replace(' ', '/'));
        }
    }

    public int askIntQuestion(final AppLogger _logger, final Integer _dft, final String _fmt, final Object... _args) {
        return Integer.valueOf(askStringMatchingRegexQuestion(_logger, (null == _dft ? null : "" + _dft), "^[0-9]+$", "an integer value", _fmt, _args));
    }

    public List<String> askListOfStringsQuestion(final AppLogger _logger, final String _q) {
        m_sysConsole.writer().println(_q);
        m_sysConsole.writer().println("        (press <enter> after each entry, leave blank to entering values)");
        final java.util.List<String> ret = new LinkedList<String>();
        for (long i = 1; true; ++i) {
            final String response = readLine("" + i + "> ");
            if (StringUtils.isEmpty(response)) {
                break;
            }
            ret.add(response.trim());
        }
        return ret;
    }

    public String askNonEmptyStringQuestion(final AppLogger _logger, final String _dft, final String _fmt, final Object... _args) {
        String response = "";
        String fmt = _fmt + " ";
        if (!StringUtils.isEmpty(_dft)) {
            fmt += "[" + _dft + "] ";
        }
        while (StringUtils.isEmpty((response = readLine(fmt, _args)))) {
            if (!StringUtils.isEmpty(_dft)) {
                return _dft.trim();
            }
            _logger.println_warn("Empty response. Asking again");
        }
        return response;
    }

    public String askStringMatchingRegexQuestion(final AppLogger _logger, final String _dft, final String _regex, final String _regexDesc, final String _fmt, final Object... _args) {
        while (true) {
            final String response = askNonEmptyStringQuestion(_logger, _dft, _fmt, _args);
            if (response.matches(_regex)) {
                return response;
            }
            _logger.printfln_err("User response does not match criteria. Must be '%s'.", _regexDesc);
        }
    }

    public String askStringQuestion(final AppLogger _logger, final String _dft, final String _fmt, final Object... _args) {
        final String response = readLine(_fmt + " ", _args);
        if (StringUtils.isEmpty(response) && !StringUtils.isEmpty(_dft)) {
            return _dft.trim();
        }
        return response.trim();
    }

    private String readLine(final String _fmt, final Object... _args) {
        return m_sysConsole.readLine(StringUtils.colorizeForTerminal(_fmt, TerminalColor.GREEN), _args);
    }
}
