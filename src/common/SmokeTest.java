package common;

/**
 * Teste rapido da camada de rede (nao faz parte dos algoritmos).
 *
 * Terminal 1:  java -cp out common.SmokeTest --mode server --port 6000
 * Terminal 2:  java -cp out common.SmokeTest --mode client --host 127.0.0.1 --port 6000
 */
public final class SmokeTest {

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        String mode = args.get("mode", "server");
        int port = args.getInt("port", 6000);

        if (mode.equals("server")) {
            Log.init("smoke-server");
            Net.serve(port, req -> {
                Log.log("recebido: " + req);
                return new Message("PONG").put("echo", req.type());
            });
        } else {
            Log.init("smoke-client");
            String host = args.get("host", "127.0.0.1");
            Message resp = Net.request(host, port, new Message("PING").put("from", "cliente"), 2000);
            Log.log("resposta: " + resp);
        }
    }
}
