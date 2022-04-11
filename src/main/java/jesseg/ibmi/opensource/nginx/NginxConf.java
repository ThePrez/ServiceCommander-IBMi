package jesseg.ibmi.opensource.nginx;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class NginxConf {

    public static void main(final String[] args) {
        System.out.println("Hello, world!");
        // NginxConfNode root = new NginxConfNode(null);
        // root.addProperty("pid", "nginx.pid");
        // root.addChild(new NginxConfNode("events"));
        // NginxConfNode http = new NginxConfNode("http");
        // http.addProperty("error_log", "logs/error.log warn");
        // http.addProperty("proxy_cache_path", "/tmp/cache keys_zone=cache:10m levels=1:2 inactive=600s max_size=100m");
        // root.addChild(http);
        // NginxConfNode upstream = new NginxConfNode("upstream python_servers");
        // upstream.addProperty("server", "127.0.0.1:3333");
        // upstream.addProperty("server", "127.0.0.1:3334");
        // upstream.addProperty("server", "127.0.0.1:3335");
        // upstream.addProperty("server", "127.0.0.1:3336");
        // upstream.addProperty("server", "127.0.0.1:3337");
        // http.addChild(upstream);
        // NginxConfNode server = new NginxConfNode("server");
        // server.addProperty("listen", "9333 backlog=8096");
        // http.addChild(server);
        // NginxConfNode rootLoc = new NginxConfNode("location /");
        // rootLoc.addProperty("proxy_pass", "http://python_servers");
        // server.addChild(rootLoc);
        // NginxConfNode staticContent = new NginxConfNode("location /tablesorter");
        // staticContent.addProperty("autoindex", "on");
        // staticContent.addProperty("alias", "tablesorter/");
        // server.addChild(staticContent);
        //
        // try (PrintWriter ps = new PrintWriter("C:\\nginx.conf", "UTF-8")) {
        // root.writeData(ps, 0);
        // } catch (Exception e) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }

        NginxConf root;
        try {
            root = new NginxConf(new File("C:\\nginx.in"));
            final LinkedList<String> upstreams = new LinkedList<String>();
            upstreams.add("127.0.0.1:3341");
            upstreams.add("127.0.0.1:3342");
            root.overwrite(new String[] { "http", "upstream python_servers" }, "server", upstreams, true);

            try (PrintWriter ps = new PrintWriter("C:\\nginx.conf", "UTF-8")) {
                root.writeData(ps, 0);
            }
        } catch (final IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    private final NginxConfNode m_root;

    public NginxConf(final File _f) throws IOException {
        m_root = NginxConfNode.open(_f);
    }

    private NginxConfNode getNode(final String[] _path, final boolean _create) {
        NginxConfNode ret = m_root;
        for (final String pathElement : _path) {
            try {
                ret = ret.getChild(pathElement);
            } catch (final NoSuchElementException e) {
                if (!_create) {
                    throw e;
                }
                final NginxConfNode newNode = new NginxConfNode(pathElement);
                ret.addChild(newNode);
                ret = newNode;
            }
        }
        return ret;
    }

    public NginxConfNode getRoot() {
        return m_root;
    }

    public void overwrite(final String[] _path, final String _prop, final List<String> _values, final boolean _create) {
        final NginxConfNode node = getNode(_path, _create);
        node.purgeProperty(_prop);
        if (null == _values) {
            return;
        }
        for (final String value : _values) {
            node.addProperty(_prop, value);
        }
    }

    public void writeData(final PrintWriter _ps, final int _i) {
        m_root.writeData(_ps, _i);
    }

    public void remove(String... _path) {
        try {
            LinkedList<String> path = new LinkedList<String>(Arrays.asList(_path));
            String removalNode = path.removeLast();
            final NginxConfNode node = getNode(path.toArray(new String[0]), false);
            NginxConfNode child = getNode(_path, false);
            node.removeChild(child);
        } catch (NoSuchElementException e) {
            // nothing to remove
        }

    }
}
