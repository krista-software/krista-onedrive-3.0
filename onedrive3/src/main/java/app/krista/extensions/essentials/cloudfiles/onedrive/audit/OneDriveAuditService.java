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

import app.krista.extension.authorization.MustAuthorizeException;
import app.krista.extensions.essentials.cloudfiles.onedrive.impl.util.Constants;
import app.krista.extensions.util.KeyValueStore;
import app.krista.ksdk.context.AuthorizationContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kristasoft.common.holders.ThreadLocalProxy;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Best-effort, non-blocking audit service for OneDrive.
 *
 * Design:
 * - In-memory ring buffers (RECORDS / SUMMARIES) are the primary read source —
 *   the dashboard always sees current data immediately.
 * - KV store is the persistence layer — written asynchronously after every record()
 *   call so data survives extension restarts.
 * - On the first record() or getData() call per invokerId per JVM session, the KV
 *   snapshot is loaded and merged into the in-memory buffers so historical records
 *   reappear after a restart.
 * - All KV writes use a single non-daemon thread (FLUSH_EXECUTOR) and are
 *   deduplicated per invokerId so burst recording never floods the store.
 * - Failures inside record() are swallowed — never affect catalog execution.
 */
@Service
public class OneDriveAuditService implements app.krista.sdk.msauth.extension.audit.AuditRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneDriveAuditService.class);

    private static final int MAX_RECORDS = 500;

    // Fixed KV keys — KV store is already account-scoped per extension instance.
    private static final String KV_RECORDS_KEY = "onedrive_audit_records";
    private static final String KV_SUMMARY_KEY = "onedrive_audit_summary";
    private static final String KV_GLOBAL_COUNTER_KEY = "onedrive_global_counter";

    // Global counter — tracks total catalog requests and first-request timestamp across restarts.
    private static final AtomicLong GLOBAL_TOTAL_REQUESTS    = new AtomicLong(0);
    private static final AtomicLong GLOBAL_FIRST_RECORDED_AT = new AtomicLong(0);
    private static volatile boolean GLOBAL_COUNTER_LOADED = false;
    private static final Object     GLOBAL_COUNTER_LOCK   = new Object();

    // ── In-memory state (survives per-request HK2 re-instantiation; cleared on JVM restart) ──
    private static final ConcurrentHashMap<String, Deque<AuditRecord>> RECORDS   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AuditSummary>       SUMMARIES = new ConcurrentHashMap<>();

    // Tracks which invokerIds have already been restored from KV (once per JVM lifetime).
    private static final Set<String> KV_LOADED    = ConcurrentHashMap.newKeySet();

    // Deduplicates KV flush tasks — at most one pending flush per invokerId at a time.
    private static final Set<String> FLUSH_QUEUED = ConcurrentHashMap.newKeySet();

    // Thread-local used to pair TRIGGERED with its SUCCESS/FAILURE for duration stamping.
    private static final ThreadLocal<AuditRecord> ACTIVE_TRIGGERED = new ThreadLocal<>();

    // Thread-local set by startRequest() — used for duration when no TRIGGERED record is queued.
    private static final ThreadLocal<Long> REQUEST_START = new ThreadLocal<>();

    // Single non-daemon thread for all KV writes — serialises snapshot writes safely.
    // Non-daemon ensures any pending flush completes before JVM shuts down on extension upgrade.
    private static final ExecutorService FLUSH_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "onedrive-audit-flush");
        t.setDaemon(false);
        return t;
    });

    // Gson that strips executionLogs from KV writes (logs can be MB-sized; in-memory only).
    private static final Gson KV_GSON = new GsonBuilder()
            .addSerializationExclusionStrategy(new com.google.gson.ExclusionStrategy() {
                @Override public boolean shouldSkipField(com.google.gson.FieldAttributes f) {
                    return AuditRecord.class.equals(f.getDeclaringClass())
                            && "executionLogs".equals(f.getName());
                }
                @Override public boolean shouldSkipClass(Class<?> clazz) { return false; }
            })
            .create();

    private final KeyValueStore keyValueStore;

    @Inject
    public OneDriveAuditService(KeyValueStore keyValueStore) {
        this.keyValueStore = keyValueStore;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts log capture and timestamps the beginning of a catalog request.
     * Call this at the very start of a catalog method, before any business logic runs.
     * Paired with {@code record(SUCCESS)} or {@code record(FAILURE)} at the end.
     */
    public void startRequest() {
        try {
            RequestLogCapture.stopCapture(); // clear any stale capture from a previous request on this thread
            RequestLogCapture.startCapture();
            REQUEST_START.set(System.currentTimeMillis());
        } catch (Exception ignored) {}
    }

    /**
     * Convenience overload that starts log capture, timestamps the request, AND records
     * {@code CATALOG_REQUEST_TRIGGERED} so the audit dashboard counts it in totalRequests
     * and duration stamping pairs correctly with the terminal SUCCESS/FAILURE record.
     *
     * <p>Prefer this overload in every catalog method instead of the no-arg {@link #startRequest()}.
     */
    public void startRequest(String invokerId, String catalogName) {
        startRequest();
        record(invokerId, AuditEventType.CATALOG_REQUEST_TRIGGERED, "triggered", catalogName, null, null, null);
    }

    /** AuditRecorder interface implementation — accepts String eventType. */
    @Override
    public void record(String invokerId, String eventType, String status,
                       String catalogName, String reason, String exceptionClass,
                       Map<String, String> metadata) {
        AuditEventType type;
        try { type = AuditEventType.valueOf(eventType); } catch (Exception e) { type = null; }
        if (type != null) {
            record(invokerId, type, status, catalogName, reason, exceptionClass, metadata);
        }
    }

    public void record(String invokerId, AuditEventType eventType, String status,
                       String catalogName, String reason, String exceptionClass,
                       Map<String, String> metadata) {
        if (invokerId == null) return;
        try {
            ensureKvLoaded(invokerId);

            AuditRecord rec = new AuditRecord();
            rec.id             = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            rec.timestamp      = System.currentTimeMillis();
            rec.eventType      = eventType.name();
            rec.status         = status;
            rec.catalogName    = catalogName;
            rec.reason         = trimSafe(reason, 500);
            rec.exceptionClass = exceptionClass;
            rec.metadata       = metadata;
            rec.triggeredBy    = resolveTriggeredBy();

            // Append to in-memory ring buffer
            Deque<AuditRecord> records = RECORDS.computeIfAbsent(invokerId, k -> new ConcurrentLinkedDeque<>());
            records.addLast(rec);
            while (records.size() > MAX_RECORDS) records.pollFirst();

            // Update in-memory summary
            AuditSummary summary = SUMMARIES.computeIfAbsent(invokerId, k -> new AuditSummary());
            summary.updateWith(rec);

            // Per-request execution log capture + duration stamping (ThreadLocal only)
            if (eventType == AuditEventType.CATALOG_REQUEST_TRIGGERED) {
                RequestLogCapture.stopCapture();  // clear any stale capture on this thread
                ACTIVE_TRIGGERED.remove();
                RequestLogCapture.startCapture();
                ACTIVE_TRIGGERED.set(rec);

            } else if (eventType == AuditEventType.CATALOG_REQUEST_SUCCESS
                    || eventType == AuditEventType.CATALOG_REQUEST_FAILURE) {
                List<String> logs     = RequestLogCapture.stopCapture();
                AuditRecord triggered = ACTIVE_TRIGGERED.get();
                ACTIVE_TRIGGERED.remove();
                // Fallback for async catalogs: TRIGGERED is recorded on the main request thread
                // but SUCCESS/FAILURE may be called from a different thread, so the ThreadLocal
                // is null there. Scan backward in the in-memory deque for the most recent
                // un-stamped TRIGGERED.
                if (triggered == null) {
                    Deque<AuditRecord> existingRecs = RECORDS.get(invokerId);
                    if (existingRecs != null) {
                        Iterator<AuditRecord> it = existingRecs.descendingIterator();
                        while (it.hasNext()) {
                            AuditRecord r = it.next();
                            if (AuditEventType.CATALOG_REQUEST_TRIGGERED.name().equals(r.eventType)
                                    && r.durationMs == 0) {
                                triggered = r;
                                break;
                            }
                        }
                    }
                }
                if (triggered != null) {
                    long duration = rec.timestamp - triggered.timestamp;
                    rec.durationMs       = duration;
                    triggered.durationMs = duration;  // stamp the already-queued TRIGGERED record in-place
                } else {
                    // startRequest() path: no TRIGGERED record, use REQUEST_START for duration.
                    Long startTime = REQUEST_START.get();
                    REQUEST_START.remove();
                    if (startTime != null) {
                        rec.durationMs = rec.timestamp - startTime;
                    }
                }
                if (!logs.isEmpty()) {
                    if (triggered != null) triggered.executionLogs = logs;
                    rec.executionLogs = logs;
                }
            }

            scheduleFlush(invokerId);

            // Increment global counter on every completed catalog request (success, failure,
            // or OAuth callback token refresh).
            if (eventType == AuditEventType.CATALOG_REQUEST_SUCCESS
                    || eventType == AuditEventType.CATALOG_REQUEST_FAILURE
                    || eventType == AuditEventType.TOKEN_REFRESHED) {
                ensureGlobalCounterLoaded();
                long now = System.currentTimeMillis();
                GLOBAL_FIRST_RECORDED_AT.compareAndSet(0, now);
                GLOBAL_TOTAL_REQUESTS.incrementAndGet();
                persistGlobalCounter();
            }

        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit record skipped (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Convenience overload for MustAuthorizeException — records CATALOG_REQUEST_FAILURE
     * (so logs and duration are attached) then records the classified auth event type
     * (e.g. TOKEN_EXPIRY_DETECTED) for health-dashboard tracking.
     */
    public void recordAuthError(String invokerId, String catalogName, MustAuthorizeException cause) {
        if (invokerId == null) return;
        try {
            // 1. CATALOG_REQUEST_FAILURE — stops log capture and stamps duration on the TRIGGERED record.
            record(invokerId, AuditEventType.CATALOG_REQUEST_FAILURE, "failure", catalogName,
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
            // 2. Classified auth event for health tracking (TOKEN_EXPIRY_DETECTED, etc.).
            AuditEventType type = classifyAuthError(cause.getMessage());
            record(invokerId, type, "failure", catalogName,
                    cause.getMessage(), cause.getClass().getSimpleName(), null);
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit auth error record skipped (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Returns filtered audit data. Reads from the in-memory ring buffer (always current).
     * On the first call per invokerId the KV snapshot is loaded so historical records
     * from before the last restart are merged in.
     */
    public AuditData getData(String invokerId, Long fromTs, Long toTs, String type, String status) {
        if (invokerId == null) return emptyData();
        try {
            ensureKvLoaded(invokerId);

            Deque<AuditRecord> records = RECORDS.getOrDefault(invokerId, new ConcurrentLinkedDeque<>());
            AuditSummary summary       = SUMMARIES.getOrDefault(invokerId, new AuditSummary());

            List<AuditRecord> filtered = new ArrayList<>(records);
            if (fromTs  != null) filtered.removeIf(r -> r.timestamp < fromTs);
            if (toTs    != null) filtered.removeIf(r -> r.timestamp > toTs);
            if (type    != null && !type.isBlank())
                filtered.removeIf(r -> !type.equalsIgnoreCase(r.eventType));
            if (status  != null && !status.isBlank())
                filtered.removeIf(r -> !status.equalsIgnoreCase(r.status));

            filtered.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
            return new AuditData(summary, filtered);
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit getData error: {}", e.getMessage());
            return emptyData();
        }
    }

    /**
     * Clears records within [fromTs, toTs] and recalculates the summary.
     */
    public void clearRecords(String invokerId, Long fromTs, Long toTs) {
        if (invokerId == null) return;
        try {
            Deque<AuditRecord> records = RECORDS.get(invokerId);
            if (records == null) return;

            records.removeIf(r ->
                    (fromTs == null || r.timestamp >= fromTs) &&
                    (toTs   == null || r.timestamp <= toTs));

            AuditSummary rebuilt = new AuditSummary();
            records.forEach(rebuilt::updateWith);
            SUMMARIES.put(invokerId, rebuilt);

            scheduleFlush(invokerId);
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit clearRecords error (non-critical): {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KV persistence helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads KV snapshot into RECORDS / SUMMARIES on the first call per invokerId per JVM session.
     * Subsequent calls are no-ops (KV_LOADED gate).
     */
    private void ensureKvLoaded(String invokerId) {
        if (!KV_LOADED.add(invokerId)) return; // already loaded for this JVM session
        try {
            Object rawRecords = keyValueStore.get(KV_RECORDS_KEY);
            Object rawSummary = keyValueStore.get(KV_SUMMARY_KEY);
            String recordsJson = rawRecords != null ? String.valueOf(rawRecords) : null;
            String summaryJson = rawSummary != null ? String.valueOf(rawSummary) : null;

            if (recordsJson != null && !recordsJson.isBlank()) {
                AuditRecord[] loaded = Constants.GSON.fromJson(recordsJson, AuditRecord[].class);
                if (loaded != null) {
                    Deque<AuditRecord> deque = RECORDS.computeIfAbsent(invokerId, k -> new ConcurrentLinkedDeque<>());
                    // Prepend historical records (they are older than any in-memory records)
                    for (int i = loaded.length - 1; i >= 0; i--) {
                        deque.addFirst(loaded[i]);
                    }
                    while (deque.size() > MAX_RECORDS) deque.pollFirst();
                }
            }

            if (summaryJson != null && !summaryJson.isBlank()) {
                AuditSummary loaded = Constants.GSON.fromJson(summaryJson, AuditSummary.class);
                if (loaded != null) {
                    if (SUMMARIES.containsKey(invokerId)) {
                        // Rebuild from all records to avoid double-counting
                        AuditSummary rebuilt = new AuditSummary();
                        Deque<AuditRecord> deque = RECORDS.get(invokerId);
                        if (deque != null) deque.forEach(rebuilt::updateWith);
                        SUMMARIES.put(invokerId, rebuilt);
                    } else {
                        SUMMARIES.put(invokerId, loaded);
                    }
                }
            }
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit KV restore skipped (non-critical): {}", e.getMessage());
        }
    }

    /**
     * Submits a KV snapshot flush task if one is not already queued for this invokerId.
     * At most one flush task is pending per invokerId at any time.
     */
    private void scheduleFlush(String invokerId) {
        if (FLUSH_QUEUED.add(invokerId)) {
            final KeyValueStore store = keyValueStore;
            final Map<Class<?>, Object> threadLocals = ThreadLocalProxy.getAll();
            FLUSH_EXECUTOR.submit(() -> {
                try {
                    ThreadLocalProxy.setAll(threadLocals);
                    FLUSH_QUEUED.remove(invokerId);
                    doFlush(invokerId, store);
                } finally {
                    ThreadLocalProxy.removeAll();
                }
            });
        }
    }

    /**
     * Snapshots the current in-memory state and writes it to KV.
     * executionLogs are stripped (KV_GSON) to keep the payload small.
     */
    private static void doFlush(String invokerId, KeyValueStore store) {
        try {
            Deque<AuditRecord> records = RECORDS.get(invokerId);
            AuditSummary summary       = SUMMARIES.get(invokerId);
            if (records != null) {
                store.put(KV_RECORDS_KEY, KV_GSON.toJson(new ArrayList<>(records)));
            }
            if (summary != null) {
                store.put(KV_SUMMARY_KEY, Constants.GSON.toJson(summary));
            }
        } catch (MustAuthorizeException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.warn("Audit KV flush error (non-critical): {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User resolution
    // ─────────────────────────────────────────────────────────────────────────

    private static String resolveTriggeredBy() {
        try {
            AuthorizationContext ctx = ThreadLocalProxy
                    .getThreadLocal(AuthorizationContext.class).get();
            if (ctx != null && ctx.isAuthenticated()) {
                app.krista.ksdk.accounts.Account account = ctx.getAuthorizedAccount();
                if (account != null) {
                    if (account.getPerson() != null
                            && account.getPerson().getPersonName() != null
                            && !account.getPerson().getPersonName().isBlank()) {
                        return account.getPerson().getPersonName();
                    }
                    if (account.getPrimaryEmailAddress() != null
                            && !account.getPrimaryEmailAddress().isBlank()) {
                        return account.getPrimaryEmailAddress();
                    }
                    if (account.getAccountId() != null) {
                        return account.getAccountId();
                    }
                }
            }
        } catch (MustAuthorizeException ignored) {
            throw ignored;
        } catch (Exception ignored) {
            // ThreadLocalProxy returns null outside a catalog-request context (e.g. webhooks)
        }
        return "krista";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth error classification
    // ─────────────────────────────────────────────────────────────────────────

    static AuditEventType classifyAuthError(String message) {
        if (message == null) return AuditEventType.AUTH_CONFIG_FAILURE;
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("password") || lower.contains("aadsts50126") || lower.contains("aadsts50173"))
            return AuditEventType.PASSWORD_CHANGE_DETECTED;

        if (lower.contains("session expired") || lower.contains("token expired") ||
            lower.contains("refresh token")   || lower.contains("aadsts70008")   ||
            lower.contains("aadsts700082")    || lower.contains("aadsts700084")  ||
            lower.contains("invalid_grant"))
            return AuditEventType.TOKEN_EXPIRY_DETECTED;

        if (lower.contains("invalid token") || lower.contains("aadsts16000"))
            return AuditEventType.INVALID_TOKEN_DETECTED;

        if ((lower.contains("client secret") && lower.contains("expir")) || lower.contains("aadsts7000222"))
            return AuditEventType.CLIENT_SECRET_EXPIRY_DETECTED;

        if (lower.contains("application's connection") || lower.contains("invalid client secret") ||
            lower.contains("invalid_client")           || lower.contains("aadsts7000215"))
            return AuditEventType.INVALID_CLIENT_SECRET_DETECTED;

        return AuditEventType.AUTH_CONFIG_FAILURE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static String trimSafe(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "\u2026";
    }

    private static AuditData emptyData() {
        return new AuditData(new AuditSummary(), Collections.emptyList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Global counter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the global counter (totalRequests + firstRecordedAt) persisted across restarts.
     */
    public Map<String, Object> getGlobalCounter() {
        ensureGlobalCounterLoaded();
        Map<String, Object> gc = new HashMap<>();
        gc.put("totalRequests",   GLOBAL_TOTAL_REQUESTS.get());
        gc.put("firstRecordedAt", GLOBAL_FIRST_RECORDED_AT.get());
        return gc;
    }

    private void ensureGlobalCounterLoaded() {
        if (GLOBAL_COUNTER_LOADED) return;
        synchronized (GLOBAL_COUNTER_LOCK) {
            if (GLOBAL_COUNTER_LOADED) return;
            try {
                Object raw = keyValueStore.get(KV_GLOBAL_COUNTER_KEY);
                if (raw != null) {
                    String json = raw.toString();
                    if (!json.isBlank()) {
                        Map<?, ?> gc = Constants.GSON.fromJson(json, Map.class);
                        Object tr  = gc.get("totalRequests");
                        Object fra = gc.get("firstRecordedAt");
                        if (tr  instanceof Number) GLOBAL_TOTAL_REQUESTS.set(((Number) tr).longValue());
                        if (fra instanceof Number) GLOBAL_FIRST_RECORDED_AT.set(((Number) fra).longValue());
                    }
                }
            } catch (Exception ignored) {}
            GLOBAL_COUNTER_LOADED = true;
        }
    }

    private void persistGlobalCounter() {
        try {
            Map<String, Object> gc = new HashMap<>();
            gc.put("totalRequests",   GLOBAL_TOTAL_REQUESTS.get());
            gc.put("firstRecordedAt", GLOBAL_FIRST_RECORDED_AT.get());
            keyValueStore.put(KV_GLOBAL_COUNTER_KEY, Constants.GSON.toJson(gc));
        } catch (Exception ignored) {}
    }
}
