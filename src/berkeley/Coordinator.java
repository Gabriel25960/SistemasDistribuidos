package berkeley;

import common.Args;
import common.Config;
import common.Log;
import common.Message;
import common.Net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coordenador do algoritmo de Berkeley.
 *
 * Uso: java -cp out berkeley.Coordinator [--offset 5000] [--config config/berkeley.properties]
 *
 * Passos:
 *   1. mostra os relogios iniciais
 *   2. consulta o relogio de cada participante (GET_TIME) e mede o RTT
 *   3. calcula a media das diferencas (incluindo o proprio coordenador) e o ajuste de cada um
 *   4. envia o ajuste (ADJUST) a cada participante e aplica o seu proprio
 *   5. consulta de novo os relogios e mostra a tabela antes/depois
 *
 * Ideia central: o coordenador envia o AJUSTE (delta), nao um horario absoluto.
 * Assim o atraso da rede nao desloca os relogios.
 */
public final class Coordinator {

    private static final int TENTATIVAS = 3;

    /** Leitura do relogio de um participante. */
    private static final class Leitura {
        boolean ok;
        long relogio;   // valor informado pelo participante
        double rttMs;   // tempo de ida e volta da consulta
        double dif;     // diferenca estimada para o relogio do coordenador (ms)
    }

    private static final class Participante {
        final int id;
        Leitura antes = new Leitura();
        Leitura depois = new Leitura();
        long ajuste;
        boolean ajustado;

        Participante(int id) {
            this.id = id;
        }
    }

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        Config cfg = Config.load(args.get("config", "config/berkeley.properties"));
        int timeout = cfg.getInt("timeout.ms", 2000);
        SimClock clock = new SimClock(args.getLong("offset", 0));
        Log.init("coordenador");

        List<Participante> ps = new ArrayList<>();
        for (int id : cfg.intList("participants")) {
            ps.add(new Participante(id));
        }

        // ---------------------------------------------------------------- PASSO 1
        Log.banner("PASSO 1 - RELOGIOS INICIAIS");
        Log.log("relogio do coordenador: " + SimClock.format(clock.now()));
        Log.log("participantes configurados: " + cfg.get("participants"));

        // ---------------------------------------------------------------- PASSO 2
        Log.banner("PASSO 2 - COORDENADOR CONSULTA OS RELOGIOS");
        for (Participante p : ps) {
            Config.NodeInfo no = cfg.node(p.id);
            Log.log("enviando GET_TIME para " + no);
            p.antes = ler(no, clock, timeout);
            if (p.antes.ok) {
                Log.log("P" + p.id + " respondeu: relogio=" + SimClock.format(p.antes.relogio)
                        + " | RTT=" + f(p.antes.rttMs) + " ms"
                        + " | diferenca estimada para o coordenador=" + sinal(p.antes.dif) + " ms");
            } else {
                Log.log("P" + p.id + " nao respondeu -> excluido desta rodada");
            }
        }

        // ---------------------------------------------------------------- PASSO 3
        Log.banner("PASSO 3 - CALCULO DOS AJUSTES");
        double soma = 0;
        int n = 1; // o coordenador participa da media com diferenca 0 para si mesmo
        for (Participante p : ps) {
            if (p.antes.ok) {
                soma += p.antes.dif;
                n++;
            }
        }
        double media = soma / n;
        Log.log("media das diferencas (coordenador + " + (n - 1) + " participantes) = "
                + sinal(soma) + " / " + n + " = " + sinal(media) + " ms");
        long ajusteCoord = Math.round(media);
        Log.log("ajuste do coordenador = media - 0 = " + ajusteCoord + " ms");
        for (Participante p : ps) {
            if (p.antes.ok) {
                p.ajuste = Math.round(media - p.antes.dif);
                Log.log("ajuste de P" + p.id + " = media - diferenca = " + sinal(media) + " - ("
                        + sinal(p.antes.dif) + ") = " + p.ajuste + " ms");
            }
        }

        // ---------------------------------------------------------------- PASSO 4
        Log.banner("PASSO 4 - ENVIO E APLICACAO DOS AJUSTES");
        for (Participante p : ps) {
            if (!p.antes.ok) {
                continue;
            }
            Config.NodeInfo no = cfg.node(p.id);
            Log.log("enviando ADJUST(" + p.ajuste + " ms) para " + no);
            try {
                Message r = Net.request(no, new Message("ADJUST").put("delta", p.ajuste), timeout);
                p.ajustado = r.is("ACK");
                Log.log("P" + p.id + " confirmou (ACK); relogio dele agora: " + SimClock.format(r.getLong("time")));
            } catch (IOException | RuntimeException e) {
                Log.log("falha ao enviar ajuste para P" + p.id + ": " + e.getMessage());
            }
        }
        long coordAntes = clock.now();
        clock.adjust(ajusteCoord);
        long coordDepois = clock.now();
        Log.log("coordenador aplicou o proprio ajuste: " + SimClock.format(coordAntes)
                + " -> " + SimClock.format(coordDepois));

        // ---------------------------------------------------------------- PASSO 5
        Log.banner("PASSO 5 - VERIFICACAO E RESULTADO FINAL");
        for (Participante p : ps) {
            if (p.ajustado) {
                p.depois = ler(cfg.node(p.id), clock, timeout);
            }
        }
        imprimirTabela(ps, coordAntes, coordDepois, ajusteCoord);
    }

    /** Consulta o relogio de um participante, compensando metade do RTT. */
    private static Leitura ler(Config.NodeInfo no, SimClock clock, int timeout) {
        Leitura l = new Leitura();
        for (int t = 1; t <= TENTATIVAS; t++) {
            long c0 = clock.now();
            long n0 = System.nanoTime();
            try {
                Message r = Net.request(no, new Message("GET_TIME"), timeout);
                double rtt = (System.nanoTime() - n0) / 1e6;
                l.relogio = r.getLong("time");
                l.rttMs = rtt;
                // relogio do coordenador no meio da viagem x relogio informado pelo participante
                l.dif = l.relogio - (c0 + rtt / 2.0);
                l.ok = true;
                return l;
            } catch (IOException | RuntimeException e) {
                Log.log("falha ao consultar " + no + " (tentativa " + t + "/" + TENTATIVAS + "): " + e.getMessage());
                if (t < TENTATIVAS) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return l;
                    }
                }
            }
        }
        return l;
    }

    private static void imprimirTabela(List<Participante> ps, long coordAntes, long coordDepois, long ajusteCoord) {
        String fmt = "%-13s %-14s %-12s %-12s %-14s %-12s";
        Log.log(String.format(fmt, "Processo", "Relogio antes", "Dif. antes", "Ajuste", "Relogio depois", "Dif. depois"));
        Log.log(String.format(fmt, "Coordenador", SimClock.format(coordAntes), "+0.0 ms",
                ajusteCoord + " ms", SimClock.format(coordDepois), "+0.0 ms"));

        double minAntes = 0, maxAntes = 0, minDepois = 0, maxDepois = 0;
        for (Participante p : ps) {
            String nome = "P" + p.id;
            if (!p.antes.ok) {
                Log.log(String.format(fmt, nome, "sem resposta", "-", "-", "-", "-"));
                continue;
            }
            minAntes = Math.min(minAntes, p.antes.dif);
            maxAntes = Math.max(maxAntes, p.antes.dif);
            if (!p.ajustado || !p.depois.ok) {
                Log.log(String.format(fmt, nome, SimClock.format(p.antes.relogio), sinal(p.antes.dif) + " ms",
                        "falhou", "-", "-"));
                continue;
            }
            minDepois = Math.min(minDepois, p.depois.dif);
            maxDepois = Math.max(maxDepois, p.depois.dif);
            Log.log(String.format(fmt, nome, SimClock.format(p.antes.relogio), sinal(p.antes.dif) + " ms",
                    p.ajuste + " ms", SimClock.format(p.depois.relogio), sinal(p.depois.dif) + " ms"));
        }
        Log.log("diferenca entre o relogio mais adiantado e o mais atrasado: ANTES = "
                + f(maxAntes - minAntes) + " ms | DEPOIS = " + f(maxDepois - minDepois) + " ms");
        Log.log("(pequenas diferencas apos o ajuste, de poucos ms, vem do atraso/precisao da rede)");
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String sinal(double v) {
        return String.format(Locale.ROOT, "%+.1f", v);
    }
}
