package common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mensagem trocada entre processos. Formato em UMA linha de texto:
 *
 *     TIPO;chave1=valor1;chave2=valor2
 *
 * Tipo, chaves e valores usam URL-encoding para nao conflitar com ';' e '='.
 * O delimitador de mensagem no TCP e a quebra de linha ('\n').
 */
public final class Message {

    private final String type;
    private final Map<String, String> fields = new LinkedHashMap<>();

    public Message(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }

    public Message put(String key, String value) {
        fields.put(key, value);
        return this;
    }

    public Message put(String key, long value) {
        return put(key, Long.toString(value));
    }

    public String get(String key) {
        return fields.get(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(fields.get(key));
    }

    public long getLong(String key) {
        return Long.parseLong(fields.get(key));
    }

    public boolean is(String expectedType) {
        return type.equals(expectedType);
    }

    /** Converte a mensagem para a linha enviada pelo socket (sem o '\n'). */
    public String serialize() {
        StringBuilder sb = new StringBuilder(enc(type));
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append(';').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    /** Reconstroi a mensagem a partir de uma linha recebida. */
    public static Message parse(String line) {
        String[] parts = line.trim().split(";");
        Message m = new Message(dec(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                m.fields.put(dec(parts[i].substring(0, eq)), dec(parts[i].substring(eq + 1)));
            }
        }
        return m;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type);
        if (!fields.isEmpty()) {
            sb.append(fields.toString());
        }
        return sb.toString();
    }
}
