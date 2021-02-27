package jesseg.ibmi.opensource;

import java.util.LinkedList;
import java.util.List;

import jesseg.ibmi.opensource.yaml.YamlServiceDef;

/**
 * Abstract class representing a definition of a service. Since currently only
 * .yaml files are supported, the only implementing class is {@link YamlServiceDef}
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
     * @return the working directory (this method will not return <tt>null</tt>
     */
    public String getWorkingDirectory() {
        return System.getProperty("user.dir");
    }

    /**
     * Whether or not the service is to inherit environment variables from the launching process (the service launcher tool's process itself)
     */
    public boolean isInheritingEnvironmentVars() {
        return true;
    }
}
