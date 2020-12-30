package jesseg.ibmi.opensource.utils;

/**
 * Simple string utilities. Not much here.
 * 
 * @author Jesse Gorzinski
 *
 */
public class StringUtils {

    private static final String LOTSA_SPACES = "                                             ";

    public static boolean isEmpty(final String _str) {
        return (null == _str) || (_str.trim().isEmpty());
    }

    public static String spacePad(final String _str, final int _len) {
        if(0 == _len) {
            return "";
        }
        String ret = _str + LOTSA_SPACES;
        while(ret.length() < _len) {
            ret += LOTSA_SPACES;
        }
        return ret.substring(0, _len);
    }
}
