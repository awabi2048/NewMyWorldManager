package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class LanguageResourceValidationTest {
    private static final String BASE_LOCALE = "ja_jp";
    private static final Path LANG_ROOT = Path.of("../cc-system/src/main/resources/lang");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Za-z0-9_]+}");
    private static final Pattern KEY_CALL = Pattern.compile(
        "(?:languageManager|lang)\\.(?:getMessage(?:List)?(?:Strict)?|getComponent(?:List)?|hasKey)\\("
            + "[^\"]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
    );

    @Test
    void languageResourcesStayComplete() throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = loadLocales();
        List<String> errors = new ArrayList<>();

        if (locales.isEmpty()) {
            errors.add("[lang validation] no locale directories found: " + LANG_ROOT);
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
            }
        }
        return keys;
    }

    private static Map<String, Map<String, Map<String, Object>>> loadLocales() throws IOException {
        Map<String, Map<String, Map<String, Object>>> locales = new LinkedHashMap<>();
        if (!Files.isDirectory(LANG_ROOT)) {
            return locales;
        }
        try (Stream<Path> localeDirs = Files.list(LANG_ROOT)) {
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path file) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        Object loaded = new Yaml(new SafeConstructor(options)).load(Files.readString(file));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("[lang validation] YAML root must be a map: " + file);
        }
        return (Map<String, Object>) map;
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
        if (value == null) return "Null";
        return "Scalar";
    }

    private static Set<String> placeholders(String value) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
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
