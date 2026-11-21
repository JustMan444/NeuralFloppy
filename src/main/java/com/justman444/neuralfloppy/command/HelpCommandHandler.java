package com.justman444.neuralfloppy.command;

public class HelpCommandHandler {
    public static void execute(String[] parts) {
        if (parts.length > 1 && parts[1].equalsIgnoreCase("all")) {
            System.out.println("""
                            :mode api|local|manual|programmers  - переключить режим
                            :model <имя>            - сменить модель
                            :think on|off           - включить/выключить режим размышлений
                            :websearch on|off       - включить/выключить авто-поиск в интернете
                            :persona                - показать текущую персону
                            :persona save <имя>     - сохранить персону в профиль
                            :persona load <имя>     - загрузить персону из профиля
                            :persona new <имя>      - создать новый профиль
                            :autosave on|off        - вкл/выкл автосохранение
                            :stream on|off          - вкл/выкл потоковый вывод
                            :status                 - показать состояние Tool
                            :models                 - список локальных моделей
                            :save                   - сохранить сессию в архив
                            :exit                   - выход
                            :web                    - открыть чат в браузере
                            :embed build            - построить эмбеддинги
                            :embed on|off           - вкл/выкл семантический поиск
                            :embed auto <N>         - авто-перестроение каждые N сообщений
                            :embed status           - состояние движка
                            :persona auto           - обновить персону через ИИ
                            :persona auto <N>       - авто-обновление каждые N сообщений :persona auto ⚠️ Не рекомендуется при включённом cache hit. ⚠️ Сбрасывает кэш промта (cache miss ~$0.13). ⚠️ Использовать не чаще 100 сообщений.
                            :persona auto off       - отключить авто-обновление
                            :remember               - сжать последние 10 сообщений
                            :memory migrate         - миграция JSON в SQLite (не работает)
                            :memory status          - состояние долгой памяти (не работает)
                            :memory search <текст>  - текстовый поиск по памяти (не работает)
                            :memory auto <N>        - авто-сжатие каждые N сообщений (не работает)
                            :memory purge <дни>     - удалить старые записи (не работает)
                            :memorн default <имя>   - установить дефолтную колонку памяти (не работает)
                            :summarize              - сводка последних 20 сообщений
                            :import <файл>          - импорт JSON-диалогов
                            :think on|off           - размышление
                            :websearch on|off       - поиск в интернете
                            :theme <имя> <уровень>  - применить тему (1=CSS, 2=CSS+HTML, 3=CSS+HTML+JS)
                            :theme off              - сбросить тему
                            :preset                 - пресеты!
                            :themes                 - показать список доступных тем
                            :presets                - показать список доступных пресетов
                            :game off               - отключить api
                            :game status            - получить статус mode programmers
                            :game list              - получить все колонки памяти (for easy mode in api)
                            :game memory <колонка>  -  создать колонку
                            :game clear <колонка>   - удалить данные из колонки
                            :game export <колонка>  - экспортировать колонку
                            :storage default|short|long|archive - выбрать тип архитектуру не рекомендуется для личного использования
                            :silent on|off          - только запись
                            :compress on|off        - авто-сжатие
                            :context dynamic|static -заморозить контекст
                            :test                   - тестирование всех систем
                            :clear                  - очистить консоль
                            :clear session          - сбрасывание активного контекста
                            :modules
                            :module enable <hello>
                            :module disable <hello>
                            :search engine classic|vec|hybrid  - выбор движка поиска
                            :vec                                - статус sqlite-vec 
                            :cache on               - включить построение префикса для Cache Hit
                            :cache off              - выключить построение префикса для Cache Hit
                            :hot freeze             - заморозить текущий префикс
                            :hot refresh            - пересобрать и применить префикс 
                            :hot status             - проверка статуса кэша
                            :asktest                - проверка нового движка (временная команда)
                            :hot config <N>         - регулирует размер префикса (ЧЕМ БОЛЬШЕ ТЕМ БОЛЬШЕ ДЕНЕГ ТРАТИТСЯ ВНАЧАЛЕ И ТЕМ МЕНЬШЕ В ДОЛГОСРОКЕ БОЛЬШИЕ ЗНАЧЕНИЯ ВЫГОДНО ДЛЯ ДЛИННЫХ СЕССИЙ)
                            :warmtokens <N>         - Настроить количество доступных токенов для теплой памяти (дефолт 32к)
                            :contextsize            - Настроить количество доступных токенов для холодной памяти и ниже
                            :url                    - Сменить url (поддерживаются лишь OpenAi форматы!)
                    
                    
                    """); // :NeuralFloppy           -узнать новости проекта
        } else {
            System.out.println("""
                    ===== БАЗОВЫЕ КОМАНДЫ =====
                    :mode api|local|manual|programmers
                    :model <имя>            - сменить модель
                    :models                 - список локальных моделей
                    :status                 - состояние Tool
                    :embed on|off           - вкл/выкл эмбеддинги
                    :embed build            - построить эмбеддинги
                    :web                    - открыть веб-интерфейс
                    :exit                   - выход
                    
                    Для расширенного списка введи :help all
                    """);
        }
    }
}
