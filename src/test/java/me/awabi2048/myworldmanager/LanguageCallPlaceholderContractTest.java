package me.awabi2048.myworldmanager;

import org.junit.jupiter.api.Test;
import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract;
import com.awabi2048.ccsystem.api.localization.LocalizationKey;

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
    private static final Path SOURCE_ROOT = Path.of("src/main/kotlin");
    private static final Pattern CALL_START = Pattern.compile(
        "\\.(getMessage(?:List)?(?:Strict)?|getComponent(?:List)?)\\s*\\("
    );
    private static final Pattern MIGRATION_SEND_START = Pattern.compile("\\bsend\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("^\\s*\"([^\"]+)\"\\s*$", Pattern.DOTALL);
    private static final Pattern GENERATED_KEY = Pattern.compile("^\\s*(\\w+Keys)\\.(\\w+)\\s*$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}|%([A-Za-z0-9_]+)%");
    private static final Pattern MAP_KEY = Pattern.compile("\"([^\"]+)\"\\s+to\\b");

    @Test
    void literalLanguageCallsSupplyExactlyThePlaceholdersRequiredByResources() throws Exception {
        List<String> errors = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".kt")).toList()) {
                String source = Files.readString(file);
                checked += validateCalls(file, source, CALL_START, errors);
                // WorldMigrationServiceのsendラッパーだけを言語呼び出しとして追加検査します。
                if (file.getFileName().toString().equals("WorldMigrationService.kt")) {
                    checked += validateCalls(file, source, MIGRATION_SEND_START, errors);
                }
            }
        }

        if (checked == 0) errors.add("静的に検証できる言語呼び出しがありません");
        if (!errors.isEmpty()) {
            fail("[language call placeholder contract] checked=" + checked + " errors=" + errors.size()
                + "\n" + String.join("\n", errors));
        }
    }

    private static int validateCalls(Path file, String source, Pattern callPattern, List<String> errors) {
        int checked = 0;
        Matcher calls = callPattern.matcher(source);
        while (calls.find()) {
                    int open = source.indexOf('(', calls.start());
                    int close = matchingParenthesis(source, open);
                    if (close < 0) {
                        errors.add(file + ": 呼び出しの閉じ括弧を解析できません");
                        continue;
                    }
                    List<String> arguments = splitTopLevel(source.substring(open + 1, close));
                    int keyIndex = languageKeyIndex(arguments);
                    if (keyIndex < 0) continue;

                    String key = languageKey(arguments.get(keyIndex));
                    // Kotlinの文字列テンプレートは列挙可能なリテラルキーではないため別の契約テストで扱います。
                    if (key.contains("$")) continue;
                    Set<String> required = LocalizationCatalogContract.INSTANCE.placeholders(key);
                    if (required == null) {
                        // 欠落キーを無視すると、そのキーほどプレースホルダー契約から漏れてしまいます。
                        errors.add(file + ": missing language key=" + key);
                        continue;
                    }
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
        return checked;
    }

    private static int languageKeyIndex(List<String> arguments) {
        for (int i = 0; i < arguments.size(); i++) {
            String value = languageKey(arguments.get(i));
            if (value != null && value.contains(".")) return i;
        }
        return -1;
    }

    private static String languageKey(String argument) {
        String literal = literal(argument);
        if (literal != null) return literal;

        Matcher generated = GENERATED_KEY.matcher(argument);
        if (!generated.matches()) return null;
        try {
            Class<?> owner = Class.forName(
                "com.awabi2048.ccsystem.api.localization.generated." + generated.group(1)
            );
            Object value = owner.getField(generated.group(2)).get(null);
            return ((LocalizationKey<?>) value).getId();
        } catch (ReflectiveOperationException | ClassCastException error) {
            throw new IllegalStateException("生成済み言語キーを解決できません: " + argument, error);
        }
    }

    private static String literal(String argument) {
        Matcher matcher = STRING_LITERAL.matcher(argument);
        return matcher.matches() ? matcher.group(1) : null;
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
