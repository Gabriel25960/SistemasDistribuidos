package common;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Log com horario e identificacao do processo, gravado no console e em logs/<nome>.log.
 * Essas saidas sao as "evidencias de execucao" exigidas pelo enunciado.
 */
public final class Log {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final PrintStream CONSOLE =
            new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);

    private static String name = "main";
    private static PrintWriter file;

    private Log() {
    }

    /** Define o nome do processo (ex.: "P3" ou "coordenador") e abre logs/<nome>.log. */
    public static synchronized void init(String procName) {
        name = procName;
        try {
            Path dir = Paths.get("logs");
            Files.createDirectories(dir);
            file = new PrintWriter(Files.newBufferedWriter(dir.resolve(procName + ".log"), StandardCharsets.UTF_8));
        } catch (IOException e) {
            CONSOLE.println("[aviso] nao foi possivel criar o arquivo de log: " + e.getMessage());
        }
    }

    public static synchronized void log(String text) {
        String line = "[" + LocalTime.now().format(FMT) + "] [" + name + "] " + text;
        CONSOLE.println(line);
        if (file != null) {
            file.println(line);
            file.flush();
        }
    }

    /** Linha destacada para separar etapas na evidencia. */
    public static void banner(String text) {
        log("==================== " + text + " ====================");
    }
}
