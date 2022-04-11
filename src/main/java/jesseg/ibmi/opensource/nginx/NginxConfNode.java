package jesseg.ibmi.opensource.nginx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Stack;

import com.github.theprez.jcmdutils.StringUtils;

public class NginxConfNode {
    private static final int INDENTATION_LEN = 2;

    public static NginxConfNode open(final File _file) throws IOException {
        final NginxConfNode root = new NginxConfNode(null, true);
        if (null == _file) {
            return root;
        }

        NginxConfNode currentNode = root;
        final Stack<NginxConfNode> stack = new Stack<NginxConfNode>();
        try (InputStreamReader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(_file), 1024 * 128), "UTF-8")) {
            String curStr = "";
            int curChar = -1;
            while (-1 != (curChar = in.read())) {
                final char c = (char) curChar;
                if ('{' == c) {
                    final NginxConfNode n = new NginxConfNode(curStr.trim());
                    stack.push(currentNode);
                    currentNode.addChild(n);
                    currentNode = n;
                    curStr = "";
                } else if (';' == c) {
                    curStr = curStr.trim();
                    final String property = curStr.replaceAll("\\s.*", "");
                    final String value = curStr.replaceFirst("^[^\\s]*\\s+", "");
                    currentNode.addProperty(property, value);
                    curStr = "";
                } else if ('}' == c) {
                    currentNode = stack.pop();
                    curStr = "";
                } else {
                    curStr += c;
                }
            }
        } catch (final Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException(e);
        }

        return root;
    }

    private final LinkedList<NginxConfNode> m_childNodes = new LinkedList<NginxConfNode>();
    private final boolean m_isRoot;
    private final String m_name;

    private final LinkedList<Entry<String, String>> m_properties = new LinkedList<Entry<String, String>>();

    public NginxConfNode(final String _name) {
        if (null == _name) {
            throw new NullPointerException();
        }
        m_name = _name;
        m_isRoot = false;
    }

    private NginxConfNode(final String _name, final boolean _isRoot) {
        m_isRoot = _isRoot;
        if (_isRoot) {
            m_name = null;
        } else {
            if (null == _name) {
                throw new NullPointerException();
            }
            m_name = _name;
        }
    }

    NginxConfNode addChild(final NginxConfNode _child) {
        m_childNodes.add(_child);
        return this;
    }

    public NginxConfNode addProperty(final String _prop, final String _val) {
        final HashMap<String, String> tmp = new HashMap<String, String>();
        tmp.put(_prop, _val);
        for (final Entry<String, String> l : tmp.entrySet()) {
            m_properties.add(l);
        }
        return this;
    }

    public NginxConfNode getChild(final String _childName) {
        for (final NginxConfNode e : m_childNodes) {
            if (_childName.equals(e.m_name)) {
                return e;
            }
        }
        throw new NoSuchElementException();
    }

    public List<NginxConfNode> getChildren(final String _regex) {
        final LinkedList<NginxConfNode> ret = new LinkedList<NginxConfNode>();
        for (final NginxConfNode e : m_childNodes) {
            if (null != e.m_name && e.m_name.matches(_regex)) {
                ret.add(e);
            }
        }
        return ret;
    }

    public List<String> getPropertyValues(final String _prop) {
        final LinkedList<String> ret = new LinkedList<String>();
        for (final Entry<String, String> e : m_properties) {
            if (_prop.equalsIgnoreCase(e.getKey())) {
                ret.add(e.getValue());
            }
        }
        return ret;
    }

    private boolean isRoot() {
        return null == m_name;
    }

    public NginxConfNode purgeProperty(final String _prop) {
        final LinkedList<Entry<String, String>> removals = new LinkedList<Entry<String, String>>();
        for (final Entry<String, String> e : m_properties) {
            if (e.getKey().equalsIgnoreCase(_prop)) {
                removals.add(e);
            }
        }
        m_properties.removeAll(removals);
        return this;
    }

    public void removeChild(final NginxConfNode _child) {
        final boolean hit = m_childNodes.remove(_child);
    }

    void writeData(final PrintWriter _writer, final int _indent) {
        final String indentStr = StringUtils.spacePad("", _indent * INDENTATION_LEN);
        final int childIndent = isRoot() ? 0 : 1 + _indent;
        final String childIndentStr = isRoot() ? "" : StringUtils.spacePad("", childIndent * INDENTATION_LEN);
        if (m_properties.isEmpty() && m_childNodes.isEmpty()) {
            _writer.println(indentStr + m_name + " {}");
            return;
        }
        if (!isRoot()) {
            _writer.println(indentStr + m_name + " {");
        }
        try {
            for (final Entry<String, String> p : m_properties) {
                _writer.println(childIndentStr + p.getKey() + " " + p.getValue() + ";");
            }
            for (final NginxConfNode child : m_childNodes) {
                child.writeData(_writer, childIndent);
            }

        } finally {
            if (null != m_name) {
                _writer.println(indentStr + "}");
            }
        }
    }
}
