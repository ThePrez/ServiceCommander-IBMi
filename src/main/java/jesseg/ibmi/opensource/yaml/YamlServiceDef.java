package jesseg.ibmi.opensource.yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.StringUtils;

import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.ServiceDefinition;
import jesseg.ibmi.opensource.ServiceDefinition.CheckAlive;

/**
 * A service definition loaded from a .yaml file.
 *
 * @author Jesse Gorzinski
 */
public class YamlServiceDef extends ServiceDefinition {

    private static final int UNSPECIFIED_INT = -1;
    final List<CheckAlive> m_backendDeclarations;
    List<ServiceDefinition> m_backends = null;
    private final String m_batchJobName;
    private final BatchMode m_batchMode;
    private final List<CheckAlive> m_checkAlives = new LinkedList<CheckAlive>();
    private final List<String> m_dependencies;
    private final List<String> m_envVars;
    private final String m_friendlyName;
    private final List<String> m_groups;
    private final boolean m_isInherintingEnvVars;
    private AppLogger m_logger;
    private final String m_name;
    private final String m_sbmJobOpts;
    private final File m_source;
    private final String m_startCmd;

    private final int m_startupWaitTime;
    private final String m_stopCmd;
    private final int m_stopWaitTime;
    private final String m_workingDir;

    @SuppressWarnings("unchecked")
    public YamlServiceDef(final String _name, final File _file, final AppLogger _logger) throws SCException {
        m_logger = _logger;
        try {
            _logger.println_verbose("Initializing service " + _name);
            _logger.println_verbose("Loading yaml service definition from file " + _file);
            m_source = _file;
            m_name = ((null == _name) ? YamlServiceDefLoader.getServiceNameFromFile(_file) : _name);
            final Yaml yaml = new Yaml();
            final Map<String, Object> yamlData;
            try (FileInputStream fis = new FileInputStream(_file)) {
                yamlData = yaml.load(fis);
            }

            // First, process all the required options, because these should be fatal if nonpresent
            m_startCmd = getRequiredYamlString(yamlData, "start_cmd");

            // For checkalive criteria, support both:
            // - the classic singleton format (with a separate "check_alive" and "check_alive_criteria" with only one value supported
            // - the new format introduced in v1.x, where the only
            Object checkAlive = getRequiredYamlObject(yamlData, "check_alive");
            try {
                final CheckAliveType checkAliveType = CheckAliveType.valueOf(checkAlive.toString().toUpperCase());

                // If we get here, we know we are processing the classic singleton format
                final String criteria = getRequiredYamlString(yamlData, "check_alive_criteria");
                m_checkAlives.add(new SimpleCheckAlive(checkAliveType, criteria));
            } catch (final Exception e) {
                // If we get here, we're processing the new v1.x format, which can either be a comma-separated list or an actual YAML array
                if (checkAlive instanceof Number) {
                    checkAlive = checkAlive.toString();
                }
                if (checkAlive instanceof String) {
                    final String[] components = checkAlive.toString().split("\\s*,\\s*");
                    for (final String component : components) {
                        m_checkAlives.add(getCheckAliveFromString(component));
                    }
                } else if (checkAlive instanceof List<?>) {
                    for (final Object component : (List<?>) checkAlive) {
                        m_checkAlives.add(getCheckAliveFromString(component.toString()));
                    }
                }
            }
            if (m_checkAlives.isEmpty()) {
                throw new IOException("ERROR: attribute 'check_alive' contains an invalid value for service '" + m_name + "'");
            }

            // now for some optional stuff.
            m_friendlyName = getRequiredYamlString(yamlData, "name");
            m_workingDir = getOptionalYamlString(yamlData, "dir");
            m_stopCmd = getOptionalYamlString(yamlData, "stop_cmd");

            m_startupWaitTime = getOptionalYamlInt(yamlData, "startup_wait_time");
            m_stopWaitTime = getOptionalYamlInt(yamlData, "stop_wait_time");

            m_sbmJobOpts = getOptionalYamlString(yamlData, "sbmjob_opts");
            m_batchJobName = getOptionalYamlString(yamlData, "sbmjob_jobname");

            m_envVars = (List<String>) yamlData.remove("environment_vars");
            m_dependencies = (List<String>) yamlData.remove("service_dependencies");

            m_backendDeclarations = new LinkedList<CheckAlive>();
            String cluster = getOptionalYamlString(yamlData, "cluster");
            if (StringUtils.isNonEmpty(cluster)) {
                final String[] components = cluster.toString().split("\\s*,\\s*");
                for (final String component : components) {
                    m_backendDeclarations.add(getCheckAliveFromString(component));
                }
            }
            
            m_groups = (List<String>) yamlData.remove("groups");

            m_isInherintingEnvVars = getOptionalYamlBool(yamlData, "environment_is_inheriting_vars", true);

            final String batchMode = getOptionalYamlString(yamlData, "batch_mode");
            if (null == batchMode) {
                m_batchMode = BatchMode.NO_BATCH;
            } else {
                try {
                    m_batchMode = BatchMode.guessFromConfigString(batchMode);
                } catch (final Exception e) {
                    throw new IOException("Invalid value specified for attribute 'batch_mode': " + batchMode);
                }
            }

            // Anything left in the parsed yaml at this point is an attribute that we didn't parse. Issue a warning
            for (final String key : yamlData.keySet()) {
                _logger.printf_warn("WARNING: Unrecognized attribute '%s' in file %s\n", key, _file.getAbsolutePath());
            }
        } catch (final Exception e) {
            throw new SCException(_logger, SCException.FailureType.INVALID_SERVICE_CONFIG, "Invalid configuration for service '%s' from file [%s]: %s", _name, _file.getAbsolutePath(), e.getLocalizedMessage());
        }
    }

    @Override
    public String getBatchJobName() {
        // If we don't have a job name, but we do have a guess from the checkalive criteria, infer it
        if (StringUtils.isEmpty(m_batchJobName) && BatchMode.NO_BATCH != getBatchMode()) {
            for (final CheckAlive checkalive : m_checkAlives) {
                if (CheckAliveType.JOBNAME == checkalive.getType()) {
                    return checkalive.getValue().replaceAll("^.*\\/", "").trim();
                }
            }
        }
        return null == m_batchJobName ? super.getBatchJobName() : m_batchJobName;
    }

    @Override
    public BatchMode getBatchMode() {
        return null == m_batchMode ? super.getBatchMode() : m_batchMode;
    }

    private CheckAlive getCheckAliveFromString(final String _str) throws IOException {
        final String str = _str.trim().toUpperCase();
        // System.out.printf("getting checkalive from string '%s'\n", str);
        if (str.isEmpty()) {
            throw new IOException("ERROR: attribute 'check_alive' contains an invalid value for service '" + m_name + "'");
        }
        // First, check for a simple port number
        try {
            return new SimpleCheckAlive(CheckAliveType.PORT, Integer.valueOf(str).toString());
        } catch (final Exception e) {
        }
        // Next, check for the format PORT:xxx
        if (str.startsWith("PORT:")) {
            try {
                return new SimpleCheckAlive(CheckAliveType.PORT, Integer.valueOf(str.replaceFirst(".*:", "")).toString());
            } catch (final Exception e) {
            }
        }
        // Next, check for the format JOB:sss
        if (str.startsWith("JOB:")) {
            try {
                return new SimpleCheckAlive(CheckAliveType.JOBNAME, str.replaceFirst(".*:", "").trim());
            } catch (final Exception e) {
            }
        }
        // Hmm, must be a job name filter....
        return new SimpleCheckAlive(CheckAliveType.JOBNAME, str);
    }

    @Override
    public List<CheckAlive> getCheckAlives() {
        return m_checkAlives;
    }

    @Override
    public List<ServiceDefinition> getClusterBackends() {
        if (null != m_backends) {
            return m_backends;
        }
        List<ServiceDefinition> ret = new LinkedList<ServiceDefinition>();

        for (final CheckAlive backend : m_backendDeclarations) {
            final String friendlyName = "Backend Job on port " + backend.getValue();
            m_logger.printfln_verbose("Creating backend service for %s", getFriendlyName());
            final String shortName = getName() + "@" + backend.getValue();
            final List<String> envvars = new LinkedList<String>();
            for (String envvar : getEnvironmentVars()) {
                if (!envvar.startsWith("PORT=")) {
                    envvars.add(envvar);
                }
            }
            envvars.add("PORT=" + backend.getValue().trim());
            final String backendStartCommand = getStartCommand();
          //@formatter:off
          ServiceDefinition backendDef = new ServiceDefinition() {
                @Override public List<CheckAlive> getCheckAlives()  { return Collections.singletonList(backend); }
                @Override public String getConfiguredWorkingDirectory() { return YamlServiceDef.this.getConfiguredWorkingDirectory();  }
                @Override public String getEffectiveWorkingDirectory() { return YamlServiceDef.this.getEffectiveWorkingDirectory();  }
                @Override public List<String> getEnvironmentVars()  { return envvars;              }
                @Override public String getFriendlyName()           { return friendlyName; }
                @Override public String getName()                   { return shortName;    }
                @Override public String getSource()                 { return "<backend>";   }
                @Override public String getStartCommand()           { return backendStartCommand;     }
            };
          //@formatter:on
            ret.add(backendDef);
        }
        m_backends = ret;
        return m_backends;
    }

    @Override
    public String getConfiguredWorkingDirectory() {
        return null == m_workingDir ? super.getConfiguredWorkingDirectory() : m_workingDir;
    }

    @Override
    public List<String> getDependencies() {
        return null == m_dependencies ? super.getDependencies() : m_dependencies;
    }

    @Override
    public String getEffectiveWorkingDirectory() {
        // If unspecified, defer to superclass
        if (null == getConfiguredWorkingDirectory()) {
            return super.getEffectiveWorkingDirectory();
        }

        // If absolute path, use it. Otherwise, resolve relative to the location of the source .yaml file
        final File workingDirFile = new File(getConfiguredWorkingDirectory());
        if (workingDirFile.isAbsolute()) {
            return workingDirFile.getAbsolutePath();
        } else {
            return m_source.getParentFile().getAbsolutePath() + "/" + getConfiguredWorkingDirectory();
        }
    }

    @Override
    public List<String> getEnvironmentVars() {
        return (null == m_envVars) ? super.getEnvironmentVars() : m_envVars;
    }

    @Override
    public String getFriendlyName() {
        return null == m_friendlyName ? super.getFriendlyName() : m_friendlyName;
    }

    @Override
    public List<String> getGroups() {
        return null == m_groups ? super.getGroups() : m_groups;
    }

    @Override
    public String getName() {
        return m_name;
    }

    private boolean getOptionalYamlBool(final Map<String, Object> _yamlData, final String _key, final boolean _def) {
        final Object data = _yamlData.remove(_key);
        if (null == data) {
            return _def;
        }
        if (data instanceof Boolean) {
            return ((Boolean) data).booleanValue();
        }
        final String sVal = data.toString().trim().toLowerCase();
        return Boolean.valueOf(sVal) || sVal.startsWith("y") || sVal.equals("1");
    }

    private int getOptionalYamlInt(final Map<String, Object> _yamlData, final String _key) throws IOException {
        final Object data = _yamlData.remove(_key);
        if (null == data) {
            return UNSPECIFIED_INT;
        }
        if (data instanceof Number) {
            return ((Number) data).intValue();
        }
        try {
            return Integer.valueOf(data.toString());
        } catch (final NumberFormatException e) {
            throw new IOException("Invalid value specified for attribute '" + _key + "'");
        }
    }

    private String getOptionalYamlString(final Map<String, Object> _yamlData, final String _key) {
        final Object data = _yamlData.remove(_key);
        if (null == data || StringUtils.isEmpty(data.toString())) {
            return null;
        }
        return data.toString().trim();
    }

    private Object getRequiredYamlObject(final Map<String, Object> _yamlData, final String _key) throws IOException {
        final Object data = _yamlData.remove(_key);
        if (null == data || StringUtils.isEmpty(data.toString())) {
            throw new IOException("Required attribute '" + _key + "' not specified");
        }
        return data;
    }

    private String getRequiredYamlString(final Map<String, Object> _yamlData, final String _key) throws IOException {
        final Object data = _yamlData.remove(_key);
        if (null == data || StringUtils.isEmpty(data.toString())) {
            throw new IOException("Required attribute '" + _key + "' not specified");
        }
        return data.toString().trim();
    }

    @Override
    public String getSbmJobOpts() {
        return null == m_sbmJobOpts ? super.getSbmJobOpts() : m_sbmJobOpts;
    }

    @Override
    public int getShutdownWaitTime() {
        return UNSPECIFIED_INT == m_stopWaitTime ? super.getShutdownWaitTime() : m_stopWaitTime;
    }

    @Override
    public String getSource() {
        return m_source.getAbsolutePath();
    }

    @Override
    public String getStartCommand() {
        return m_startCmd;
    }

    @Override
    public int getStartupWaitTime() {
        return UNSPECIFIED_INT == m_startupWaitTime ? super.getStartupWaitTime() : m_startupWaitTime;
    }

    @Override
    public String getStopCommand() {
        return null == m_stopCmd ? super.getStopCommand() : m_stopCmd;
    }

    @Override
    public boolean isInheritingEnvironmentVars() {
        return m_isInherintingEnvVars;
    }
}
