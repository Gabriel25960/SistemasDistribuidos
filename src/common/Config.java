package common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Le um arquivo .properties com os enderecos (IP e porta) dos processos.
 *
 * Formato:
 *   node.<id>=<host>:<porta>
 *   outras chaves (timeouts, ordem do anel, etc.)
 */
public final class Config {

    /** Endereco de um processo. */
    public static final class NodeInfo {
        public final int id;
        public final String host;
        public final int port;

        public NodeInfo(int id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return "P" + id + "@" + host + ":" + port;
        }
    }

    private final Properties props = new Properties();

    public static Config load(String path) throws IOException {
        Config c = new Config();
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            c.props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return c;
    }

    public String get(String key) {
        String v = props.getProperty(key);
        if (v == null) {
            throw new IllegalArgumentException("chave ausente na configuracao: " + key);
        }
        return v.trim();
    }

    public String get(String key, String defaultValue) {
        String v = props.getProperty(key);
        return v == null ? defaultValue : v.trim();
    }

    public int getInt(String key, int defaultValue) {
        String v = props.getProperty(key);
        return v == null ? defaultValue : Integer.parseInt(v.trim());
    }

    /** Endereco do processo com o id informado (chave node.<id>). */
    public NodeInfo node(int id) {
        String[] hp = get("node." + id).split(":");
        return new NodeInfo(id, hp[0].trim(), Integer.parseInt(hp[1].trim()));
    }

    /** Lista de inteiros separados por virgula (ex.: ring=3,7,1,8). */
    public List<Integer> intList(String key) {
        List<Integer> out = new ArrayList<>();
        for (String s : get(key).split(",")) {
            out.add(Integer.parseInt(s.trim()));
        }
        return out;
    }
}
