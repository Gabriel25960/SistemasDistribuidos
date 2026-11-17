package anel;

import common.Args;
import common.Config;
import common.Log;
import common.Message;
import common.Net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * No do anel - Eleicao Chang-Roberts.
 *
 * Uso: java -cp out anel.Node --id 3 [--config config/anel.properties]
 *
 * Comandos digitados no terminal do no:  election | status | quit | help
 * (os mesmos comandos podem ser enviados de fora com anel.Control)
 *
 * Mensagens:
 *   ELECTION(id)            circula pelo anel carregando o maior id visto ate agora
 *   ELECTED(leader,origin)  anuncia o coordenador eleito a todos
 *   PING / PONG             verifica se o coordenador esta vivo
 *   CMD(action)             comandos de controle (election, status, quit)
 *
 * Regras do Chang-Roberts ao receber ELECTION(id):
 *   id  > meu id                         -> encaminha sem alterar
 *   id  < meu id e nao participo ainda   -> substitui por meu id e encaminha
 *   id  < meu id e ja participo          -> descarta
 *   id == meu id                         -> a mensagem deu a volta: SOU O COORDENADOR
 *
 * Falhas: se o sucessor nao responde (conexao recusada ou timeout), o no tenta o
 * proximo da lista do anel. Assim o anel se reconfigura sem o processo morto.
 */
public final class Node {

    private final int myId;
    private final Config cfg;
    private final List<Integer> ring;
    private final int timeoutMs;
    private final int heartbeatMs;
    private final int electionTimeoutMs;

    // Estado compartilhado entre as threads (protegido por 'lock')
    private final Object lock = new Object();
    private Integer coordinator = null;   // coordenador conhecido (null = nenhum)
    private boolean participant = false;  // participa de uma eleicao em andamento?
    private long participantSince = 0;

    // Fila unica: processa as mensagens recebidas em ordem, fora da thread do socket,
    // para responder o ACK imediatamente e nao acumular timeouts pelo anel.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "worker");
        t.setDaemon(true);
        return t;
    });

    private Node(int id, Config cfg) {
        this.myId = id;
        this.cfg = cfg;
        this.ring = cfg.intList("ring");
        this.timeoutMs = cfg.getInt("timeout.ms", 1500);
        this.heartbeatMs = cfg.getInt("heartbeat.ms", 2000);
        this.electionTimeoutMs = cfg.getInt("election.timeout.ms", 10000);
        if (!ring.contains(id)) {
            throw new IllegalArgumentException("o id " + id + " nao esta na lista 'ring' da configuracao");
        }
    }

    public static void main(String[] argv) throws Exception {
        Args args = Args.parse(argv);
        int id = args.getInt("id");
        Config cfg = Config.load(args.get("config", "config/anel.properties"));
        new Node(id, cfg).start();
    }

    private void start() throws IOException {
        Log.init("N" + myId);
        Config.NodeInfo me = cfg.node(myId);
        int pos = ring.indexOf(myId);
        int sucessor = ring.get((pos + 1) % ring.size());

        Log.banner("NO P" + myId + " (" + me + ")");
        Log.log("anel (ordem): " + ring + " | meu sucessor: P" + sucessor);
        Log.log("digite 'election', 'status', 'quit' ou 'help'");

        Net.serve(me.port, this::tratar);

        startThread("monitor", this::monitorar);
        startThread("console", this::console);
    }

    private static void startThread(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ recepcao

    private Message tratar(Message req) {
        switch (req.type()) {
            case "PING":
                return new Message("PONG").put("from", myId);

            case "ELECTION": {
                int id = req.getInt("id");
                int from = req.getInt("from");
                worker.submit(() -> seguro(() -> aoReceberEleicao(id, from)));
                return null; // ACK imediato
            }

            case "ELECTED": {
                int leader = req.getInt("leader");
                int origin = req.getInt("origin");
                int from = req.getInt("from");
                worker.submit(() -> seguro(() -> aoReceberEleito(leader, origin, from)));
                return null; // ACK imediato
            }

            case "CMD":
                return comando(req.get("action"));

            default:
                return new Message("ERROR").put("reason", "tipo desconhecido: " + req.type());
        }
    }

    private static void seguro(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Log.log("erro inesperado: " + e);
        }
    }

    // ------------------------------------------------------------ algoritmo CR

    private void aoReceberEleicao(int id, int from) {
        Message saida = null;
        boolean souLider = false;

        synchronized (lock) {
            Log.log("recebi ELECTION(" + id + ") de P" + from);
            if (id > myId) {
                marcarParticipante();
                Log.log("  " + id + " > meu id " + myId + " -> encaminho sem alterar");
                saida = eleicao(id);
            } else if (id < myId) {
                if (!participant) {
                    marcarParticipante();
                    Log.log("  " + id + " < meu id " + myId + " e ainda nao participo -> substituo por " + myId + " e encaminho");
                    saida = eleicao(myId);
                } else {
                    Log.log("  " + id + " < meu id " + myId + " e ja participo da eleicao -> descarto");
                }
            } else {
                coordinator = myId;
                participant = false;
                souLider = true;
                Log.log("  meu proprio id voltou: a mensagem deu a volta no anel");
            }
        }

        if (saida != null && encaminhar(saida) < 0) {
            tornarLiderSozinho();
        }
        if (souLider) {
            Log.banner("EU (P" + myId + ") SOU O NOVO COORDENADOR");
            Log.log("anunciando o resultado a todos com ELECTED");
            encaminhar(new Message("ELECTED").put("leader", myId).put("origin", myId).put("from", myId));
        }
    }

    private void aoReceberEleito(int leader, int origin, int from) {
        boolean propagar;
        synchronized (lock) {
            boolean jaInformado = coordinator != null && coordinator == leader && !participant;
            if (jaInformado) {
                propagar = false;
                if (origin == myId) {
                    Log.log("ELECTED(" + leader + ") completou a volta no anel");
                } else {
                    Log.log("ELECTED(" + leader + ") de P" + from + ": ja conheco esse coordenador -> fim da circulacao");
                }
            } else {
                coordinator = leader;
                participant = false;
                propagar = true;
                Log.banner("NOVO COORDENADOR: P" + leader + " (informado por P" + from + ")");
            }
        }
        if (propagar) {
            encaminhar(new Message("ELECTED").put("leader", leader).put("origin", origin).put("from", myId));
        }
    }

    private void iniciarEleicao(String motivo) {
        synchronized (lock) {
            if (participant) {
                Log.log("ja participo de uma eleicao -> nao inicio outra");
                return;
            }
            marcarParticipante();
        }
        Log.banner("ELEICAO INICIADA POR P" + myId + " (" + motivo + ")");
        worker.submit(() -> seguro(() -> {
            if (encaminhar(eleicao(myId)) < 0) {
                tornarLiderSozinho();
            }
        }));
    }

    private void tornarLiderSozinho() {
        synchronized (lock) {
            coordinator = myId;
            participant = false;
        }
        Log.banner("NENHUM OUTRO PROCESSO ATIVO: P" + myId + " E O COORDENADOR");
    }

    private void marcarParticipante() { // chamar com 'lock' em maos
        participant = true;
        participantSince = System.currentTimeMillis();
    }

    private Message eleicao(int id) {
        return new Message("ELECTION").put("id", id).put("from", myId);
    }

    // ------------------------------------------------- circulacao e reconfiguracao

    /**
     * Envia a mensagem ao proximo processo ATIVO do anel.
     * Se o sucessor estiver indisponivel (conexao recusada ou timeout), tenta o seguinte.
     * Retorna o id de quem recebeu, ou -1 se nenhum outro processo respondeu.
     */
    private int encaminhar(Message msg) {
        int pos = ring.indexOf(myId);
        for (int k = 1; k < ring.size(); k++) {
            int destId = ring.get((pos + k) % ring.size());
            Config.NodeInfo dest = cfg.node(destId);
            Log.log("enviando " + msg + " -> P" + destId);
            try {
                Net.request(dest, msg, timeoutMs);
                return destId;
            } catch (IOException | RuntimeException e) {
                Log.log("P" + destId + " INDISPONIVEL (" + e.getMessage()
                        + ") -> anel reconfigurado: pulando para o proximo");
            }
        }
        Log.log("nenhum outro processo do anel respondeu");
        return -1;
    }

    // --------------------------------------------------------- deteccao de falhas

    private void monitorar() {
        int falhas = 0;
        Integer monitorado = null;
        while (true) {
            dormir(heartbeatMs);

            Integer alvo;
            boolean travada;
            synchronized (lock) {
                alvo = coordinator;
                travada = participant && System.currentTimeMillis() - participantSince > electionTimeoutMs;
                if (travada) {
                    participant = false;
                }
            }

            if (travada) {
                Log.log("eleicao sem resultado ha mais de " + electionTimeoutMs
                        + " ms (mensagem pode ter se perdido) -> reiniciando");
                iniciarEleicao("timeout da eleicao");
                continue;
            }
            if (alvo == null || alvo == myId) {
                falhas = 0;
                monitorado = alvo;
                continue;
            }
            if (!alvo.equals(monitorado)) {
                monitorado = alvo;
                falhas = 0;
            }

            if (ping(alvo)) {
                falhas = 0;
                continue;
            }
            falhas++;
            Log.log("coordenador P" + alvo + " nao respondeu ao PING (" + falhas + "/2)");
            if (falhas >= 2) {
                falhas = 0;
                Log.log("COORDENADOR P" + alvo + " CONSIDERADO FALHO (timeout / falha TCP)");
                synchronized (lock) {
                    if (coordinator != null && coordinator.equals(alvo)) {
                        coordinator = null;
                    }
                }
                // pequena espera aleatoria para reduzir eleicoes simultaneas
                dormir(ThreadLocalRandom.current().nextInt(500));
                boolean jaTemRumo;
                synchronized (lock) {
                    jaTemRumo = coordinator != null || participant;
                }
                if (jaTemRumo) {
                    Log.log("outra eleicao ja esta em andamento (ou ja ha novo coordenador) -> nao inicio a minha");
                } else {
                    iniciarEleicao("falha do coordenador P" + alvo);
                }
            }
        }
    }

    private boolean ping(int id) {
        try {
            Net.request(cfg.node(id), new Message("PING").put("from", myId), timeoutMs);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ comandos / console

    private Message comando(String action) {
        switch (action) {
            case "election":
                Log.log("comando recebido: election");
                iniciarEleicao("comando manual");
                return new Message("ACK");
            case "status": {
                Integer c;
                boolean p;
                synchronized (lock) {
                    c = coordinator;
                    p = participant;
                }
                return new Message("STATUS").put("id", myId)
                        .put("coordinator", c == null ? "nenhum" : "P" + c)
                        .put("participant", p ? "sim" : "nao");
            }
            case "quit":
                Log.log("comando recebido: quit");
                startThread("quit", () -> {
                    dormir(200); // da tempo de responder o ACK
                    morrer();
                });
                return new Message("ACK");
            default:
                return new Message("ERROR").put("reason", "comando desconhecido: " + action);
        }
    }

    private void console() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = in.readLine()) != null) {
                switch (line.trim().toLowerCase()) {
                    case "election":
                        iniciarEleicao("comando manual");
                        break;
                    case "status": {
                        synchronized (lock) {
                            Log.log("coordenador conhecido: " + (coordinator == null ? "nenhum" : "P" + coordinator)
                                    + " | participando de eleicao: " + (participant ? "sim" : "nao"));
                        }
                        break;
                    }
                    case "quit":
                        morrer();
                        break;
                    case "help":
                        Log.log("comandos: election (inicia eleicao) | status | quit (encerra este processo)");
                        break;
                    default:
                        if (!line.trim().isEmpty()) {
                            Log.log("comando desconhecido (use 'help')");
                        }
                }
            }
        } catch (IOException e) {
            // console indisponivel: o no continua funcionando apenas pela rede
        }
    }

    /** Simula a falha do processo: termina de imediato, sem avisar ninguem. */
    private void morrer() {
        Log.banner("P" + myId + " ENCERRADO (simulando falha)");
        Runtime.getRuntime().halt(0);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
