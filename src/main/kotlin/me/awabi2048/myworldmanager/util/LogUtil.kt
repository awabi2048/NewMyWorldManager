package me.awabi2048.myworldmanager.util

import java.util.logging.Logger

object LogUtil {
    private const val SEPARATOR = "============================================================"
    private const val PREFIX = "[MyWorldManager] "

    fun logWithSeparator(logger: Logger, lines: List<String>) {
        logger.info(SEPARATOR)
        lines.forEach { line ->
            logger.info(line)
        }
        logger.info(SEPARATOR)
    }

    fun logWarningBox(logger: Logger, lines: List<String>) {
        logger.warning(SEPARATOR)
        lines.forEach { line ->
            logger.warning("! $line")
        }
        logger.warning(SEPARATOR)
    }
}
