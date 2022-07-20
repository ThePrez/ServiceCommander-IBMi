package jesseg.ibmi.opensource;

import java.util.HashMap;
import java.util.Map;

public class SCDaemon implements Runnable {
    private static SCDaemon s_d;
    public static boolean isRunning() {
        return false;
    }
    public static SCDaemon start() {
        return s_d=new SCDaemon();
    }
    
    private Map<String,ServiceDefinition> m_managedServices = new HashMap<String,ServiceDefinition>();
    @Override
    public void run() {
       // File daemonDir = AppDirectories.conf.getDaemonDirectory();
    }
    
}
