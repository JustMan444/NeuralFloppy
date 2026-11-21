package com.justman444.neuralfloppy.command;

import com.justman444.neuralfloppy.engine.AskEngine;
import com.justman444.neuralfloppy.tool.NeuralFloppyTool2_0_DT_snapchot2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

public class TestCommandHandler {
    public static void askTestExecute(String[] parts)
    {
        String q = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        AskEngine.AskRequest req = new AskEngine.AskRequest();
        req.question = q;
        req.mode = AskEngine.Mode.API;
        AskEngine.AskResponse resp;
        try {
            resp = AskEngine.ask(req);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("[ASKTEST] " + resp);
    }
    public static void testExecute(String[] parts)
    {
        System.out.println("=== Диагностика NeuralFloppy ===");
        // 1. Папки
        boolean dirsOk = Files.isDirectory(Path.of("data")) &&
                Files.isDirectory(Path.of("archive")) &&
                Files.isDirectory(Path.of("personas")) &&
                Files.isDirectory(Path.of("themes")) &&
                Files.isDirectory(Path.of("presets"));
        System.out.println("Папки: " + (dirsOk ? "OK" : "ОШИБКА"));

        // 2. Ollama
        boolean ollamaOk = false;
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            ollamaOk = resp.statusCode() == 200;
        } catch (Exception e) {
            ollamaOk = false;
        }
        System.out.println("Ollama: " + (ollamaOk ? "OK" : "НЕДОСТУПНА"));

        // 3. Эмбеддинг-модель
        boolean embOk = !NeuralFloppyTool2_0_DT_snapchot2.EmbeddingEngine.getVectors().isEmpty() || Files.exists(Path.of("data/embeddings.json"));
        System.out.println("Эмбеддинги: " + (embOk ? "OK (" + NeuralFloppyTool2_0_DT_snapchot2.EmbeddingEngine.vectors.size() + " векторов)" : "НЕТ ДАННЫХ"));

        // 4. Память
        System.out.println("Активных сообщений: " + NeuralFloppyTool2_0_DT_snapchot2.getMessages().size());
        try {
            System.out.println("Размер chat.ndjson: " + Files.size(Path.of("chat.ndjson")) + " байт");
        } catch (IOException e) {
            System.out.println("Ошибка обработки файла");
        }

        // 5. Веб-сервер
        boolean webOk = false;
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", 8080), 2000);
            webOk = true;
            socket.close();
        } catch (Exception e) {
            webOk = false;
        }
        System.out.println("Веб-сервер (порт 8080): " + (webOk ? "OK" : "НЕ ЗАПУЩЕН"));
        System.out.println("=== Диагностика завершена ===");
    }
}
