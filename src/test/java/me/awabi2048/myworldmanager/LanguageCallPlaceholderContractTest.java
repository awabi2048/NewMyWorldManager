package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

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

class LanguageCallPlaceholderContractTest {
    private static final Path LANGUAGE_ROOT = Path.of("../cc-system/src/main/resources/lang/ja_jp");
    private static final Path SOURCE_ROOT = Path.of("src/main/kotlin");
    private static final Pattern CALL_START = Pattern.compile(
        "\\.(getMessage(?:List)?(?:Strict)?|getComponent(?:List)?)\\s*\\("
    );
    private static final Pattern STRING_LITERAL = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern MAP_KEY = Pattern.compile("\"([^\"]+)\"\\s+to\\b");

    @Test
    void literalLanguageCallsSupplyExactlyThePlaceholdersRequiredByResources() throws Exception {
        Map<String, Object> messages = loadLanguageValues();
        List<String> errors = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".kt")).toList()) {
                String source = Files.readString(file);
                Matcher calls = CALL_START.matcher(source);
                while (calls.find()) {
                    int open = source.indexOf('(', calls.start());
                    int close = matchingParenthesis(source, open);
                    if (close < 0) {
                        errors.add(file + ": 呼び出しの閉じ括弧を解析できません");
                        continue;
                    }
                    List<String> arguments = splitTopLevel(source.substring(open + 1, close));
                    int keyIndex = literalLanguageKeyIndex(arguments);
                    if (keyIndex < 0) continue;

                    String key = literal(arguments.get(keyIndex));
                    Object value = messages.get(key);
                    if (!(value instanceof String) && !(value instanceof List<?>)) continue;

                    Set<String> required = placeholders(value);
                    String placeholderArgument = keyIndex + 1 < arguments.size()
                        ? arguments.get(keyIndex + 1).trim()
                        : "emptyMap()";
                    Set<String> supplied;
                    if (placeholderArgument.startsWith("mapOf(")) {
                        supplied = mapKeys(placeholderArgument);
                    } else if (placeholderArgument.startsWith("emptyMap(")) {
                        supplied = Set.of();
                    } else {
                        continue;
                    }
                    checked++;
                    if (!supplied.containsAll(required)) {
                        Set<String> missing = new TreeSet<>(required);
                        missing.removeAll(supplied);
                        errors.add(file + ": " + key + " missing=" + missing + " supplied=" + supplied);
                    }
                }
            }
        }

        if (checked == 0) errors.add("静的に検証できる言語呼び出しがありません");
        if (!errors.isEmpty()) {
            fail("[language call placeholder contract] checked=" + checked + " errors=" + errors.size()
                + "\n" + String.join("\n", errors));
        }
    }

    private static Map<String, Object> loadLanguageValues() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        try (Stream<Path> files = Files.walk(LANGUAGE_ROOT)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".yml")).toList()) {
                Object loaded = yaml.load(Files.readString(file));
                if (loaded instanceof Map<?, ?> map) flatten(values, "", map);
            }
        }
        return values;
    }

    private static void flatten(Map<String, Object> output, String prefix, Map<?, ?> map) {
        map.forEach((rawKey, value) -> {
            String key = prefix.isEmpty() ? String.valueOf(rawKey) : prefix + "." + rawKey;
            if (value instanceof Map<?, ?> nested) flatten(output, key, nested);
            else output.put(key, value);
        });
    }

    private static int literalLanguageKeyIndex(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            String value = literal(arguments.get(i));
            if (value != null && value.contains(".")) return i;
        }
        return -1;
    }

    private static String literal(String argument) {
        Matcher matcher = STRING_LITERAL.matcher(argument);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static Set<String> placeholders(Object value) {
        Set<String> result = new TreeSet<>();
        if (value instanceof String text) collectPlaceholders(result, text);
        else if (value instanceof List<?> list) list.forEach(line -> collectPlaceholders(result, String.valueOf(line)));
        return result;
    }

    private static void collectPlaceholders(Set<String> output, String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) output.add(matcher.group(1));
    }

    private static Set<String> mapKeys(String expression) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = MAP_KEY.matcher(expression);
        while (matcher.find()) keys.add(matcher.group(1));
        return keys;
    }

    private static int matchingParenthesis(String source, int open) {
        int depth = 0;
        boolean string = false;
        boolean escaped = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
                continue;
            }
            if (c == '"') string = true;
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<String> splitTopLevel(String source) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int round = 0, square = 0, curly = 0;
        boolean string = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
                continue;
            }
            if (c == '"') string = true;
            else if (c == '(') round++;
            else if (c == ')') round--;
            else if (c == '[') square++;
            else if (c == ']') square--;
            else if (c == '{') curly++;
            else if (c == '}') curly--;
            else if (c == ',' && round == 0 && square == 0 && curly == 0) {
                result.add(source.substring(start, i));
                start = i + 1;
            }
        }
        result.add(source.substring(start));
        return result;
    }
}
