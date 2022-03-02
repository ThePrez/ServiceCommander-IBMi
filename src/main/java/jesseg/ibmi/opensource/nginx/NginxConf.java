package jesseg.ibmi.opensource.nginx;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class NginxConf {

    public static void main(String[] args) {
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
            LinkedList<String> upstreams = new LinkedList<String>();
            upstreams.add("127.0.0.1:3341");
            upstreams.add("127.0.0.1:3342");
            root.overwrite(new String[] { "http", "upstream python_servers" }, "server", upstreams, true);

            try (PrintWriter ps = new PrintWriter("C:\\nginx.conf", "UTF-8")) {
                root.writeData(ps, 0);
            }
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    private NginxConfNode m_root;

    public NginxConf(File _f) throws IOException {
        m_root = NginxConfNode.open(_f);
    }

    private NginxConfNode getNode(String[] _path, boolean _create) {
        NginxConfNode ret = m_root;
        for (String pathElement : _path) {
            try {
                ret = ret.getChild(pathElement);
            } catch (NoSuchElementException e) {
                if (!_create) {
                    throw e;
                }
                NginxConfNode newNode = new NginxConfNode(pathElement);
                ret.addChild(newNode);
                ret = newNode;
            }
        }
        return ret;
    }

    public NginxConfNode getRoot() {
        return m_root;
    }

    public void overwrite(String[] _path, String _prop, List<String> _values, boolean _create) {
        NginxConfNode node = getNode(_path, _create);
        node.purgeProperty(_prop);
        for (String value : _values) {
            node.addProperty(_prop, value);
        }
    }

    public void writeData(PrintWriter _ps, int _i) {
        m_root.writeData(_ps, _i);
    }
}
