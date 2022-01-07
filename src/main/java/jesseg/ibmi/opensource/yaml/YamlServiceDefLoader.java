package jesseg.ibmi.opensource.yaml;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.theprez.jcmdutils.AppLogger;

import jesseg.ibmi.opensource.AppDirectories;
import jesseg.ibmi.opensource.SCException;
import jesseg.ibmi.opensource.ServiceDefinitionCollection;

/**
 * Loads service definitions from <tt>.yaml</tt> files found in any of the global, user, or
 * manually-specified directories.
 *
 * @author Jesse Gorzinski
 *
 */
public class YamlServiceDefLoader {

    public static final String PROP_IGNORE_GLOBALS = "sc.ignoreglobalconfigs";
    private static final Pattern s_filePattern = Pattern.compile("^([a-z\\-_0-9]+)\\.y[a]{0,1}ml$");

    public static Pattern getFilePattern() {
        return s_filePattern;
    }

    public static String getServiceNameFromFile(final File _f) {
        final Matcher m = s_filePattern.matcher(_f.getName());
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    ServiceDefinitionCollection loadFromDirectory(final File _dir, final AppLogger _logger) throws SCException {
        final ServiceDefinitionCollection ret = new ServiceDefinitionCollection();
        if (null == _dir) {
            return ret;
        }
        File[] files = _dir.listFiles();
        if (null == files) {
            _logger.printfln_warn_verbose("Unable to read from directory '%s'", "" + _dir);
        }
        for (final File f : files) {
            if (f.isDirectory()) {
                continue;
            }
            final String fileName = f.getName();
            final Matcher m = s_filePattern.matcher(fileName);
            if (!m.find()) {
                _logger.println_warn("WARNING: Ignoring file: " + f.getAbsolutePath());
                continue;
            }
            final String serviceName = m.group(1);
            try {
                ret.put(new YamlServiceDef(serviceName, f, _logger));
            } catch (final SCException e) {
                _logger.println_warn("WARNING: Ignoring file due to load errors: " + f.getAbsolutePath());
            }
        }
        return ret;
    }

    public ServiceDefinitionCollection loadFromYamlFiles(final AppLogger _logger, final boolean _ignoreGlobals) throws SCException {
        final ServiceDefinitionCollection ret = new ServiceDefinitionCollection();
        if (_ignoreGlobals) {
            _logger.println_verbose("Ignoring globally configured services");
        } else {
            File globalDir = AppDirectories.conf.getGlobalServicesDirOrNull();
            ret.putAll(loadFromDirectory(globalDir, _logger));
            ret.putAll(loadFromDirectory(new File(globalDir, "system"), _logger));

        }
        ret.putAll(loadFromDirectory(AppDirectories.conf.getUserServicesDirOrNull(), _logger));
        ret.putAll(loadFromDirectory(AppDirectories.conf.getCustomServicesDirOrNull(), _logger));
        return ret;
    }
}
