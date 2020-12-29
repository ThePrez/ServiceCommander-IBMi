package jesseg.ibmi.opensource.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import jesseg.ibmi.opensource.AppDirectories;

/**
 * A simple class to encapsulate a helper script used for submitting jobs to batch. This script file is actually
 * written out to disk and then executed in order to perform a SBMJOB as needed. 
 * 
 * @author Jesse Gorzinski
 */
public class SbmJobScript {
//@formatter:off
private static String s_qp2term2 = "#!/QOpenSys/usr/bin/sh\n" + 
			"if [[ \"\" = \"$SBMJOB_JOBNAME\" ]];" +
			"then\n"+
			"    SBMJOB_JOBNAME=$(echo \"$1\" | sed 's/[^a-zA-Z0-9]//g' | cut -c-10) \n" + 
			"fi\n" +
			"SBMJOB_OPTS=\"JOB($SBMJOB_JOBNAME) $SBMJOB_OPTS\"\n" + 
			"QIBM_USE_DESCRIPTOR_STDIO=N\n" + 
			"export QIBM_USE_DESCRIPTOR_STDIO\n" + 
			"exec /QOpenSys/usr/bin/system -kpiveO \"SBMJOB CMD(CALL PGM(QP2SHELL2) PARM('/QOpenSys/usr/bin/sh' '-c' 'cd $(pwd) && env && exec $*')) CPYENVVAR(*YES) PRTDEV(*USRPRF) ALWMLTTHD(*YES) $SBMJOB_OPTS\"";
//@formatter:on
    public static File getQp2() throws IOException {
        final File scriptsDir = AppDirectories.conf.getScriptsDirectory();
        if (!scriptsDir.isDirectory()) {
            scriptsDir.mkdirs();
        }
        final File script = new File(scriptsDir.getAbsolutePath() + "/batch_qp2.sh");
        if (script.exists() && script.length() > 20) {
            return script;
        }
        try (FileOutputStream out = new FileOutputStream(script)) {
            out.write(s_qp2term2.getBytes("UTF-8"));
            out.flush();
            out.close();
        }
        return script;
    }
}
