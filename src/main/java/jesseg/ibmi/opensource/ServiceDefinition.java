package jesseg.ibmi.opensource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.yaml.YamlServiceDef;

/**
 * Abstract class representing a definition of a service. Since currently only
 * .yaml files are supported, the commonly implementing class is {@link YamlServiceDef}.
 * Ad-hoc services can also be programmatically defined by extending this class
 *
 * @author Jesse Gorzinski
 */
public abstract class ServiceDefinition {

    /**
     * An enum for if/how the program is submitted to batch. Currently, QP2SHELL2 and non-batch
     * invocations are supported. In the future, there may be more methods, such as QSH.
     *
     * @author Jesse Gorzinski
     */
    public enum BatchMode {
        /**
         * Submit to batch using the QP2SHELL2 program
         */
        BATCH_QP2SHELL2,
        /**
         * Do not submit to batch
         */
        NO_BATCH;
        public static BatchMode guessFromConfigString(final String _configString) {
            final String lc = _configString.toLowerCase().trim();
            switch (lc) {
                case "yes":
                case "1":
                case "qp2shell2":
                case "qp2":
                case "y":
                case "true":
                case "t":
                    return BATCH_QP2SHELL2;
                case "no":
                case "0":
                case "n":
                case "false":
                case "f":
                    return NO_BATCH;
            }
            return valueOf(_configString);
        }

        public boolean isBatch() {
            return this != NO_BATCH;
        }
    }

    /**
     * The technique used to check whether the service is alive or not
     */
    public enum CheckAliveType {
        /**
         * Check whether the job is alive by checking whether a job with the specified job name is active
         */
        JOBNAME,
        /**
         * Check whether the job is alive by checking whether a job is listening on the given port
         */
        PORT
    }

    /**
     * If submitting to batch, the custom job name to be using for the batch job. This value
     * is ignored if not submitting to batch.
     *
     * @return the custom job name, or <tt>null</tt> if a default job name is to be used
     */
    public String getBatchJobName() {
        return null;
    }

    /**
     * @see BatchMode
     */
    public BatchMode getBatchMode() {
        return BatchMode.NO_BATCH;
    }

    /**
     * The criteria for checking whether the job is alive or not (either a job name or port number)
     *
     * @return the job name or port number, depending on the return value of {@link #getCheckAliveType()}
     */
    public abstract String getCheckAliveCriteria();

    /**
     * @see CheckAliveType
     */
    public abstract CheckAliveType getCheckAliveType();

    /**
     * A list of other services that are dependencies of this one, if any.
     *
     * @return a list of dependencies, in simple name format, or an empty list if there are none. This method will not return <tt>null</tt>.
     */
    public List<String> getDependencies() {
        return new LinkedList<String>();
    }

    /**
     * A list of custom environment variables to be set in the process for the service when it is launched, if any.
     *
     * @return a list of environment variables, in <tt>KEY=VALUE</tt> format, or an empty list if there are none. This method will not return <tt>null</tt>.
     */
    public List<String> getEnvironmentVars() {
        return new LinkedList<String>();
    }

    /**
     * A "friendly" name for the service
     *
     * @return the friendly name
     */
    public String getFriendlyName() {
        return getName();
    }

    /**
     * A list of custom-defined groups that this service is a member of, if any.
     *
     * @return a list of groups, or an empty list if there are none. This method will not return <tt>null</tt>.
     */
    public List<String> getGroups() {
        return new LinkedList<String>();
    }

    /**
     * The name of the service. This will typically match the filename, without the extension. For instance, <tt>myservice</tt> will be defined
     * in a file <tt>myservice.yaml</tt>.
     *
     * @return the name of the service
     */
    public abstract String getName();

    /**
     * If submitting to batch, custom options to the SBMJOB command, for instance <tt>JOBD</tt>
     * options.
     *
     * @return custom options to the SBMJOB command, or an empty string. This method will never return <tt>null</tt>.
     */
    public String getSbmJobOpts() {
        return "";
    }

    /**
     * The length of time to wait for the service to stop.
     *
     * @return the wait time, in seconds
     */
    public int getShutdownWaitTime() {
        return 45;
    }

    /**
     * Return a human description of the source of this service description (for instance, a filename).
     *
     * @return the source (shall not return <tt>null</tt>).
     */
    public abstract String getSource();

    /**
     * Get the command used to start the service. This command is expected to be run in a PASE shell (namely, <tt>/QOpenSys/usr/bin/sh</tt>)
     *
     * @return the start command
     */
    public abstract String getStartCommand();

    /**
     * The length of time to wait for the service to start.
     *
     * @return the wait time, in seconds
     */
    public int getStartupWaitTime() {
        return 60;
    }

    /**
     * (optional) Get the command used to stop the service. This command is expected to be run in a PASE shell (namely, <tt>/QOpenSys/usr/bin/sh</tt>)
     *
     * @return the stop command, or <tt>null</tt> if there is no special stop command for this service
     */
    public String getStopCommand() {
        return null;
    }

    /**
     * Get the working directory to be used for starting and stopping the service
     *
     * @return the working directory (this method will not return <tt>null</tt>)
     */
    public String getEffectiveWorkingDirectory() {
        return System.getProperty("user.dir");
    }

    /**
     * Get the working directory that is configured to be used for starting and stopping the service, or <tt>null</tt> if unset
     *
     * @return the working directory (this method will return <tt>null</tt> if there is no directory configured
     */
    public String getConfiguredWorkingDirectory() {
        return null;
    }

    /**
     * Whether or not the service is to inherit environment variables from the launching process (the service launcher tool's process itself)
     */
    public boolean isInheritingEnvironmentVars() {
        return true;
    }

    public static void checkForCheckaliveConflicts(AppLogger _logger, Collection<ServiceDefinition> _defs) {
        checkForCheckaliveConflicts(_logger, new ArrayList(_defs));
    }

    public static void checkForCheckaliveConflicts(AppLogger _logger, List<ServiceDefinition> _defs) {
        HashMap<String, List<ServiceDefinition>> defsByCheckalive = new HashMap<String, List<ServiceDefinition>>();
        for (ServiceDefinition def : _defs) {
            String checkAliveKey = "" + def.getCheckAliveType().name() + ":" + def.getCheckAliveCriteria();
            List<ServiceDefinition> currentList = defsByCheckalive.get(checkAliveKey);
            if (null == currentList) {
                defsByCheckalive.put(checkAliveKey, currentList = new LinkedList<ServiceDefinition>());
            }
            currentList.add(def);
        }
        for (Entry<String, List<ServiceDefinition>> entry : defsByCheckalive.entrySet()) {
            List<ServiceDefinition> defs = entry.getValue();
            if (1 >= defs.size()) {
                continue;
            }
            String warningStr = "WARNING: the following services all have conflicting definitions for liveliness check " + entry.getKey() + ":";
            for (ServiceDefinition def : defs) {
                warningStr += "\n    " + def.getName() + " (" + def.getFriendlyName() + ")";
            }
            _logger.println_warn(warningStr);
        }
    }
}
