package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LanguageResourceValidationTest {
    private static final String BASE_LOCALE = "ja_jp";
    private static final String LANG_ROOT_PROPERTY = "cc.system.lang.root";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_]+}|%[A-Za-z0-9_]+%");
    private static final Pattern KEY_CALL = Pattern.compile(
        "(?:languageManager|lang)\\.(?:getMessage(?:List)?(?:Strict)?|getComponent(?:List)?|hasKey)\\("
            + "[^\"]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
    );
    private static final Pattern MIGRATION_SEND_CALL = Pattern.compile(
        "\\bsend\\([^\"]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
    );

    @Test
    void languageResourcesStayComplete() throws IOException {
        LanguageSource source = resolveLanguageSource();
        if (System.getProperty(LANG_ROOT_PROPERTY) == null) {
            assertInstanceOf(JarLanguageSource.class, source,
                "default validation must ignore sibling and dirty worktrees");
            assertFalse(source.description().contains("src" + java.io.File.separator + "main"),
                "default validation must use the loaded CC-System artifact");
        }
        Map<String, Map<String, Map<String, Object>>> locales = loadLocales(source);
        List<String> errors = new ArrayList<>();

        if (locales.isEmpty()) {
            errors.add("[lang validation] no locale resources found: " + source.description());
        }
        if (!locales.containsKey(BASE_LOCALE)) {
            errors.add("[lang validation] missing base locale: " + BASE_LOCALE);
        }

        Map<String, Map<String, Object>> baseFiles = locales.getOrDefault(BASE_LOCALE, Map.of());
        Set<String> allFiles = new TreeSet<>();
        locales.values().forEach(files -> allFiles.addAll(files.keySet()));

        for (String locale : new TreeSet<>(locales.keySet())) {
            Map<String, Map<String, Object>> files = locales.get(locale);
            for (String fileName : allFiles) {
                Map<String, Object> base = baseFiles.get(fileName);
                Map<String, Object> actual = files.get(fileName);
                if (base == null) {
                    errors.add(format("extra file", locale, fileName, "<file>", "base locale does not contain this file"));
                } else if (actual == null) {
                    errors.add(format("missing file", locale, fileName, "<file>", "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    compareNode(errors, locale, fileName, "", base, actual);
                    findExtraKeys(errors, locale, fileName, "", base, actual);
                }
            }
        }

        for (String key : referencedLanguageKeys()) {
            for (String locale : new TreeSet<>(locales.keySet())) {
                if (!hasKey(locales.get(locale), key)) {
                    errors.add(format("missing referenced key", locale, "<merged>", key, "referenced by Kotlin source"));
                }
            }
        }

        if (!errors.isEmpty()) {
            fail("[lang validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
    }

    private static Set<String> referencedLanguageKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        Path sourceRoot = Path.of("src/main/kotlin");
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".kt")).toList()) {
                Matcher matcher = KEY_CALL.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
                // WorldMigrationServiceのsendラッパーは、内部でLanguageManagerへ委譲する専用経路です。
                if (file.getFileName().toString().equals("WorldMigrationService.kt")) {
                    Matcher wrappedMatcher = MIGRATION_SEND_CALL.matcher(Files.readString(file));
                    while (wrappedMatcher.find()) {
                        keys.add(wrappedMatcher.group(1));
                    }
                }
            }
        }
        return keys;
    }

    private static LanguageSource resolveLanguageSource() throws IOException {
        String explicit = System.getProperty(LANG_ROOT_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            Path root = Path.of(explicit).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new IOException("Explicit CC-System language root is not a directory: " + root);
            }
            return new DirectoryLanguageSource(root);
        }

        try {
            Class<?> ccSystem = Class.forName("com.awabi2048.ccsystem.CCSystem");
            Path codeSource = Path.of(
                ccSystem.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toAbsolutePath().normalize();
            if (Files.isDirectory(codeSource)) {
                Path root = codeSource.resolve("lang");
                if (!Files.isDirectory(root)) {
                    throw new IOException("CC-System classes directory does not contain lang resources: " + root);
                }
                return new DirectoryLanguageSource(root);
            }
            if (!Files.isRegularFile(codeSource)) {
                throw new IOException("CC-System code source is neither a directory nor a JAR: " + codeSource);
            }
            return new JarLanguageSource(codeSource);
        } catch (ClassNotFoundException | URISyntaxException exception) {
            throw new IOException("Unable to resolve the loaded CC-System code source", exception);
        }
    }

    private static Map<String, Map<String, Map<String, Object>>> loadLocales(LanguageSource source) throws IOException {
        return source.loadLocales();
    }

    private static Map<String, Map<String, Map<String, Object>>> loadDirectoryLocales(Path root) throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return locales;
        }
        try (Stream<Path> localeDirs = Files.list(root)) {
            for (Path localeDir : localeDirs.filter(Files::isDirectory).sorted().toList()) {
                Map<String, Map<String, Object>> files = new LinkedHashMap<>();
                try (Stream<Path> ymlFiles = Files.walk(localeDir)) {
                    for (Path file : ymlFiles.filter(Files::isRegularFile).filter(LanguageResourceValidationTest::isYaml).sorted().toList()) {
                        String relativePath = localeDir.relativize(file).toString().replace('\\', '/');
                        files.put(relativePath, readYaml(file));
                    }
                }
                locales.put(localeDir.getFileName().toString().toLowerCase(), files);
            }
        }
        return locales;
    }

    private static Map<String, Map<String, Map<String, Object>>> loadJarLocales(Path jarPath) throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = new LinkedHashMap<>();
        Set<String> seenEntries = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (JarEntry entry : jar.stream().filter(e -> !e.isDirectory()).toList()) {
                String name = entry.getName();
                if (!name.startsWith("lang/") || !isYaml(Path.of(name))) continue;
                if (!seenEntries.add(name)) {
                    throw new IOException("Duplicate language resource in CC-System JAR: " + name);
                }
                String[] parts = name.split("/", 3);
                if (parts.length != 3) continue;
                String locale = parts[1].toLowerCase();
                try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                    Object loaded = new Yaml(new SafeConstructor(loaderOptions())).load(reader);
                    if (!(loaded instanceof Map<?, ?> map)) {
                        throw new IOException("Language YAML root must be a map: " + jarPath + "!/" + name);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> values = (Map<String, Object>) map;
                    Map<String, Map<String, Object>> files = locales.computeIfAbsent(locale, ignored -> new LinkedHashMap<>());
                    if (files.putIfAbsent(parts[2], values) != null) {
                        throw new IOException("Duplicate language path in CC-System JAR: " + parts[2]);
                    }
                }
            }
        }
        return locales;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path file) throws IOException {
        Object loaded = new Yaml(new SafeConstructor(loaderOptions())).load(Files.readString(file));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("[lang validation] YAML root must be a map: " + file);
        }
        return (Map<String, Object>) map;
    }

    private static LoaderOptions loaderOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        return options;
    }

    private sealed interface LanguageSource permits DirectoryLanguageSource, JarLanguageSource {
        Map<String, Map<String, Map<String, Object>>> loadLocales() throws IOException;
        String description();
    }

    private record DirectoryLanguageSource(Path root) implements LanguageSource {
        @Override
        public Map<String, Map<String, Map<String, Object>>> loadLocales() throws IOException {
            return loadDirectoryLocales(root);
        }

        @Override
        public String description() {
            return root.toString();
        }
    }

    private record JarLanguageSource(Path jar) implements LanguageSource {
        @Override
        public Map<String, Map<String, Map<String, Object>>> loadLocales() throws IOException {
            return loadJarLocales(jar);
        }

        @Override
        public String description() {
            return jar.toString();
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void compareNode(List<String> errors, String locale, String fileName, String path, Object expected, Object actual) {
        if (!nodeType(expected).equals(nodeType(actual))) {
            errors.add(format("type mismatch", locale, fileName, displayPath(path), "expected=" + nodeType(expected) + " actual=" + nodeType(actual)));
            return;
        }
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (!actualMap.containsKey(entry.getKey())) {
                    errors.add(format("missing key", locale, fileName, childPath, "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    compareNode(errors, locale, fileName, childPath, entry.getValue(), actualMap.get(entry.getKey()));
                }
            }
        } else if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            // 翻訳では改行位置と行数が変わり得るため、リスト要素は文字列型と全体の置換契約を検証します。
            for (int index = 0; index < actualList.size(); index++) {
                if (!(actualList.get(index) instanceof String)) {
                    errors.add(format("list element type mismatch", locale, fileName, path + "[" + index + "]", "expected=String actual=" + nodeType(actualList.get(index))));
                }
            }
            Set<String> expectedPlaceholders = placeholders(expectedList);
            Set<String> actualPlaceholders = placeholders(actualList);
            if (!expectedPlaceholders.equals(actualPlaceholders)) {
                errors.add(format("placeholder mismatch", locale, fileName, displayPath(path), "expected=" + expectedPlaceholders + " actual=" + actualPlaceholders));
            }
        } else if (expected instanceof String expectedString && actual instanceof String actualString) {
            Set<String> expectedPlaceholders = placeholders(expectedString);
            Set<String> actualPlaceholders = placeholders(actualString);
            if (!expectedPlaceholders.equals(actualPlaceholders)) {
                errors.add(format("placeholder mismatch", locale, fileName, displayPath(path), "expected=" + expectedPlaceholders + " actual=" + actualPlaceholders));
            }
        }
    }

    private static void findExtraKeys(List<String> errors, String locale, String fileName, String path, Object expected, Object actual) {
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            for (Map.Entry<?, ?> entry : actualMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (!expectedMap.containsKey(entry.getKey())) {
                    errors.add(format("extra key", locale, fileName, childPath, "base=" + BASE_LOCALE + "/" + fileName));
                } else {
                    findExtraKeys(errors, locale, fileName, childPath, expectedMap.get(entry.getKey()), entry.getValue());
                }
            }
        }
    }

    private static boolean hasKey(Map<String, Map<String, Object>> files, String dottedKey) {
        for (Map<String, Object> root : files.values()) {
            if (resolve(root, dottedKey.split("\\.")) != null || hasLiteralKey(root, dottedKey)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object resolve(Map<String, Object> root, String[] parts) {
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasLiteralKey(Map<String, Object> node, String key) {
        if (node.containsKey(key)) return true;
        for (Object value : node.values()) {
            if (value instanceof Map<?, ?> sub && hasLiteralKey((Map<String, Object>) sub, key)) {
                return true;
            }
        }
        return false;
    }

    private static String nodeType(Object value) {
        if (value instanceof Map<?, ?>) return "Map";
        if (value instanceof List<?>) return "List";
        if (value instanceof String) return "String";
        if (value instanceof Number) return "Number";
        if (value instanceof Boolean) return "Boolean";
        if (value == null) return "Null";
        return value.getClass().getSimpleName();
    }

    private static Set<String> placeholders(String value) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static Set<String> placeholders(List<?> values) {
        Set<String> placeholders = new LinkedHashSet<>();
        values.forEach(value -> placeholders.addAll(placeholders(String.valueOf(value))));
        return placeholders;
    }

    private static String displayPath(String path) {
        return path.isEmpty() ? "<root>" : path;
    }

    private static String format(String type, String locale, String file, String key, String detail) {
        return "[lang validation] " + type + "\n"
            + "  locale: " + locale + "\n"
            + "  file: src/main/resources/lang/" + locale + "/" + file + "\n"
            + "  key: " + key + "\n"
            + "  detail: " + detail;
    }
}
