package jesseg.ibmi.opensource.utils;

public class StringUtils {

    public static boolean isEmpty(String stopCmd) {
        return (null == stopCmd) || (stopCmd.trim().isEmpty());
    }
}
