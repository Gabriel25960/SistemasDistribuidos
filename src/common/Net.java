package common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Camada de comunicacao TCP usada por TODOS os algoritmos.
 *
 * Modelo: requisicao e resposta em conexao curta.
 *   - request(): abre uma conexao, envia uma linha, le a linha de resposta (ACK ou dado).
 *   - serve():   aceita conexoes, le uma linha, chama o handler e devolve a resposta.
 *
 * Qualquer falha (conexao recusada, timeout, conexao fechada) vira IOException.
 * E essa excecao que permite detectar processos indisponiveis (falha de TCP / timeout).
 */
public final class Net {

    /** Trata uma mensagem recebida. O retorno e a resposta enviada ao remetente (null = ACK). */
    public interface Handler {
        Message handle(Message request);
    }

    private Net() {
    }

    /** Sobe um servidor TCP em todas as interfaces da maquina, na porta indicada. */
    public static ServerSocket serve(int port, Handler handler) throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));

        Thread acceptor = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    Thread t = new Thread(() -> handleClient(client, handler));
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!server.isClosed()) {
                        Log.log("erro ao aceitar conexao: " + e.getMessage());
                    }
                }
            }
        }, "acceptor-" + port);
        // Nao-daemon: mantem o processo vivo enquanto o servidor estiver ativo.
        acceptor.start();

        Log.log("servidor TCP escutando na porta " + port);
        return server;
    }

    private static void handleClient(Socket client, Handler handler) {
        try (Socket s = client) {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

            String line = in.readLine();
            if (line == null) {
                return;
            }
            Message response = handler.handle(Message.parse(line));
            if (response == null) {
                response = new Message("ACK");
            }
            out.write(response.serialize());
            out.write('\n');
            out.flush();
        } catch (Exception e) {
            Log.log("erro ao tratar conexao: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem e aguarda a resposta.
     * Lanca IOException se nao conectar, estourar o timeout ou a conexao cair.
     */
    public static Message request(String host, int port, Message msg, int timeoutMs) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));

            out.write(msg.serialize());
            out.write('\n');
            out.flush();

            String line = in.readLine();
            if (line == null) {
                throw new IOException("conexao encerrada sem resposta");
            }
            return Message.parse(line);
        }
    }

    public static Message request(Config.NodeInfo node, Message msg, int timeoutMs) throws IOException {
        return request(node.host, node.port, msg, timeoutMs);
    }
}
