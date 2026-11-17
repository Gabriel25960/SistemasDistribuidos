package berkeley;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Relogio simulado de um processo.
 *
 * Valor lido = relogio real da maquina + deslocamento (offset).
 * Cada processo comeca com um offset diferente, entao cada um "acredita" estar
 * em um horario diferente, sem mexer no relogio do sistema operacional.
 * O algoritmo de Berkeley corrige o offset, nunca o relogio real.
 */
public final class SimClock {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private long offsetMs;

    public SimClock(long initialOffsetMs) {
        this.offsetMs = initialOffsetMs;
    }

    /** Horario atual segundo este relogio, em milissegundos desde 1970. */
    public synchronized long now() {
        return System.currentTimeMillis() + offsetMs;
    }

    /** Aplica um ajuste (positivo adianta, negativo atrasa). */
    public synchronized void adjust(long deltaMs) {
        offsetMs += deltaMs;
    }

    public static String format(long ms) {
        return FMT.format(Instant.ofEpochMilli(ms));
    }
}
