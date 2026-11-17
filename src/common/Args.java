package common;

import java.util.HashMap;
import java.util.Map;

/**
 * Parser simples de argumentos de linha de comando no formato:
 *   --chave valor     ou     --chave=valor
 * Aceita valores negativos (ex.: --offset -45000).
 */
public final class Args {

    private final Map<String, String> map = new HashMap<>();

    public static Args parse(String[] argv) {
        Args a = new Args();
        for (int i = 0; i < argv.length; i++) {
            String s = argv[i];
            if (!s.startsWith("--")) {
                continue;
            }
            String key = s.substring(2);
            int eq = key.indexOf('=');
            if (eq >= 0) {
                a.map.put(key.substring(0, eq), key.substring(eq + 1));
            } else if (i + 1 < argv.length && !argv[i + 1].startsWith("--")) {
                a.map.put(key, argv[++i]);
            } else {
                a.map.put(key, "true");
            }
        }
        return a;
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public String get(String key) {
        String v = map.get(key);
        if (v == null) {
            throw new IllegalArgumentException("argumento obrigatorio ausente: --" + key);
        }
        return v;
    }

    public String get(String key, String defaultValue) {
        return map.getOrDefault(key, defaultValue);
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public int getInt(String key, int defaultValue) {
        return map.containsKey(key) ? Integer.parseInt(map.get(key)) : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        return map.containsKey(key) ? Long.parseLong(map.get(key)) : defaultValue;
    }
}
