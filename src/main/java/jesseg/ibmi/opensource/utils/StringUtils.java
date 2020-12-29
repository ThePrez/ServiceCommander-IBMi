package jesseg.ibmi.opensource.utils;

/**
 * Simple string utilities. Not much here.
 * 
 * @author Jesse Gorzinski
 *
 */
public class StringUtils {

    public static boolean isEmpty(String _str) {
        return (null == _str) || (_str.trim().isEmpty());
    }
}
