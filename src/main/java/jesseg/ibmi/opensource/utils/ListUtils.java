package jesseg.ibmi.opensource.utils;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import jesseg.ibmi.opensource.ServiceDefinition.CheckAlive;

public class ListUtils {
    public static <T> List<T> deduplicate(final List<T> _in) {
        final HashSet<T> s = new HashSet<T>();
        s.addAll(_in);
        final LinkedList<T> ret = new LinkedList<T>();
        ret.addAll(s);
        return ret;
    }

    public static <T> List<T> intersection(final List<T> _a, final List<T> _b) {
        final LinkedList<T> ret = new LinkedList<T>();
        ret.addAll(_a);
        ret.retainAll(_b);
        return ret;
    }

    public static <T> String toString(final List<CheckAlive> list, final String _separator) {
        String ret = "";
        synchronized (list) {
            for (int i = 0; i < list.size(); ++i) {
                ret += ("" + list.get(i));
                if (list.size() > 1 + i) {
                    ret += _separator;
                }
            }
        }
        return ret;
    }

    public static <T> List<T> union(final List<T> _a, final List<T> _b) {
        final LinkedList<T> ret = new LinkedList<T>();
        ret.addAll(_b);
        ret.addAll(_a);
        return deduplicate(ret);
    }
}
