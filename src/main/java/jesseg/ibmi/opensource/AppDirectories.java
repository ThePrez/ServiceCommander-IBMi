package jesseg.ibmi.opensource;

import java.io.File;

public enum AppDirectories {
    conf;
    private static File s_globalServicesDir = new File("/QOpenSys/etc/services");

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
        final File retDir = new File(System.getProperty("user.home", "~") + "/.sc/scripts");
        if (!retDir.isDirectory()) {
            retDir.mkdirs();
        }
        return retDir;
    }

    public File getUserServicesDirOrNull() {
        return validateDir(new File(System.getProperty("user.home") + "/.services"));
    }

    private File validateDir(final File _dir) {
        if (null == _dir || !_dir.isDirectory() || !_dir.canRead() || !_dir.canExecute()) {
            return null;
        }
        return _dir;
    }

}
