package jesseg.ibmi.opensource;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Stack;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.AppLogger.DeferredLogger;

import jesseg.ibmi.opensource.OperationExecutor.Operation;
import jesseg.ibmi.opensource.SCException.FailureType;

public class AsyncOperationSet {
    final Stack<SCException> m_exceptions = new Stack<SCException>();
    final LinkedHashMap<Thread, AppLogger.DeferredLogger> m_list = new LinkedHashMap<Thread, AppLogger.DeferredLogger>();
    private final AppLogger m_logger;

    public AsyncOperationSet(final AppLogger _logger) {
        m_logger = _logger;
    }

    public void join() throws SCException {
        m_logger.println_verbose("Waiting for worker threads to complete...");
        for (final Entry<Thread, DeferredLogger> output : m_list.entrySet()) {
            try {
                output.getKey().join();
                output.getValue().close();
            } catch (final Exception e) {
                m_exceptions.push(SCException.fromException(e, m_logger));
            }
        }
        if (!m_exceptions.isEmpty()) {
            throw m_exceptions.pop();
        }
    }

    public void start(final Operation _op, final String service, final ServiceDefinitionCollection _serviceDefs, final String _exceptionMsg) {
        final DeferredLogger deferredLogger = new DeferredLogger(m_logger);
        m_logger.printf_verbose("Performing operation '%s' on service '%s' (asynchronously)\n", _op.name(), service);
        final Thread t = new Thread((Runnable) () -> {
            try {
                new OperationExecutor(_op, service, _serviceDefs, deferredLogger).execute();
            } catch (final SCException e) {
                if (null == _exceptionMsg) {
                    m_exceptions.push(e);
                } else {
                    m_exceptions.push(new SCException(deferredLogger, e, FailureType.GENERAL_ERROR, _exceptionMsg + ": " + e.getLocalizedMessage()));
                }
            }
        }, "Async-" + _op.name() + ":" + service);
        m_list.put(t, deferredLogger);
        t.start();
    }

}
