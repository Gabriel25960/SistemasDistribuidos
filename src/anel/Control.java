package anel;

import common.Args;
import common.Config;
import common.Message;
import common.Net;

/**
 * Envia um comando a um no do anel (usado pelos scripts de demonstracao).
 *
 * Uso: java -cp out anel.Control --to 3 --cmd election|status|quit [--config config/anel.properties]
 */
public final class Control {

    public static void main(String[] argv) {
        Args args = Args.parse(argv);
        int to = args.getInt("to");
        String cmd = args.get("cmd");
        try {
            Config cfg = Config.load(args.get("config", "config/anel.properties"));
            Message r = Net.request(cfg.node(to), new Message("CMD").put("action", cmd), 3000);
            if (r.is("STATUS")) {
                System.out.println("P" + to + " -> coordenador=" + r.get("coordinator")
                        + " | em eleicao=" + r.get("participant"));
            } else {
                System.out.println("P" + to + " <- " + r);
            }
        } catch (Exception e) {
            System.out.println("P" + to + " -> sem resposta (" + e.getMessage() + ")");
            System.exit(1);
        }
    }
}
