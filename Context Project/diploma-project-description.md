# Дипломное описание проекта `autoshop-notification`

## 1. Назначение проекта

`autoshop-notification` — это отдельный микросервис уведомлений для системы AutoShop. Его основная задача — принимать события о заказах из Kafka, проверять их корректность, обеспечивать идемпотентность обработки, формировать email-уведомления на основе шаблонов, отправлять письма через почтового провайдера и сохранять полную историю обработки в базе данных.

Это прямо зафиксировано в `README.md:3`: сервис потребляет события из Kafka, рендерит шаблоны писем, отправляет email, хранит состояние доставки и защищает процесс механизмами retry, DLQ и идемпотентности по `eventId`.

С архитектурной точки зрения проект решает несколько важных задач сразу:

- изолирует уведомления в отдельный bounded context;
- снимает нагрузку по отправке писем с основного бизнес-сервиса;
- обеспечивает асинхронное взаимодействие через событийную модель;
- позволяет масштабировать обработку уведомлений независимо от ядра системы;
- создаёт аудит и техническую трассировку по каждому событию и каждой попытке доставки.

## 2. Технологический стек

Основной стек проекта определяется в `build.gradle:1` и `build.gradle:21`:

- Java 17 — базовый язык реализации (`build.gradle:11`);
- Spring Boot 3.5.13 — каркас приложения (`build.gradle:3`);
- Spring Web — инфраструктура HTTP и actuator-эндпоинтов (`build.gradle:27`);
- Spring Data JPA — работа с реляционной БД (`build.gradle:23`);
- PostgreSQL — основная БД во время выполнения (`build.gradle:31`, `src/main/resources/application.properties:5`);
- Liquibase — управление схемой БД и миграциями (`build.gradle:28`);
- Spring Kafka — потребление сообщений и настройка retry/DLT (`build.gradle:29`);
- Spring Mail — SMTP-отправка (`build.gradle:24`);
- Thymeleaf — генерация HTML-писем по шаблонам (`build.gradle:25`);
- Lombok — сокращение шаблонного кода в сущностях/моделях (`build.gradle:30`);
- Testcontainers + Kafka/PostgreSQL containers — интеграционные тесты (`build.gradle:34`-`build.gradle:38`);
- H2 — in-memory БД для части тестового окружения (`build.gradle:40`).

По своему характеру это backend-проект микросервисного типа с событийной интеграцией и устойчивой обработкой внешних сообщений.

## 3. Общее архитектурное место сервиса в системе

Согласно `README.md:53`, `README.md:63` и `README.md:98`, сервис включён в экосистему AutoShop следующим образом:

- `autoshop-core` публикует доменные события по заказам в Kafka;
- `autoshop-notification` читает события из топика `autoshop.order-events`;
- при ошибках, неуспешно обработанные записи могут быть перенаправлены в DLT `autoshop.order-events.dlt`;
- локально сервис работает рядом с другими микросервисами: Core на `8080`, AuthService на `8082`, NotificationService на `8083`.

Таким образом, NotificationService выступает как downstream-consumer по отношению к Core и реализует реактивное поведение на события жизненного цикла заказа.

## 4. Поддерживаемые бизнес-события

В `README.md:65`-`README.md:67` и в маппинге `src/main/java/com/vladko/autoshopnotification/template/service/TemplateKeyResolver.java:9` сервис поддерживает три типа доменных событий:

- `ORDER_CREATED` — заказ создан;
- `ORDER_STATUS_CHANGED` — статус заказа изменён;
- `ORDER_COMPLETED` — заказ завершён.

Единый формат транспортного сообщения описан record-моделью `src/main/java/com/vladko/autoshopnotification/event/dto/NotificationEventEnvelope.java:8`. В нём содержатся:

- `eventId` — глобально уникальный идентификатор события;
- `eventType` — тип события;
- `occurredAt` — время возникновения;
- `source` — источник события;
- `version` — версия контракта;
- `correlationId` — корреляционный идентификатор;
- `payload` — полезная нагрузка в JSON.

Под конкретные бизнес-сценарии используются специализированные payload-record’ы:

- `OrderCreatedPayload` — `src/main/java/com/vladko/autoshopnotification/event/dto/OrderCreatedPayload.java:5`;
- `OrderStatusChangedPayload` — `src/main/java/com/vladko/autoshopnotification/event/dto/OrderStatusChangedPayload.java:5`;
- `OrderCompletedPayload` — `src/main/java/com/vladko/autoshopnotification/event/dto/OrderCompletedPayload.java:6`.

Это показывает, что проект отделяет транспортный контракт события от бизнес-логики построения письма.

## 5. Конфигурация приложения

Главные runtime-настройки собраны в `src/main/resources/application.properties`:

- порт приложения — `src/main/resources/application.properties:3`;
- подключение к PostgreSQL — `src/main/resources/application.properties:5`;
- параметры Kafka consumer/producer — `src/main/resources/application.properties:11`-`src/main/resources/application.properties:18`;
- список actuator endpoints — `src/main/resources/application.properties:29`-`src/main/resources/application.properties:30`;
- имя основного топика заказов — `src/main/resources/application.properties:32`;
- DLT-топик — `src/main/resources/application.properties:33`;
- выбор почтового провайдера — `src/main/resources/application.properties:39`;
- параметры Mailjet — `src/main/resources/application.properties:41`-`src/main/resources/application.properties:46`;
- параметры retry для email и Kafka — `src/main/resources/application.properties:48`-`src/main/resources/application.properties:51`.

Типизированная обёртка над конфигурацией реализована через `@ConfigurationProperties`:

- Kafka-параметры: `src/main/java/com/vladko/autoshopnotification/config/AppKafkaProperties.java:5`;
- mail-provider и отправитель: `src/main/java/com/vladko/autoshopnotification/config/AppMailProperties.java:5`;
- параметры Mailjet: `src/main/java/com/vladko/autoshopnotification/config/AppMailjetProperties.java:8`;
- retry-настройки: `src/main/java/com/vladko/autoshopnotification/config/AppRetryProperties.java:7`.

Отдельно важно, что `AutoshopNotificationApplication` включает `@ConfigurationPropertiesScan`, что позволяет автоматически регистрировать record-конфигурации: `src/main/java/com/vladko/autoshopnotification/AutoshopNotificationApplication.java:7`.

## 6. Структура пакетов и ответственность слоёв

### 6.1 Корневой пакет

Корневой пакет приложения — `com.vladko.autoshopnotification`.

Его можно разделить на следующие логические подсистемы:

- `config` — инфраструктурная конфигурация;
- `event` — входной слой потребления событий;
- `event.dto` — транспортные модели;
- `notification` — основная доменная логика обработки уведомлений;
- `template` — шаблоны и рендеринг уведомлений;
- `email` — абстракция и реализации почтовой отправки;
- `retry` — классификация ошибок и политика повторов.

### 6.2 Пакет `config`

Пакет `src/main/java/com/vladko/autoshopnotification/config` отвечает за внешнюю инфраструктуру и создание spring-beans.

Ключевой класс — `KafkaConsumerConfig` (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:19`). Он:

- создаёт `ConcurrentKafkaListenerContainerFactory` (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:23`);
- задаёт `AckMode.RECORD`, то есть подтверждение записи на уровне одного сообщения (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:33`);
- настраивает `DefaultErrorHandler` (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:54`);
- публикует `NewTopic` для основного топика и DLT (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:39`, `src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:47`);
- объявляет `NonRetryableNotificationException` как исключение, которое не должно ретраиться (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:68`).

Это означает, что в проекте продуман не только happy-path, но и контролируемое поведение при технических и бизнес-ошибках.

### 6.3 Пакет `event`

`NotificationEventConsumer` является входной точкой событийной обработки (`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:12`).

Он:

- подписан на Kafka-топик через `@KafkaListener` (`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:29`);
- получает сырую строку JSON и десериализует её в `NotificationEventEnvelope` (`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:31`, `src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:37`);
- при невалидном JSON выбрасывает `NonRetryableNotificationException`, что предотвращает бессмысленные повторы (`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:41`);
- передаёт событие в `NotificationProcessingService` вместе с метаданными Kafka-записи (`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:34`).

Дополнительная record-модель `EventMetadata` используется для сохранения технической трассировки: топик, partition, offset.

### 6.4 Пакет `notification`

Это центральный доменный слой проекта. Здесь описаны:

- JPA-сущности уведомления и обработки;
- перечисления статусов;
- репозитории;
- основной orchestration-service.

Ключевой класс — `NotificationProcessingService` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:32`). Именно в нём сосредоточен полный жизненный цикл одного события.

### 6.5 Пакет `template`

Пакет отвечает за преобразование события в готовое письмо.

В него входят:

- `TemplateKeyResolver` — сопоставляет тип события и ключ шаблона (`src/main/java/com/vladko/autoshopnotification/template/service/TemplateKeyResolver.java:9`);
- `NotificationTemplateService` — получает шаблон, подготавливает переменные, рендерит HTML и subject (`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:51`);
- `StatusLabelMapper` — переводит внутренние статусы заказа в человекочитаемые подписи на русском языке (`src/main/java/com/vladko/autoshopnotification/template/service/StatusLabelMapper.java:6`).

### 6.6 Пакет `email`

Здесь находится стратегия отправки письма.

- `EmailSender` — общий контракт;
- `SmtpEmailSender` — базовая SMTP-реализация (`src/main/java/com/vladko/autoshopnotification/email/SmtpEmailSender.java:16`);
- `MailjetEmailSender` — провайдерная HTTP-реализация для Mailjet (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:22`);
- `EmailMessage` — внутренняя модель подготовленного письма;
- `EmailSendResult` — результат попытки отправки, включая провайдерские идентификаторы;
- `MailjetEmailException` — специализированная ошибка для классификации retryability.

### 6.7 Пакет `retry`

Подсистема retry отделена в отдельный пакет, что архитектурно удачно: правила повторов не «размазаны» по сервису.

Здесь определены:

- базовое исключение `NotificationProcessingException`;
- `RetryableNotificationException`;
- `NonRetryableNotificationException`;
- `RetryClassifier`, который определяет, нужно ли повторять почтовую ошибку (`src/main/java/com/vladko/autoshopnotification/retry/RetryClassifier.java:11`).

## 7. Подробный жизненный цикл обработки события

Ниже приведён фактический pipeline обработки одного сообщения, построенный по коду `NotificationProcessingService`.

### Шаг 1. Получение сообщения из Kafka

Kafka Listener получает запись и передаёт её в бизнес-обработку: `src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:29`-`src/main/java/com/vladko/autoshopnotification/event/NotificationEventConsumer.java:34`.

### Шаг 2. Валидация envelope

Метод `process(...)` начинается с `validateEnvelope(envelope)` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:61`-`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:63`).

Из найденных проверок видно, что валидируются:

- наличие `eventId`;
- наличие `eventType`;
- наличие `source`;
- версия контракта — поддерживается только `version = 1` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:35`, `src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:200`).

Это важно для устойчивости интеграции: сервис явно фиксирует поддерживаемую версию схемы событий.

### Шаг 3. Идемпотентная регистрация события во inbox

После валидации вызывается `getOrCreateInbox(...)` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:63`, `src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:168`).

Назначение inbox-таблицы:

- зарегистрировать факт получения события;
- привязать к нему Kafka metadata;
- избежать повторной полной обработки при редоставке одного и того же `eventId`.

Идея подтверждается и описанием в `README.md:108`-`README.md:110`.

### Шаг 4. Проверка, не было ли событие уже обработано

Если inbox уже находится в статусе `PROCESSED`, обработка прерывается с логированием (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:64`-`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:68`).

Это первый защитный контур от дублей.

### Шаг 5. Перевод inbox в состояние обработки

Inbox помечается как `PROCESSING`, после чего сохраняется в БД (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:70`-`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:71`).

### Шаг 6. Поиск уже существующего уведомления

Далее сервис ищет `NotificationEntity` по паре `eventId + channel` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:75`).

Если письмо уже было ранее отправлено и имеет статус `SENT`, сервис повторно его не шлёт и завершает обработку (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:76`-`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:82`).

Это второй защитный контур от дублей.

### Шаг 7. Формирование контента уведомления

Вызов `templateService.render(envelope)` создаёт готовое представление письма (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:84`).

В `NotificationTemplateService` процесс устроен так:

1. Определяется `templateKey` по `eventType` (`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:51`-`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:52`);
2. Из БД выбирается активный шаблон по ключу и каналу (`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:54`);
3. Подготавливается набор переменных;
4. Переменные помещаются в Thymeleaf context (`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:62`);
5. HTML рендерится через `templateEngine.process(...)` (`src/main/java/com/vladko/autoshopnotification/template/service/NotificationTemplateService.java:63`).

Таким образом, шаблон письма не захардкожен в сервисной логике, а разделён на:

- данные события;
- БД-метаданные шаблона;
- HTML-файл из ресурсов.

### Шаг 8. Создание или переиспользование сущности уведомления

Если уведомление по `eventId + EMAIL` ещё не существует, создаётся `NotificationEntity` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:85`, `src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:115`).

Это сущность-агрегат для хранения:

- типа события;
- канала доставки;
- получателя;
- темы письма;
- ключа шаблона;
- текущего статуса;
- времени создания/обновления/отправки;
- последней ошибки;
- с Mailjet-версии — провайдерских идентификаторов.

### Шаг 9. Отправка письма

Сервис формирует `EmailMessage` и передаёт его через `EmailSender`, не завязываясь на конкретную реализацию. Это хороший пример паттерна Strategy.

SMTP-отправка выполняется через `JavaMailSender` в `SmtpEmailSender.send(...)` (`src/main/java/com/vladko/autoshopnotification/email/SmtpEmailSender.java:27`-`src/main/java/com/vladko/autoshopnotification/email/SmtpEmailSender.java:39`).

Mailjet-отправка выполняется через HTTP API в `MailjetEmailSender.send(...)` (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:47`-`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:68`).

Особенности Mailjet-реализации:

- выполняется предварительная валидация конфигурации (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:48`, `src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:140`);
- в JSON-запросе используется `SandboxMode` (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:162`);
- `eventId` передаётся как `CustomID` (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:173`), что также описано в `README.md:51`;
- HTTP-ошибки классифицируются на retryable/non-retryable по статус-коду (`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:130`-`src/main/java/com/vladko/autoshopnotification/email/MailjetEmailSender.java:137`).

### Шаг 10. Фиксация попытки доставки

После отправки сервис сохраняет отдельную запись о попытке доставки. Наличие сущности `NotificationDeliveryAttemptEntity` и репозитория `NotificationDeliveryAttemptRepository` показывает, что каждая попытка — отдельный аудитируемый объект, а не просто поле в основном уведомлении.

Это позволяет хранить:

- номер попытки;
- итог попытки;
- текст ошибки;
- провайдера;
- временные метки.

### Шаг 11. Обновление статусов уведомления и inbox

При успехе уведомление помечается как отправленное (`markSent(...)`), а inbox как `PROCESSED` (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:91`).

При ошибке сервис разделяет два сценария:

- retryable failure — событие можно переобработать позже;
- non-retryable failure — дальнейшие попытки бессмысленны.

В обоих случаях фиксируются статусы и текст ошибок (`src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:148`, `src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:211`, `src/main/java/com/vladko/autoshopnotification/notification/service/NotificationProcessingService.java:224`).

## 8. Модель данных и база данных

### 8.1 Источник схемы

Схема БД управляется через Liquibase, master-файл находится в `src/main/resources/db/changelog/db.changelog-master.yaml:1`.

В него включены две миграции:

- базовая схема уведомлений — `db.changelog-1.0-notifications.sql`;
- расширение под Mailjet — `db.changelog-1.1-mailjet.sql`.

### 8.2 Таблица `notification`

Создаётся в `src/main/resources/db/changelog/db.changelog-1.0-notifications.sql:4`.

Назначение таблицы:

- хранить одну основную запись об уведомлении;
- отражать его жизненный цикл;
- служить опорной таблицей для аудита отправок.

Ключевые поля:

- `event_id` — идентификатор входного события;
- `event_type` — тип бизнес-события;
- `source` — источник события;
- `channel` — канал уведомления;
- `recipient` — адрес получателя;
- `subject` — тема письма;
- `template_key` — использованный шаблон;
- `status` — статус уведомления;
- `error_message` — текст последней ошибки;
- `created_at`, `updated_at`, `sent_at` — временные метки.

На таблице установлен уникальный constraint `uk_notification_event_channel`, который не позволяет создать два одинаковых уведомления по одной паре `event_id + channel` (`src/main/resources/db/changelog/db.changelog-1.0-notifications.sql:18`).

### 8.3 Таблица `notification_delivery_attempt`

Создаётся в `src/main/resources/db/changelog/db.changelog-1.0-notifications.sql:21`.

Смысл таблицы — хранить каждую попытку отправки как самостоятельный факт. Это особенно важно для дипломного обоснования отказоустойчивости, потому что позволяет показывать не только итоговый статус, но и полную историю ретраев.

### 8.4 Таблица `notification_event_inbox`

Создаётся в `src/main/resources/db/changelog/db.changelog-1.0-notifications.sql:32`.

Это технически очень важная таблица, так как именно она реализует inbox-pattern и идемпотентность на уровне события. Она нужна, чтобы повторная доставка Kafka-сообщения не приводила к повторной отправке письма.

### 8.5 Таблица `notification_template`

Создаётся в `src/main/resources/db/changelog/db.changelog-1.0-notifications.sql:45`.

Эта таблица отделяет бизнес-справочник шаблонов от кода. За счёт неё можно изменять subject, путь к body template и активность шаблона без изменения Java-логики.

### 8.6 Mailjet-расширение схемы

Миграция `src/main/resources/db/changelog/db.changelog-1.1-mailjet.sql:4`-`src/main/resources/db/changelog/db.changelog-1.1-mailjet.sql:20` добавляет поля:

- `provider_name`;
- `provider_message_id` (`src/main/resources/db/changelog/db.changelog-1.1-mailjet.sql:8`);
- `provider_message_uuid` (`src/main/resources/db/changelog/db.changelog-1.1-mailjet.sql:11`);
- `provider_message_href` (`src/main/resources/db/changelog/db.changelog-1.1-mailjet.sql:14`).

Также создаются индексы для provider message identifiers, что подтверждает ориентацию проекта на последующую трассировку провайдерских событий.

## 9. Шаблоны email и генерация контента

HTML-шаблоны расположены в `src/main/resources/templates/email/`:

- `order-created.html` — письмо о создании заказа (`src/main/resources/templates/email/order-created.html:1`);
- `order-status-changed.html` — письмо об изменении статуса (`src/main/resources/templates/email/order-status-changed.html:1`);
- `order-completed.html` — письмо о завершении заказа (`src/main/resources/templates/email/order-completed.html:1`).

По содержанию шаблонов видно, что проект ориентирован на русскоязычный пользовательский интерфейс уведомлений:

- обращения к клиенту;
- форматирование дат;
- подписи статусов на русском языке;
- отображение данных заказа и автомобиля.

Дополнительная важная деталь: `StatusLabelMapper` переводит технические статусы `NEW`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` в человекочитаемые строки (`src/main/java/com/vladko/autoshopnotification/template/service/StatusLabelMapper.java:8`).

## 10. Подсистема отправки email

### 10.1 Абстракция

Контракт `EmailSender` позволяет сервису обработки уведомлений не знать, какой именно транспорт используется. Это даёт гибкость для замены SMTP на внешний API без переписывания доменной логики.

### 10.2 SMTP-реализация

`SmtpEmailSender` использует `JavaMailSender` (`src/main/java/com/vladko/autoshopnotification/email/SmtpEmailSender.java:10`) и возвращает `EmailSendResult.accepted(...)` после передачи письма почтовому клиенту (`src/main/java/com/vladko/autoshopnotification/email/SmtpEmailSender.java:38`).

Это простая и логичная стартовая реализация для локального окружения с Mailhog.

### 10.3 Mailjet-реализация

`MailjetEmailSender` — более зрелая интеграция с внешним провайдером.

Из кода видно, что она включает:

- работу через `RestClient`;
- basic auth по API key / secret;
- настройку таймаутов;
- sandbox-mode;
- сохранение provider identifiers;
- классификацию ошибок по HTTP-ответам.

Это говорит о переходе от «учебной» SMTP-отправки к более промышленной интеграции с внешним email-провайдером.

## 11. Отказоустойчивость, ретраи и DLT

Проект очень хорошо демонстрирует паттерны надёжной событийной обработки.

### 11.1 Retry на уровне Kafka listener

`KafkaConsumerConfig` настраивает `DefaultErrorHandler` (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:54`) и использует отдельный DLT (`src/main/java/com/vladko/autoshopnotification/config/KafkaConsumerConfig.java:61`).

Это означает:

- временные ошибки обрабатываются повторно;
- безнадёжные записи не блокируют весь consumer;
- сообщение после исчерпания попыток уходит в специальный dead-letter topic.

### 11.2 Retry на уровне email-ошибок

`RetryClassifier` (`src/main/java/com/vladko/autoshopnotification/retry/RetryClassifier.java:11`) отделяет технические сбои от логических/валидационных. Например:

- `MailAuthenticationException` — не retryable;
- ошибки подготовки письма — не retryable;
- часть остальных `MailException` — retryable;
- `MailjetEmailException` делегирует решение на флаг `isRetryable()`.

### 11.3 Идемпотентность

Идемпотентность обеспечивается сразу на нескольких уровнях:

- уникальность `eventId` в inbox-слое;
- уникальность пары `eventId + channel` в notification-слое;
- проверка `NotificationStatus.SENT` перед повторной отправкой.

Это один из самых сильных архитектурных аспектов проекта.

## 12. Тестовая стратегия проекта

Тесты покрывают как unit-логику, так и интеграционные сценарии.

Ключевые тестовые классы:

- `src/test/java/com/vladko/autoshopnotification/template/TemplateKeyResolverTest.java:10` — проверка маппинга eventType → templateKey;
- `src/test/java/com/vladko/autoshopnotification/template/StatusLabelMapperTest.java:8` — проверка локализации статусов;
- `src/test/java/com/vladko/autoshopnotification/template/NotificationTemplateServiceTest.java:19` — интеграционная проверка рендеринга шаблонов;
- `src/test/java/com/vladko/autoshopnotification/notification/NotificationProcessingServiceTest.java:37` — проверка доменной логики обработки уведомлений;
- `src/test/java/com/vladko/autoshopnotification/notification/NotificationKafkaConsumerIntegrationTest.java:38` — сквозной интеграционный тест Kafka + БД + обработка;
- `src/test/java/com/vladko/autoshopnotification/email/MailjetEmailSenderTest.java:27` — проверка Mailjet payload и классификации ошибок.

Особенно важен тест на дедупликацию: `duplicateEventIdDoesNotSendSecondEmail()` в `src/test/java/com/vladko/autoshopnotification/notification/NotificationKafkaConsumerIntegrationTest.java:134`. Он подтверждает, что идемпотентность не декларативна, а реально проверяется тестами.

## 13. Фактическая файловая структура проекта

Ниже приведена укрупнённая структура с пояснением назначения.

```text
autoshop-notification/
├── build.gradle                        # сборка и зависимости
├── settings.gradle                     # имя/настройки Gradle-проекта
├── README.md                           # описание сервиса и локального запуска
├── docs/
│   └── mailjet-integration-plan.md     # проектный план интеграции Mailjet
├── gradle/wrapper/                     # Gradle Wrapper
├── src/main/java/com/vladko/autoshopnotification/
│   ├── AutoshopNotificationApplication.java
│   ├── config/                         # конфигурация Kafka, retry, mail properties
│   ├── email/                          # email abstraction + SMTP/Mailjet
│   ├── event/                          # Kafka consumer
│   │   └── dto/                        # transport records для событий
│   ├── notification/                   # сущности, статусы, репозитории, orchestration
│   │   ├── entity/
│   │   ├── repository/
│   │   └── service/
│   ├── retry/                          # retry/non-retryable исключения и классификатор
│   └── template/                       # шаблоны уведомлений и рендеринг
│       ├── entity/
│       ├── repository/
│       └── service/
├── src/main/resources/
│   ├── application.properties          # основная конфигурация
│   ├── application-local.properties    # локальное окружение
│   ├── db/changelog/                   # Liquibase migrations
│   └── templates/email/                # HTML-шаблоны писем
└── src/test/
    ├── java/                           # unit + integration tests
    └── resources/                      # test properties
```

## 14. Примерный ход реализации проекта по git-истории и логике развития

Git-история проекта короткая, но очень показательная:

- `f0a3db6` — `Initial commit!`;
- `3a8f4ed` — `Add notification`;
- `d6a69c1` — `Add MailJet`.

На основе состава коммитов и структуры кода можно достаточно уверенно восстановить последовательность разработки.

### Этап 0. Создание каркаса сервиса

Вероятнее всего, сначала был создан базовый Spring Boot проект с Gradle Wrapper, `build.gradle`, `settings.gradle`, главным классом приложения и стандартным тестом запуска. Это соответствует начальному коммиту `f0a3db6` и базовым файлам текущего репозитория.

### Этап 1. Реализация основной идеи notification-сервиса

Коммит `3a8f4ed` (`Add notification`) добавляет почти весь базовый функционал сразу. По составу файлов можно реконструировать такой ход работ:

1. **Формирование архитектурного каркаса**
   - создан главный пакет сервиса;
   - подключены зависимости Spring Kafka, JPA, Mail, Thymeleaf, Liquibase;
   - заведён README с описанием сценария запуска.

2. **Проектирование контракта входных событий**
   - введён `NotificationEventEnvelope`;
   - добавлены payload record’ы для трёх сценариев;
   - зафиксирована версия контракта и требования к `eventId`.

3. **Настройка Kafka-consumer’а**
   - создан `NotificationEventConsumer`;
   - создан `KafkaConsumerConfig`;
   - добавлены основной topic и DLT;
   - настроен `DefaultErrorHandler`.

4. **Проектирование схемы БД и идемпотентности**
   - создана миграция `db.changelog-1.0-notifications.sql`;
   - введены таблицы `notification`, `notification_delivery_attempt`, `notification_event_inbox`, `notification_template`;
   - заложены уникальные ограничения для борьбы с дублями.

5. **Разработка доменной модели обработки**
   - созданы JPA-сущности и перечисления статусов;
   - введены репозитории;
   - написан `NotificationProcessingService` как orchestration-центр.

6. **Реализация шаблонного рендеринга**
   - создан `TemplateKeyResolver`;
   - реализован `NotificationTemplateService`;
   - добавлены HTML-шаблоны для трёх событий.

7. **Подключение SMTP-отправки**
   - добавлен `EmailSender`;
   - реализован `SmtpEmailSender`;
   - для локальной отладки выбран Mailhog.

8. **Добавление тестов**
   - покрыты шаблоны, мапперы, сервис обработки;
   - добавлен интеграционный Kafka-тест на идемпотентность и поток обработки.

По логике это очень цельный коммит: сначала была собрана минимально полноценная версия микросервиса, уже пригодная для локального запуска и демонстрации ключевых архитектурных решений.

### Этап 2. Переход от базовой SMTP-отправки к провайдерной интеграции Mailjet

Коммит `d6a69c1` (`Add MailJet`) показывает второй крупный этап зрелости проекта.

По списку файлов и коду можно восстановить такую последовательность:

1. **Подготовка проектного плана**
   - создан `docs/mailjet-integration-plan.md`, то есть интеграция сначала была продумана концептуально.

2. **Расширение конфигурации**
   - добавлен `AppMailjetProperties`;
   - расширены `application.properties` и local/example-конфиги.

3. **Унификация контракта отправки**
   - `EmailSendResult` стал возвращать не только факт отправки, но и провайдерские идентификаторы;
   - `EmailSender` был адаптирован под новый результат.

4. **Реализация HTTP-провайдера Mailjet**
   - создан `MailjetEmailSender`;
   - добавлена собственная ошибка `MailjetEmailException`;
   - реализована классификация HTTP-ошибок на retryable и non-retryable.

5. **Расширение доменной модели уведомления**
   - в `NotificationEntity` и схеме БД добавлены поля `provider_name`, `provider_message_id`, `provider_message_uuid`, `provider_message_href`;
   - это позволило связать внутреннюю запись уведомления с ответом внешнего почтового провайдера.

6. **Корректировка общей логики обработки**
   - `NotificationProcessingService` доработан так, чтобы сохранять провайдерские метаданные после успешной отправки.

7. **Добавление специализированных тестов**
   - `MailjetEmailSenderTest` проверяет структуру HTTP-запроса, `SandboxMode`, `CustomID`, а также retry-семантику HTTP 429 и 400.

С точки зрения дипломного описания это важный момент: проект не остановился на локальной SMTP-демонстрации, а был развит в сторону реальной промышленной интеграции с SaaS-провайдером email-рассылки.

## 15. Архитектурные достоинства проекта

Если описывать сильные стороны именно этого проекта, то стоит выделить следующие:

1. **Выделение уведомлений в отдельный микросервис**
   - улучшает масштабируемость;
   - снижает связанность с core-сервисом.

2. **Событийная интеграция через Kafka**
   - позволяет асинхронно реагировать на бизнес-события;
   - упрощает расширение на новые типы уведомлений.

3. **Идемпотентность и inbox-pattern**
   - защищают от повторной отправки писем;
   - критически важны в распределённых системах.

4. **Комбинация retry + DLT**
   - повышает отказоустойчивость;
   - предотвращает зависание consumer’а на «ядовитом» сообщении.

5. **Отделение шаблонов от кода**
   - упрощает сопровождение;
   - позволяет эволюционировать контент писем независимо от бизнес-логики.

6. **Provider abstraction для email**
   - делает архитектуру расширяемой;
   - позволяет использовать SMTP, Mailjet и потенциально другие провайдеры.

7. **Хорошая наблюдаемость и аудит**
   - хранится inbox;
   - хранится notification;
   - хранится история delivery attempts;
   - сохраняются provider message identifiers.

## 16. Возможные направления дальнейшего развития

На основе текущей структуры можно предложить естественные следующие шаги:

- добавить webhook-обработку событий Mailjet (bounce/open/click/spam/unsub), что уже концептуально намечено в `docs/mailjet-integration-plan.md`;
- расширить каналы уведомлений, например SMS или push, используя уже существующий `NotificationChannel`;
- вынести шаблоны целиком в БД или административный интерфейс;
- добавить outbox/integration events для обратной передачи фактов доставки в другие сервисы;
- ввести более детальную метрику и мониторинг попыток доставки;
- добавить поддержку нескольких версий event contract.

## 17. Итоговый вывод для диплома

Проект `autoshop-notification` представляет собой хорошо структурированный микросервис обработки уведомлений, построенный на современных enterprise-подходах Spring Boot экосистемы. В нём реализованы не только базовые функции отправки email, но и важные для промышленной эксплуатации механизмы:

- событийная интеграция через Kafka;
- идемпотентная обработка сообщений;
- отказоустойчивость за счёт retry и DLT;
- хранение истории обработки и доставки;
- шаблонная генерация контента;
- абстрагирование почтового транспорта;
- расширение до реального внешнего провайдера Mailjet.

По git-истории видно, что разработка велась последовательно: сначала был собран полнофункциональный notification-сервис с Kafka, БД, шаблонами и SMTP, после чего он был эволюционно расширен интеграцией Mailjet. Такой путь реализации выглядит логичным, инженерно обоснованным и хорошо подходит для описания в дипломной работе как пример разработки прикладного отказоустойчивого микросервиса уведомлений.
