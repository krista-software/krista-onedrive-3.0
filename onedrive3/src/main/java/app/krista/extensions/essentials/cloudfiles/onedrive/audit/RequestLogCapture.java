/*
 * Onedrive 3.0 Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.essentials.cloudfiles.onedrive.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import app.krista.extension.authorization.MustAuthorizeException;

/**
 * Thread-local Log4j2 appender for capturing per-request execution logs.
 * A single instance is installed on the root logger once; capture is scoped
 * per-thread via a ThreadLocal, filtered to the onedrive package only.
 *
 * Depends on log4j-core which is present at runtime via krista-apis (compileOnly
 * in build.gradle keeps it out of the fat-jar to avoid duplication).
 */
class RequestLogCapture extends AbstractAppender {

    private static final String ONEDRIVE_PKG =
            "app.krista.extensions.essentials.cloudfiles.onedrive";
    private static final int MAX_LOGS = 200;
    private static final int MAX_MSG_LEN = 500;

    private static final ThreadLocal<List<String>> CAPTURE = new ThreadLocal<>();
    private static volatile boolean installed = false;

    private RequestLogCapture() {
        super("onedrive-request-capture", null, null, true, Property.EMPTY_ARRAY);
    }

    static void install() {
        if (installed) return;
        synchronized (RequestLogCapture.class) {
            if (installed) return;
            try {
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configuration config = ctx.getConfiguration();
                RequestLogCapture appender = new RequestLogCapture();
                appender.start();
                config.addAppender(appender);
                config.getRootLogger().addAppender(appender, null, null);
                // The onedrive logger has additivity="false" so its events never reach the
                // root logger. Register the appender directly on the onedrive logger config
                // so that per-request log capture actually receives onedrive log events.
                org.apache.logging.log4j.core.config.LoggerConfig onedriveLogger =
                        config.getLoggerConfig(ONEDRIVE_PKG);
                if (ONEDRIVE_PKG.equals(onedriveLogger.getName())) {
                    onedriveLogger.addAppender(appender, null, null);
                }
                ctx.updateLoggers();
                installed = true;
            } catch (MustAuthorizeException ignored) {
                throw ignored;
            } catch (Exception ignored) {
                // Non-critical — if install fails, log capture simply won't work.
            }
        }
    }

    static void startCapture() {
        install();
        CAPTURE.set(new ArrayList<>());
    }

    static List<String> stopCapture() {
        List<String> logs = CAPTURE.get();
        CAPTURE.remove();
        return logs != null ? new ArrayList<>(logs) : Collections.emptyList();
    }

    @Override
    public void append(LogEvent event) {
        List<String> logs = CAPTURE.get();
        if (logs == null) return;
        if (!event.getLoggerName().startsWith(ONEDRIVE_PKG)) return;
        if (logs.size() >= MAX_LOGS) return;

        String shortName = event.getLoggerName();
        int dot = shortName.lastIndexOf('.');
        if (dot >= 0) shortName = shortName.substring(dot + 1);

        String msg = "[" + event.getLevel() + "] " + shortName + ": "
                + event.getMessage().getFormattedMessage();
        if (msg.length() > MAX_MSG_LEN) msg = msg.substring(0, MAX_MSG_LEN) + "\u2026";
        logs.add(msg);
    }
}
