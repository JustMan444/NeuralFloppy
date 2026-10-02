import tool.CommandHandler;




//        String[] parts = cmd.split("\\s+");
// 
    // Пытаемся найти команду от модуля
//        Map<String, CommandHandler> moduleCommands = ModuleLoader.getCommandRegistry();
//        if (!moduleCommands.isEmpty()) {
////            for (Map.Entry<String, CommandHandler> entry : moduleCommands.entrySet()) {
////                if (parts[0].equalsIgnoreCase(entry.getKey())) {
////                    entry.getValue().execute(parts);
////                    return;
////
////                }
////            }
//            // Пытаемся найти команду от модуля
//            String moduleResult = ModuleLoader.executeCommand(cmd);
//            if (moduleResult != null) {
//                System.out.println(moduleResult);
//                return;
//            }
//
//        }

    // Старый оставь, но пусть делегирует:
//    @Override
//    public String askLLM(String query, JsonObject state) {
//        return askLLM(query, state, null);
//    }


//    public void saveToColumn(String column, String data, String format) {
//        try {
//            String storageMode = StorageManager.getMode();
//            String fileName = "chat_" + column + ".ndjson";
//            Message msg = new Message("SYSTEM", data, Instant.now().getEpochSecond());
//            String line = GSON.toJson(msg) + "\n";
//            Path debugPath = Path.of(fileName);
//            Files.writeString(debugPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//            Files.writeString(Path.of(fileName), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//
//            if (storageMode.equals("long") || storageMode.equals("archive")) {
//                MemoryManager.addCompressed(data, null);
//            }
//        } catch (Exception e) {
//            System.err.println("[GameAPI] Ошибка сохранения в колонку " + column + ": " + e.getMessage());
//        }
//    }


//public void saveToColumn(String column, String data, String format) {
//    try {
//        String storageMode = StorageManager.getMode();
//        String fileName = "chat_" + column + ".ndjson";
//
//        // Если это observer — пытаемся распарсить как эпизод
//        if ("observer".equals(format)) {
//            try {
//                JsonObject obj = GSON.fromJson(data, JsonObject.class);
//                if (obj.has("state") || obj.has("action")) {
//                    // Это эпизод q-agent — пишем как есть
//                    String line = GSON.toJson(obj) + "\n";
//                    Path debugPath = Path.of(fileName);
//                    System.out.println("[COLUMN] Пишу эпизод в: " + debugPath.toAbsolutePath());
//                    Files.writeString(debugPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//                    return;
//                }
//            } catch (Exception e) {
//                // не эпизод — падаем в старую логику
//            }
//        }
//
//        // Обычная логика для Message
//        Message msg = new Message("SYSTEM", data, Instant.now().getEpochSecond());
//        String line = GSON.toJson(msg) + "\n";
//        Path debugPath = Path.of(fileName);
//        System.out.println("[COLUMN] Пишу в: " + debugPath.toAbsolutePath());
//        Files.writeString(debugPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//
//        if (storageMode.equals("long") || storageMode.equals("archive")) {
//            MemoryManager.addCompressed(data, null);
//        }
//    } catch (Exception e) {
//        System.err.println("[GameAPI] Ошибка сохранения в колонку " + column + ": " + e.getMessage());
//    }
//}
