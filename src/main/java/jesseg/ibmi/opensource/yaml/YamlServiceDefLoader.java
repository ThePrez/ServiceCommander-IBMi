package jesseg.ibmi.opensource.yaml;

import java.io.File;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jesseg.ibmi.opensource.AppDirectories;
import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.ServiceDefinition;
import jesseg.ibmi.opensource.utils.AppLogger;

/**
 * Loads service definitions from <tt>.yaml</tt> files found in any of the global, user, or 
 * manually-specified directories.
 * 
 * @author Jesse Gorzinski
 *
 */
public class YamlServiceDefLoader {

    Pattern s_filePattern = Pattern.compile("^([a-z\\-_]+)(\\.yaml)$");

    public HashMap<String, ServiceDefinition> loadFromYamlFiles(final AppLogger _logger) throws SCException {
        final HashMap<String, ServiceDefinition> ret = new HashMap<String, ServiceDefinition>();
        ret.putAll(loadFromDirectory(AppDirectories.conf.getGlobalServicesDirOrNull(), _logger));
        ret.putAll(loadFromDirectory(AppDirectories.conf.getUserServicesDirOrNull(), _logger));
        ret.putAll(loadFromDirectory(AppDirectories.conf.getCustomServicesDirOrNull(), _logger));
        return ret;
    }

    HashMap<String, ServiceDefinition> loadFromDirectory(final File _dir, final AppLogger _logger) throws SCException {
        final HashMap<String, ServiceDefinition> ret = new HashMap<String, ServiceDefinition>();
        if (null == _dir) {
            return ret;
        }
        for (final File f : _dir.listFiles()) {
            final String fileName = f.getName();
            final Matcher m = s_filePattern.matcher(fileName);
            if (!m.find()) {
                _logger.println_err("WARNING: Ignoring file: " + f.getAbsolutePath());
            }
            final String serviceName = m.group(1);
            ret.put(serviceName, new YamlServiceDef(serviceName, f, _logger));
        }
        ret.putAll(loadFromDirectory(AppDirectories.conf.getGlobalServicesDirOrNull(), _logger));
        return ret;
    }
}
