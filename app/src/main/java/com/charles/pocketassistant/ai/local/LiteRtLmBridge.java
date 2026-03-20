package com.charles.pocketassistant.ai.local;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class LiteRtLmBridge implements AutoCloseable {
    private static final String TAG = "LiteRtLmBridge";

    private static final String ENGINE_CLASS = "com.google.ai.edge.litertlm.Engine";
    private static final String ENGINE_CONFIG_CLASS = "com.google.ai.edge.litertlm.EngineConfig";
    private static final String BACKEND_CLASS = "com.google.ai.edge.litertlm.Backend";
    private static final String CONVERSATION_CONFIG_CLASS = "com.google.ai.edge.litertlm.ConversationConfig";
    private static final String MESSAGE_CLASS = "com.google.ai.edge.litertlm.Message";
    private static final String MESSAGE_COMPANION_CLASS = "com.google.ai.edge.litertlm.Message$Companion";
    private static final String CONTENT_TEXT_CLASS = "com.google.ai.edge.litertlm.Content$Text";

    private final Context appContext;
    private final Object lock = new Object();

    private Object engine;
    private String loadedModelPath;
    private String loadedBackend;

    @Inject
    public LiteRtLmBridge(@ApplicationContext Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @NonNull
    public String generate(@NonNull String modelPath, @NonNull String prompt) throws Exception {
        synchronized (lock) {
            ensureEngineLocked(modelPath);
            Object conversation = null;
            try {
                Class<?> conversationConfigClass = Class.forName(CONVERSATION_CONFIG_CLASS);
                Object conversationConfig = conversationConfigClass.getConstructor().newInstance();
                conversation = engine.getClass()
                    .getMethod("createConversation", conversationConfigClass)
                    .invoke(engine, conversationConfig);
                Object companion = Class.forName(MESSAGE_CLASS).getField("Companion").get(null);
                Object messageInput = Class.forName(MESSAGE_COMPANION_CLASS)
                    .getMethod("of", String.class)
                    .invoke(companion, prompt);
                Object message = conversation.getClass()
                    .getMethod("sendMessage", Class.forName(MESSAGE_CLASS))
                    .invoke(conversation, messageInput);
                String output = extractText(message);
                Log.i(TAG, "Generated response with backend=" + loadedBackend + ", chars=" + output.length());
                return output;
            } finally {
                closeQuietly(conversation);
            }
        }
    }

    @NonNull
    public String currentBackend() {
        synchronized (lock) {
            return loadedBackend == null ? "" : loadedBackend;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeLocked();
        }
    }

    @NonNull
    private String extractText(@NonNull Object message) throws Exception {
        Class<?> textClass = Class.forName(CONTENT_TEXT_CLASS);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) Class.forName(MESSAGE_CLASS).getMethod("getContents").invoke(message);

        StringBuilder builder = new StringBuilder();
        Method getTextMethod = textClass.getMethod("getText");
        for (Object value : values) {
            if (!textClass.isInstance(value)) {
                continue;
            }
            String text = (String) getTextMethod.invoke(value);
            if (text == null || text.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text);
        }
        return builder.toString().trim();
    }

    private void ensureEngineLocked(@NonNull String modelPath) throws Exception {
        if (engine != null && modelPath.equals(loadedModelPath) && isInitialized(engine)) {
            return;
        }
        closeLocked();

        Exception lastError = null;
        for (String backendName : new String[] { "GPU", "CPU" }) {
            try {
                Class<?> backendClass = Class.forName(BACKEND_CLASS);
                Object backend = backendClass.getField(backendName).get(null);
                Class<?> engineConfigClass = Class.forName(ENGINE_CONFIG_CLASS);
                Constructor<?> configConstructor = engineConfigClass.getConstructor(
                    String.class,
                    backendClass,
                    backendClass,
                    backendClass,
                    Integer.class,
                    String.class
                );
                Object config = configConstructor.newInstance(
                    modelPath,
                    backend,
                    null,
                    null,
                    null,
                    cacheDir().getAbsolutePath()
                );
                Class<?> engineClass = Class.forName(ENGINE_CLASS);
                Object candidate = engineClass.getConstructor(engineConfigClass).newInstance(config);
                engineClass.getMethod("initialize").invoke(candidate);
                engine = candidate;
                loadedModelPath = modelPath;
                loadedBackend = backendName;
                Log.i(TAG, "Initialized LiteRT-LM backend=" + loadedBackend + " for " + modelPath);
                return;
            } catch (Exception error) {
                lastError = unwrap(error);
                Log.w(TAG, "LiteRT-LM backend failed: " + backendName, lastError);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("Unable to initialize LiteRT-LM.");
    }

    private boolean isInitialized(@NonNull Object candidate) throws Exception {
        Object value = candidate.getClass().getMethod("isInitialized").invoke(candidate);
        return value instanceof Boolean && (Boolean) value;
    }

    private void closeLocked() {
        closeQuietly(engine);
        engine = null;
        loadedModelPath = null;
        loadedBackend = null;
    }

    private void closeQuietly(Object target) {
        if (target == null) {
            return;
        }
        try {
            target.getClass().getMethod("close").invoke(target);
        } catch (Exception ignored) {
            // Ignore shutdown errors from the runtime.
        }
    }

    @NonNull
    private File cacheDir() {
        File dir = new File(appContext.getNoBackupFilesDir(), "litertlm-cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    private Exception unwrap(@NonNull Exception error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof Exception exception) {
            return exception;
        }
        return error;
    }
}
