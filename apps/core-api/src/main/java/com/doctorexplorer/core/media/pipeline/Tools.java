package com.doctorexplorer.core.media.pipeline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Executa ferramentas externas (Tesseract, libheif) com tempo-limite. Erro ou ausência = falha fechada. */
public final class Tools {

    private Tools() {}

    public static byte[] run(List<String> cmd, byte[] stdin, int timeoutSeconds) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            Thread writer = Thread.ofVirtual().start(() -> {
                try (OutputStream os = p.getOutputStream()) {
                    if (stdin != null) os.write(stdin);
                } catch (IOException ignored) {
                    // o processo pode fechar a entrada cedo
                }
            });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> {
                try (InputStream is = p.getInputStream()) {
                    is.transferTo(out);
                } catch (IOException ignored) {
                    // tratado pelo código de saída
                }
            });
            Thread.ofVirtual().start(() -> {
                try (InputStream es = p.getErrorStream()) {
                    es.transferTo(OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                    // stderr descartado: pode conter trechos do arquivo
                }
            });
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException(cmd.get(0) + " passou do tempo-limite");
            }
            writer.join();
            reader.join();
            if (p.exitValue() != 0) throw new IllegalStateException(cmd.get(0) + " terminou com código " + p.exitValue());
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(cmd.get(0) + " indisponível no servidor", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido", e);
        }
    }

    /** Diretório temporário privado, de preferência em memória (/dev/shm). Apagado por {@link #deleteTree}. */
    public static Path privateTempDir() throws IOException {
        Path shm = Path.of("/dev/shm");
        var perms = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        return Files.isDirectory(shm) && Files.isWritable(shm) ? Files.createTempDirectory(shm, "de-media-", perms) : Files.createTempDirectory("de-media-", perms);
    }

    public static void deleteTree(Path dir) {
        if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // melhor esforço; o diretório é privado
                }
            });
        } catch (IOException ignored) {
            // nada a fazer
        }
    }
}
