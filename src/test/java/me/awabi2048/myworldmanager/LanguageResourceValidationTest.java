package me.awabi2048.myworldmanager;

import com.awabi2048.ccsystem.api.localization.LocalizationCatalogContract;
import com.awabi2048.ccsystem.api.localization.LocalizationKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class LanguageResourceValidationTest {
    private static final Pattern KEY_CALL = Pattern.compile(
        "(?:languageManager|lang)\\.(getMessageListStrict|getMessageStrict|getMessageList|getMessage|"
            + "getComponentList|getComponent|hasKey)\\([^\"\\)]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
    );
    private static final Pattern MIGRATION_SEND_CALL = Pattern.compile(
        "\\bsend\\([^\"]*?\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\""
    );

    @Test
    void fixedReferencesUseGeneratedKeysAndDynamicBoundariesRemainValid() throws IOException {
        List<String> errors = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/kotlin"))) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".kt")).toList()) {
                String source = Files.readString(file);
                var matcher = KEY_CALL.matcher(source);
                while (matcher.find()) {
                    errors.add("fixed key must use generated LocalizationKey\n  file: " + file
                        + "\n  key: " + matcher.group(2));
                }
                // 移行通知のsendラッパーは動的境界ですが、参照契約はビルド時に検証します。
                if (file.getFileName().toString().equals("WorldMigrationService.kt")) {
                    var wrapped = MIGRATION_SEND_CALL.matcher(source);
                    while (wrapped.find()) {
                        requireKey(errors, file, wrapped.group(1), LocalizationKey.ValueType.TEXT);
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            fail("[embedded localization validation] " + errors.size() + " error(s)\n\n" + String.join("\n", errors));
        }
    }

    private static void requireKey(
        List<String> errors,
        Path file,
        String key,
        LocalizationKey.ValueType expected
    ) {
        if (!LocalizationCatalogContract.INSTANCE.contains(key)) {
            errors.add("missing key\n  file: " + file + "\n  key: " + key);
        } else if (LocalizationCatalogContract.INSTANCE.valueType(key) != expected) {
            errors.add("value type mismatch\n  file: " + file + "\n  key: " + key
                + "\n  expected: " + expected
                + "\n  actual: " + LocalizationCatalogContract.INSTANCE.valueType(key));
        }
    }
}
