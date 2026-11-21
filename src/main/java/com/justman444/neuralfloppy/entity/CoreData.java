package com.justman444.neuralfloppy.entity;

import com.google.gson.Gson;
import com.justman444.neuralfloppy.module.ModuleContext;
import com.justman444.neuralfloppy.tool.NeuralFloppyCore;
import com.justman444.neuralfloppy.tool.NeuralFloppyTool2_0_DT_snapchot2;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreData {
    private static final String PERSONA_FILE = "persona.txt";
    private static final String NDJSON_FILE = "chat.ndjson";
    private static final String ARCHIVE_DIR = "archive";
    private static String API_KEY = "sk-or-v1-....."; // API ключ
    private static double temperature = 0.7;
    private static int maxTokens = 500;
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final Gson GSON = new Gson();
    private static BufferedReader reader;
    private static List<NeuralFloppyTool2_0_DT_snapchot2.Message> messages = new ArrayList<>();
    private static Map<String, List<Integer>> wordIndex = new HashMap<>();
    private static String currentPersona = "";
    private static NeuralFloppyCore coreInstance;
    public enum Mode { API, LOCAL, MANUAL }
    private static Mode currentMode = Mode.API;
    private static String currentModel = "openrouter/free";
    private static boolean autoSave = true;
    private static boolean streaming = true;
    // Режимы NeuralFloppy 1.9.2
    private static boolean gameModeEnabled = false;    // Активация игрового API
    private static String defaultColumn = null;        // Колонка памяти по умолчанию
    private static boolean silentMode = false;         // Режим без генерации ответа
    private static boolean compressEnabled = true;     // Автосжатие в долгую память
    private static boolean dynamicContext = true;      // Динамический контекст
    private static int contextSize = 10; // по умолчанию
    private static boolean thinkingEnabled = false;
    private static boolean webSearchEnabled = false;
    // Эмбеддинги
    private static boolean embedEnabled = false;
    private static int embedAutoThreshold = 0; // 0 = выключено
    private static int embedNewCount = 0;
    private static boolean embedBuildInProgress = false;
    private static int personaAutoThreshold = 0; // 0 = выключено
    private static int personaNewCount = 0;
    private static int memoryAutoThreshold = 0;
    private static int memoryNewCount = 0;
    private static ModuleContext moduleContext;
    private static int numCtx = 32768;
    private static int warm_tokens = 32000;
    private static String searchEngine = "classic"; // classic | vec | hybrid | manual

    public static String getApiKey() {
        return API_KEY;
    }

    public static void setApiKey(String apiKey) {
        API_KEY = apiKey;
    }

    public static double getTemperature() {
        return temperature;
    }

    public static void setTemperature(double temperature) {
        CoreData.temperature = temperature;
    }

    public static BufferedReader getReader() {
        return reader;
    }

    public static void setReader(BufferedReader reader) {
        CoreData.reader = reader;
    }

    public static int getMaxTokens() {
        return maxTokens;
    }

    public static void setMaxTokens(int maxTokens) {
        CoreData.maxTokens = maxTokens;
    }

    public static List<NeuralFloppyTool2_0_DT_snapchot2.Message> getMessages() {
        return messages;
    }

    public static void setMessages(List<NeuralFloppyTool2_0_DT_snapchot2.Message> messages) {
        CoreData.messages = messages;
    }

    public static Map<String, List<Integer>> getWordIndex() {
        return wordIndex;
    }

    public static void setWordIndex(Map<String, List<Integer>> wordIndex) {
        CoreData.wordIndex = wordIndex;
    }

    public static String getCurrentPersona() {
        return currentPersona;
    }

    public static void setCurrentPersona(String currentPersona) {
        CoreData.currentPersona = currentPersona;
    }

    public static NeuralFloppyCore getCoreInstance() {
        return coreInstance;
    }

    public static void setCoreInstance(NeuralFloppyCore coreInstance) {
        CoreData.coreInstance = coreInstance;
    }

    public static Mode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(Mode currentMode) {
        CoreData.currentMode = currentMode;
    }

    public static String getCurrentModel() {
        return currentModel;
    }

    public static void setCurrentModel(String currentModel) {
        CoreData.currentModel = currentModel;
    }

    public static boolean isAutoSave() {
        return autoSave;
    }

    public static void setAutoSave(boolean autoSave) {
        CoreData.autoSave = autoSave;
    }

    public static boolean isStreaming() {
        return streaming;
    }

    public static void setStreaming(boolean streaming) {
        CoreData.streaming = streaming;
    }

    public static boolean isGameModeEnabled() {
        return gameModeEnabled;
    }

    public static void setGameModeEnabled(boolean gameModeEnabled) {
        CoreData.gameModeEnabled = gameModeEnabled;
    }

    public static String getDefaultColumn() {
        return defaultColumn;
    }

    public static void setDefaultColumn(String defaultColumn) {
        CoreData.defaultColumn = defaultColumn;
    }

    public static boolean isCompressEnabled() {
        return compressEnabled;
    }

    public static void setCompressEnabled(boolean compressEnabled) {
        CoreData.compressEnabled = compressEnabled;
    }

    public static boolean isSilentMode() {
        return silentMode;
    }

    public static void setSilentMode(boolean silentMode) {
        CoreData.silentMode = silentMode;
    }

    public static boolean isDynamicContext() {
        return dynamicContext;
    }

    public static void setDynamicContext(boolean dynamicContext) {
        CoreData.dynamicContext = dynamicContext;
    }

    public static int getContextSize() {
        return contextSize;
    }

    public static void setContextSize(int contextSize) {
        CoreData.contextSize = contextSize;
    }

    public static boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public static void setThinkingEnabled(boolean thinkingEnabled) {
        CoreData.thinkingEnabled = thinkingEnabled;
    }

    public static boolean isWebSearchEnabled() {
        return webSearchEnabled;
    }

    public static void setWebSearchEnabled(boolean webSearchEnabled) {
        CoreData.webSearchEnabled = webSearchEnabled;
    }

    public static boolean isEmbedEnabled() {
        return embedEnabled;
    }

    public static void setEmbedEnabled(boolean embedEnabled) {
        CoreData.embedEnabled = embedEnabled;
    }

    public static int getEmbedAutoThreshold() {
        return embedAutoThreshold;
    }

    public static void setEmbedAutoThreshold(int embedAutoThreshold) {
        CoreData.embedAutoThreshold = embedAutoThreshold;
    }

    public static int getEmbedNewCount() {
        return embedNewCount;
    }

    public static void setEmbedNewCount(int embedNewCount) {
        CoreData.embedNewCount = embedNewCount;
    }

    public static boolean isEmbedBuildInProgress() {
        return embedBuildInProgress;
    }

    public static void setEmbedBuildInProgress(boolean embedBuildInProgress) {
        CoreData.embedBuildInProgress = embedBuildInProgress;
    }

    public static int getPersonaAutoThreshold() {
        return personaAutoThreshold;
    }

    public static void setPersonaAutoThreshold(int personaAutoThreshold) {
        CoreData.personaAutoThreshold = personaAutoThreshold;
    }

    public static int getPersonaNewCount() {
        return personaNewCount;
    }

    public static void setPersonaNewCount(int personaNewCount) {
        CoreData.personaNewCount = personaNewCount;
    }

    public static int getMemoryNewCount() {
        return memoryNewCount;
    }

    public static void setMemoryNewCount(int memoryNewCount) {
        CoreData.memoryNewCount = memoryNewCount;
    }

    public static int getMemoryAutoThreshold() {
        return memoryAutoThreshold;
    }

    public static void setMemoryAutoThreshold(int memoryAutoThreshold) {
        CoreData.memoryAutoThreshold = memoryAutoThreshold;
    }

    public static ModuleContext getModuleContext() {
        return moduleContext;
    }

    public static void setModuleContext(ModuleContext moduleContext) {
        CoreData.moduleContext = moduleContext;
    }

    public static int getNumCtx() {
        return numCtx;
    }

    public static void setNumCtx(int numCtx) {
        CoreData.numCtx = numCtx;
    }

    public static String getSearchEngine() {
        return searchEngine;
    }

    public static void setSearchEngine(String searchEngine) {
        CoreData.searchEngine = searchEngine;
    }

    public static int getWarm_tokens() {
        return warm_tokens;
    }

    public static void setWarm_tokens(int warm_tokens) {
        CoreData.warm_tokens = warm_tokens;
    }
}
