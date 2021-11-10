package jesseg.ibmi.opensource.yaml;

import java.io.File;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jesseg.ibmi.opensource.AppDirectories;
import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.ServiceDefinition;
import jesseg.ibmi.opensource.ServiceDefinitionCollection;
import jesseg.ibmi.opensource.utils.AppLogger;

/**
 * Loads service definitions from <tt>.yaml</tt> files found in any of the global, user, or
 * manually-specified directories.
 *
 * @author Jesse Gorzinski
 *
 */
public class YamlServiceDefLoader {

    public static final String PROP_IGNORE_GLOBALS = "sc.ignoreglobalconfigs";
    private final Pattern s_filePattern = Pattern.compile("^([a-z\\-_0-9]+)\\.y[a]{0,1}ml$");

    ServiceDefinitionCollection loadFromDirectory(final File _dir, final AppLogger _logger) throws SCException {
        final ServiceDefinitionCollection ret = new ServiceDefinitionCollection();
        if (null == _dir) {
            return ret;
        }
        for (final File f : _dir.listFiles()) {
            final String fileName = f.getName();
            final Matcher m = s_filePattern.matcher(fileName);
            if (!m.find()) {
                _logger.println_warn("WARNING: Ignoring file: " + f.getAbsolutePath());
                continue;
            }
            final String serviceName = m.group(1);
            ret.put(new YamlServiceDef(serviceName, f, _logger));
        }
        return ret;
    }

    public ServiceDefinitionCollection loadFromYamlFiles(final AppLogger _logger) throws SCException {
        final ServiceDefinitionCollection ret = new ServiceDefinitionCollection();
        if (Boolean.getBoolean(PROP_IGNORE_GLOBALS)) {
            _logger.println_verbose("Ignoring globally configured services");
        } else {
            ret.putAll(loadFromDirectory(AppDirectories.conf.getGlobalServicesDirOrNull(), _logger));
        }
        ret.putAll(loadFromDirectory(AppDirectories.conf.getUserServicesDirOrNull(), _logger));
        ret.putAll(loadFromDirectory(AppDirectories.conf.getCustomServicesDirOrNull(), _logger));
        return ret;
    }
}
