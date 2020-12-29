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

    private FailureType m_failure;
    private AppLogger m_logger;

    public SCException(AppLogger _logger, Throwable _causedBy, FailureType _failure, String _messageFmt, Object... _args) {
        super(String.format(_messageFmt, _args), _causedBy);
        m_logger = _logger;
        m_failure = _failure;
        _logger.println_err(super.getMessage());
    }

    public SCException(AppLogger _logger, FailureType _failure, String _messageFmt, Object... _args) {
        this(_logger, new RuntimeException(), _failure, _messageFmt, _args);
    }
}
