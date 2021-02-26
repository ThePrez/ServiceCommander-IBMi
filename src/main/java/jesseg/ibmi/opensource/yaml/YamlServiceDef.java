package jesseg.ibmi.opensource.yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.ServiceDefinition;
import jesseg.ibmi.opensource.utils.AppLogger;
import jesseg.ibmi.opensource.utils.StringUtils;

/**
 * A service definition loaded from a .yaml file.
 * 
 * @author Jesse Gorzinski
 */
public class YamlServiceDef extends ServiceDefinition {
    private static final int UNSPECIFIED_INT = -1;
    private final File m_source;
    private final String m_name;
    private final String m_startCmd;
    private final CheckAliveType m_checkAliveType;
    private final String m_checkAliveCriteria;
    private final List<String> m_envVars;
    private final boolean m_isInherintingEnvVars;
    private final String m_workingDir;
    private final String m_stopCmd;
    private final int m_startupWaitTime;
    private final int m_stopWaitTime;
    private final String m_sbmJobOpts;
    private final String m_friendlyName;
    private final List<String> m_dependencies;
    private final String m_batchJobName;
    private final BatchMode m_batchMode;
    private final List<String> m_groups;

    @SuppressWarnings("unchecked")
    public YamlServiceDef(final String _name, final File _file, final AppLogger _logger) throws SCException {
        try {
            _logger.println_verbose("Initializing service " + _name);
            _logger.println_verbose("Loading yaml service definition from file " + _file);
            m_source = _file;
            m_name = _name;
            final Yaml yaml = new Yaml();
            final Map<String, Object> yamlData;
            try (FileInputStream fis = new FileInputStream(_file)) {
                yamlData = yaml.load(fis);
            }

            // First, process all the required options, because these should be fatal if nonpresent
            m_startCmd = getRequiredYamlString(yamlData, "start_cmd");
            final String checkAliveType = getRequiredYamlString(yamlData, "check_alive");
            try {
                m_checkAliveType = CheckAliveType.valueOf(checkAliveType.toUpperCase());
            } catch (final Exception e) {
                throw new IOException("ERROR: attribute 'check_alive' contains an invalid value for service '" + m_name + "'");
            }

            m_checkAliveCriteria = getRequiredYamlString(yamlData, "check_alive_criteria");

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
        return null == m_batchJobName ? super.getBatchJobName() : m_batchJobName;
    }

    @Override
    public BatchMode getBatchMode() {
        return null == m_batchMode ? super.getBatchMode() : m_batchMode;
    }

    @Override
    public String getCheckAliveCriteria() {
        return m_checkAliveCriteria;
    }

    @Override
    public CheckAliveType getCheckAliveType() {
        return m_checkAliveType;
    }

    @Override
    public List<String> getDependencies() {
        return null == m_dependencies ? super.getDependencies() : m_dependencies;
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
    public String getWorkingDirectory() {
        return null == m_workingDir ? super.getWorkingDirectory() : m_workingDir;
    }

    @Override
    public boolean isInheritingEnvironmentVars() {
        return m_isInherintingEnvVars;
    }
}
