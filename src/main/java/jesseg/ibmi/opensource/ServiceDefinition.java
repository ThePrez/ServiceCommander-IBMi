package jesseg.ibmi.opensource;

import java.util.LinkedList;
import java.util.List;

import jesseg.ibmi.opensource.utils.ListUtils;
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

    public interface CheckAlive {
        public CheckAliveType getType();

        public String getValue();
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

    public static class SimpleCheckAlive implements CheckAlive {

        private final CheckAliveType m_type;

        private final String m_value;

        public SimpleCheckAlive(final CheckAliveType _type, final String _value) {
            super();
            this.m_type = _type;
            this.m_value = _value;
        }

        @Override
        public boolean equals(final Object _o) {
            if (!(_o instanceof SimpleCheckAlive)) {
                return false;
            }
            return toString().equals(_o.toString());
        }

        @Override
        public CheckAliveType getType() {
            return m_type;
        }

        @Override
        public String getValue() {
            return m_value;
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public String toString() {
            return "" + m_type.name() + ":" + m_value;
        }
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

    public abstract List<CheckAlive> getCheckAlives();

    public String getCheckAlivesHumanReadable() {
        return ListUtils.toString(getCheckAlives(), ", ");
    }

    public List<ServiceDefinition> getClusterBackends() {
        return new LinkedList<ServiceDefinition>();
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
     * A list of other services that are dependencies of this one, if any.
     *
     * @return a list of dependencies, in simple name format, or an empty list if there are none. This method will not return <tt>null</tt>.
     */
    public List<String> getDependencies() {
        return new LinkedList<String>();
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

    public boolean isAdHoc() {
        return false;
    }

    public boolean isClusterMode() {
        return !getClusterBackends().isEmpty();
    }

    public boolean isInGroup(final String _group) {
        final List<String> groups = getGroups();
        for (final String group : groups) {
            if (group.equalsIgnoreCase(_group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether or not the service is to inherit environment variables from the launching process (the service launcher tool's process itself)
     */
    public boolean isInheritingEnvironmentVars() {
        return true;
    }
}
