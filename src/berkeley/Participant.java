package berkeley;

import common.Args;
import common.Config;
import common.Log;
import common.Message;
import common.Net;

/**
 * Participante do algoritmo de Berkeley.
 *
 * Uso: java -cp out berkeley.Participant --id 1 --offset 30000 [--config config/berkeley.properties]
 *
 * --offset: diferenca inicial (em ms) do relogio deste processo.
 *
 * Atende a duas mensagens do coordenador:
 *   GET_TIME        -> responde com o valor do seu relogio
 *   ADJUST(delta)   -> aplica o ajuste no relogio e responde ACK
 */
public final class Participant {

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        int id = args.getInt("id");
        Config cfg = Config.load(args.get("config", "config/berkeley.properties"));
        long offset = args.getLong("offset", 0);

        Config.NodeInfo me = cfg.node(id);
        Log.init("P" + id);
        SimClock clock = new SimClock(offset);

        Log.banner("PARTICIPANTE P" + id + " (" + me + ")");
        Log.log("relogio inicial: " + SimClock.format(clock.now())
                + " (deslocamento inicial de " + offset + " ms)");

        Net.serve(me.port, req -> {
            if (req.is("GET_TIME")) {
                long t = clock.now();
                Log.log("coordenador pediu meu relogio -> respondi " + SimClock.format(t));
                return new Message("TIME").put("time", t);
            }
            if (req.is("ADJUST")) {
                long delta = req.getLong("delta");
                long antes = clock.now();
                clock.adjust(delta);
                long depois = clock.now();
                Log.log("recebi ajuste de " + delta + " ms -> relogio "
                        + SimClock.format(antes) + " passou a " + SimClock.format(depois));
                return new Message("ACK").put("time", depois);
            }
            return new Message("ERROR").put("reason", "tipo desconhecido: " + req.type());
        });
    }
}
