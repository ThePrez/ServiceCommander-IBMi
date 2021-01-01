package jesseg.ibmi.opensource;

import java.io.File;

/**
 * Just a place to keep track of where various directories are that the application uses. 
 * 
 * <br>
 * 
 * Most notably, there are three places that the tool will search for service definition files:
 * <ul>
 *      <li> A global directory (/QOpenSys/etc/sc/services)
 *      <li> A user-specific directory($HOME/.sc/services)
 *      <li> If defined, whatever the value of the <tt>services.dir</tt> system property is
 * </ul>
 * 
 * @author Jesse Gorzinski
 *
 */
public enum AppDirectories {
    conf;
    private static File s_globalServicesDir = new File("/QOpenSys/etc/sc/services");

    public File getCustomServicesDirOrNull() {
        final String servicesDir = System.getProperty("services.dir", null);
        if (null == servicesDir) {
            return null;
        }
        return validateDir(new File(servicesDir));
    }

    public File getGlobalServicesDirOrNull() {
        return validateDir(s_globalServicesDir);
    }

    public File getLogsDirectory() {
        final File logDir = new File(System.getProperty("user.home", "~") + "/.sc/logs");
        if (!logDir.isDirectory()) {
            logDir.mkdirs();
        }
        return logDir;
    }

    public File getScriptsDirectory() {
        final File retDir = new File(System.getProperty("user.home", "~") + "/.sc/.scripts");
        if (!retDir.isDirectory()) {
            retDir.mkdirs();
        }
        return retDir;
    }

    public File getUserServicesDirOrNull() {
        return validateDir(new File(System.getProperty("user.home") + "/.sc/services"));
    }

    private File validateDir(final File _dir) {
        if (null == _dir || !_dir.isDirectory() || !_dir.canRead() || !_dir.canExecute()) {
            return null;
        }
        return _dir;
    }

}
