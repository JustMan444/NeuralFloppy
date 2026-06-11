# 💾 NeuralFloppy TOOL (v1.7 In Development)

[English](#english) | [Русский](#русский)

## Versions
---
NeuralFloppy 1.0          *Manual basic version of the script // Ручная базовая версия скрипта*
NeuralFloppyAPI 1.0       *The basic version of the script for working with the API // Базовая версия скрипта для работы с API*
NeuralFloppyLocal 1.0     *The basic version of the script for working with local models // Базовая версия скрипта для работы с локальными моделями*
NeuralFloppyTool 1.1      *Multi-functional version for full operation // Многофункциональная версия для полной работы*
NeuralFloppyCLI 1.1       *Manual augmented version of the script // Ручная дополненная версия скрипта*
NeuralFloppyAPI_CLI 1.1   *An expanded version of the script for working with the API // Дополненная версия скрипта для работы с API*
NeuralFloppyLocal_CLI 1.1 *An expanded version of the script for working with local models // Дополненная версия скрипта для работы с локальными моделями*
NeuralFloppyTool 1.2      *Adding streaming and auto-save support // Добавление поддержки стриминга и авто-сохранений*
NeuralFloppyTool 1.3      *Fixing the encoding bug and adding a web interface // Исправление бага кодировки и добавления веб интерфейса*
NeuralFloppyTool 1.4      *Adding multiple console commands and interface improvements // Добавление множества консольных команд и улучшения интерфейса*
NeuralFloppyTool 1.5V     *Adding embeddings // Добавление эмбедингов *
NeuralFloppyTool 1.6+     *Auto-person changes // Авто-изменения персоны*
NeuralFloppyTool 1.7      *In Development... // В разработке...* 🚀
---
## English

> **"A script that will give eternal memory to AI models."** — An autonomous, asynchronous AI-mentor engine with adaptive digital soul evolution, packed into a lightweight Java monolith. No Python overhead, no external databases, no cloud censorship. Clean engineering for Senior developers and game dev.

### 🚀 Architectural Features

- **736-Dimensional Semantic Space (RAG):** Natively integrated with `nomic-embed-text` via Ollama API. The entire history of your development logs (`BlockNet`, `Just_bot`) is laid out on a vector grid in RAM. Semantic search finds context and intent in < 10 milliseconds instead of matching keywords.
- **Local Vector Cache:** Upon server startup, the Java backend instantly loads thousands of pre-calculated 736D embeddings from a local JSON file. Zero CPU/GPU overhead on restarts.
- **Dynamic Ego Synthesis (`:persona auto <N>`):** A breakthrough in cognitive software. The AI autonomously analyzes the conversation history in a background thread and completely overwrites/expands its system prompt, adapting to your current development milestones on the fly.
- **Cyberpunk Web UI (Port 8080):** A minimalist, high-contrast control panel in Fallout/Matrix terminal style. Complete interactive control over temperature sliders, model selection, and smooth, real-time UTF-8 chunk streaming directly to the browser.
- **Dual-Rail Inference:** Instant switching between total offline autonomy (`:mode local` via Ollama) and giant cloud MoE models (`:mode api` via OpenRouter, e.g., `openai/gpt-oss-120b:free`).

### 🛠 Command Contract (`:help`)

```bash
:mode api|local|manual - Switch inference rails
:model <name>           - Hot-swap the AI model
:models                - Scan and list locally downloaded Ollama models
:status                - Engine telemetry (state, database size in bytes)
:embed build           - Trigger 736-dimensional indexing of the database
:embed on|off          - Toggle semantic associative search
:persona auto <N>      - Enable auto-persona mutation every N messages
:web                   - Spin up the local HTTP server on port 8080
:exit                  - Safely shutdown the monolith
```

### 📦 Quick Start

1. **Compile the Monolith:**
   ```bash
   javac -encoding UTF-8 -cp "lib/gson-2.10.1.jar" src/tool/NeuralFloppyTool1_6.java -d out
   ```
2. **Launch the Server:**
   ```bash
   java -Dfile.encoding=UTF-8 -cp "out;lib/gson-2.10.1.jar" Tool.NeuralFloppyTool1_6
   ```

### ⚖️ License & Rights

Licensed under the **GNU General Public License v3 (GPL v3)**. 
Используйте код с осторожностью.Copyright (C) 2026 Just_Man444 (NeuralFloppy Author). All rights reserved.
---

## Русский

> **«Скрипт, который даст вечную память ИИ-моделям».** — Автономный асинхронный движок ИИ-наставника с динамической эволюцией цифровой души, упакованный в легковесный Java-монолит. Никаких питоновских костылей, внешних баз данных и облачной цензуры. Чистая инженерия для Senior-разработчиков и геймдева.

### 🚀 Архитектурные Фичи

- **736-Мерное Семантическое Пространство (RAG):** Нативно интегрирован с `nomic-embed-text` через Ollama API. Вся история твоих логов разработки раскладывается по координатной сетке смыслов в ОЗУ. Поиск ищет не буквы, а контекст и суть за < 10 миллисекунд!
- **Локальный Кэш Векторов:** При старте сервера бэкенд мгновенно подтягивает тысячи готовых 736D-эмбеддингов из локального JSON-файла. Ноль нагрузки на процессор при перезапусках.
- **Динамический Синтез Эго (`:persona auto <N>`):** Прорыв в области когнитивного софта. ИИ сам фоновым потоком анализирует историю диалогов и полностью переписывает/дополняет свой системный промпт, подстраиваясь под твои текущие геймдев-задачи на лету.
- **Киберпанк Web UI (Порт 8080):** Минималистичная, контрастная панель управления в стиле Fallout/Matrix. Полный интерактивный контроль: ползунки температуры, переключение моделей и живой, плавный UTF-8 стриминг чанков прямо в браузер.

### 📦 Быстрый Старт

1. **Сборка Монолита:**
   ```bash
   javac -encoding UTF-8 -cp "lib/gson-2.10.1.jar" src/tool/NeuralFloppyTool1_6.java -d out
   ```
2. **Запуск Бортового Сервера:**
   ```bash
   java -Dfile.encoding=UTF-8 -cp "out;lib/gson-2.10.1.jar" Tool.NeuralFloppyTool1_6
   ```
3. **Выход в Сеть:**
   Введи в консоли команду `:web` и открывай браузер на `http://localhost:8080`.

### ⚖️ Лицензия и Права

Проект защищён под лицензией **GNU General Public License v3 (GPL v3)**. 

Copyright (C) 2026 Just_Man444 (NeuralFloppy Author). All rights reserved.{content: }
