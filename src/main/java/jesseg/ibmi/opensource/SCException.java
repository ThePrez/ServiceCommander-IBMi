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
        ERROR_CHECKING_STATUS, ERROR_EXECUTING_STARTUP_CMD, ERROR_STARTING_DEPENDENCY, ERROR_STOPPING_DEPENDENT, GENERAL_ERROR, INVALID_SERVICE_CONFIG, MISSING_SERVICE_DEF, TIMEOUT_ON_SERVICE_STARTUP, TIMEOUT_ON_SERVICE_STOP, UNSUPPORTED_OPERATION
    }

    public static SCException fromException(final Exception e, final AppLogger _logger) {
        return (e instanceof SCException) ? (SCException) e : new SCException(_logger, FailureType.GENERAL_ERROR, e.getLocalizedMessage());
    }
    private final FailureType m_failure;

    private final AppLogger m_logger;

    public SCException(final AppLogger _logger, final FailureType _failure, final String _messageFmt, final Object... _args) {
        this(_logger, new RuntimeException(), _failure, _messageFmt, _args);
    }

    public SCException(final AppLogger _logger, final Throwable _causedBy, final FailureType _failure, final String _messageFmt, final Object... _args) {
        super(String.format(_messageFmt, _args), _causedBy);
        m_logger = _logger;
        m_failure = _failure;
        m_logger.println_err(super.getMessage());
    }

    public FailureType getFailureType() {
        return m_failure;
    }
}
