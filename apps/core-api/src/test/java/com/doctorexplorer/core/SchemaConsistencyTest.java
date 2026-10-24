package com.doctorexplorer.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * schema.sql precisa ser a concatenação exata das migrações (cabeçalho + V1..Vn em ordem numérica).
 * Regenere com {@code python3 scripts/gen_schema.py}. Também garante que toda migração começa com linha
 * em branco, para a concatenação ficar legível.
 */
class SchemaConsistencyTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String HEADER = "-- GERADO por scripts/gen_schema.py a partir de db/migration/V*.sql. Não edite à mão.\n";
    private static final Pattern VERSION = Pattern.compile("V(\\d+(?:_\\d+)*)__.*\\.sql");

    @Test
    void schemaSqlIsExactConcatenationOfMigrations() throws IOException {
        List<Path> migrations = migrations();
        assertThat(migrations).isNotEmpty();

        StringBuilder expected = new StringBuilder(HEADER);
        for (Path m : migrations) {
            String content = Files.readString(m, StandardCharsets.UTF_8);
            assertThat(content).as("%s deve começar com linha em branco", m.getFileName()).startsWith("\n");
            expected.append(content);
        }
        String actual = Files.readString(RESOURCES.resolve("schema.sql"), StandardCharsets.UTF_8);
        assertThat(actual).as("schema.sql desatualizado: rode python3 scripts/gen_schema.py").isEqualTo(expected.toString());
    }

    @Test
    void migrationVersionsAreUniqueAndSequential() throws IOException {
        List<int[]> versions = migrations().stream().map(p -> version(p.getFileName().toString())).toList();
        for (int i = 0; i < versions.size(); i++) {
            assertThat(versions.get(i)[0]).as("versões devem ser V1..Vn sem buracos").isEqualTo(i + 1);
        }
    }

    private static List<Path> migrations() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("db/migration"))) {
            return files.filter(p -> VERSION.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(p -> version(p.getFileName().toString())[0]))
                    .toList();
        }
    }

    private static int[] version(String name) {
        Matcher m = VERSION.matcher(name);
        if (!m.matches()) throw new IllegalArgumentException(name);
        return Stream.of(m.group(1).split("_")).mapToInt(Integer::parseInt).toArray();
    }
}
