package jesseg.ibmi.opensource;

import jesseg.ibmi.opensource.utils.AppLogger;

/**
 * Custom exception class
 * 
 * @author Jesse Gorzinski
 */
@SuppressWarnings("serial")
public class SCException extends Exception {

    public enum FailureType {
        ERROR_EXECUTING_STARTUP_CMD, GENERAL_ERROR, UNSUPPORTED_OPERATION, TIMEOUT_ON_SERVICE_STARTUP, MISSING_SERVICE_DEF, ERROR_CHECKING_STATUS, INVALID_SERVICE_CONFIG, ERROR_STARTING_DEPENDENCY, TIMEOUT_ON_SERVICE_STOP
    }

    private final FailureType m_failure;
    private final AppLogger m_logger;

    public SCException(final AppLogger _logger, final Throwable _causedBy, final FailureType _failure, final String _messageFmt, final Object... _args) {
        super(String.format(_messageFmt, _args), _causedBy);
        m_logger = _logger;
        m_failure = _failure;
        m_logger.println_err(super.getMessage());
    }

    public SCException(final AppLogger _logger, final FailureType _failure, final String _messageFmt, final Object... _args) {
        this(_logger, new RuntimeException(), _failure, _messageFmt, _args);
    }

    public FailureType getFailureType() {
        return m_failure;
    }
}
