# План интеграции Mailjet

Дата анализа: 2026-04-22.

## Источники

- [Mailjet Email API Guides](https://dev.mailjet.com/email/guides/#event-api-real-time-notifications) - исходный документ, который нужно учесть.
- [Mailjet API documentation repository](https://github.com/mailjet/api-documentation) - markdown-исходники гайдов, включая Event API и Send API.
- [Event API: real-time notifications](https://raw.githubusercontent.com/mailjet/api-documentation/master/guides/_event-api.md) - настройка webhook endpoint, формат событий и рекомендации по обработке.
- [Send transactional email](https://raw.githubusercontent.com/mailjet/api-documentation/master/guides/_send-api.md) - Send API v3.1, sender requirements, SandboxMode, CustomID и ответ API.
- [Mailjet Event Tracking API Help Center](https://documentation.mailjet.com/hc/en-us/articles/360043578154-Event-Tracking-API) - подтверждение retry-поведения и payload событий.

## Краткий вывод

Интеграцию лучше делать через Mailjet Send API v3.1, а не через SMTP. В текущем сервисе уже есть удачная точка расширения: `EmailSender` и одна реализация `SmtpEmailSender`. Значит, можно добавить `MailjetEmailSender`, переключаемый конфигурацией, не трогая Kafka consumer, рендеринг шаблонов и базовую идемпотентность по `eventId`.

Без домена сайта можно подготовить почти всю интеграцию: конфигурацию, клиент, sandbox-валидацию payload, сохранение provider-id, webhook endpoint и тесты. Для реальной доставки Mailjet все равно потребует verified sender address или домен отправителя, а для production webhooks понадобится публичный HTTPS URL.

## Что важно из API Mailjet

### Отправка писем

- Рекомендуемый endpoint: `POST https://api.mailjet.com/v3.1/send`.
- Авторизация: HTTP Basic Auth, где username - public API key, password - private API key.
- Payload верхнего уровня начинается с массива `Messages`.
- Минимальный message:
  - `From`: `{ "Email": "...", "Name": "..." }`, email должен быть validated/active sender.
  - `To`: массив получателей `{ "Email": "...", "Name": "..." }`.
  - `Subject`.
  - `TextPart` и/или `HTMLPart`, либо `TemplateID`.
- `SandboxMode=true` валидирует payload без реальной отправки. Это главный режим до появления домена/verified sender.
- Ответ содержит `Messages[].Status` и для каждого получателя `MessageUUID`, `MessageID`, `MessageHref`.
- `CustomID` нужен для нашей корреляции: в него стоит класть `notification.eventId` или внутренний notification id. По `CustomID` затем можно искать message в Mailjet и связывать webhooks с нашей записью.
- `EventPayload` можно использовать для компактного контекста, например `eventType=ORDER_CREATED;template=ORDER_CREATED_EMAIL`.

### Event API webhooks

- Callback настраивается через `POST https://api.mailjet.com/v3/REST/eventcallbackurl`.
- Основные поля регистрации: `EventType`, `Url`, `Version`.
- `Version=2` включает grouped events: Mailjet присылает JSON array, события за одну секунду могут быть смешаны по типам.
- Поддерживаемые события: `sent`, `open`, `click`, `bounce`, `blocked`, `spam`, `unsub`.
- Webhook должен быстро вернуть HTTP `200 OK`; не-200 приводит к retry со стороны Mailjet.
- В payload общие поля: `event`, `time`, `email`, `mj_campaign_id`, `mj_contact_id`, `customcampaign`, `MessageID`, `Message_GUID`, `CustomID`, `Payload`.
- Дополнительные поля:
  - `sent`: `smtp_reply`.
  - `open`: `ip`, `geo`, `agent`.
  - `click`: `url`, `ip`, `geo`, `agent`.
  - `bounce`: `blocked`, `hard_bounce`, `error_related_to`, `error`, `comment`.
  - `blocked`: `error_related_to`, `error`.
  - `spam`: `source`.
  - `unsub`: `mj_list_id`, `ip`, `geo`, `agent`.
- Рекомендация Mailjet: webhook не должен ходить в другие API или долго работать синхронно. Нужно принять payload, сохранить и обработать асинхронно.
- Для безопасности Mailjet рекомендует HTTPS и basic auth в callback URL.

## Текущее состояние микросервиса

- Spring Boot 3.5, Java 17.
- Сейчас email уходит через `SmtpEmailSender`, который реализует `EmailSender`.
- Настройки отправителя лежат в `AppMailProperties`: `app.mail.from`, `app.mail.from-name`.
- `NotificationProcessingService` уже делает:
  - валидацию envelope;
  - inbox идемпотентность по `eventId`;
  - рендер HTML через `NotificationTemplateService`;
  - запись `notification`;
  - retry отправки;
  - запись `notification_delivery_attempt`.
- В `notification_delivery_attempt.provider` сейчас жестко записывается `SMTP`.
- В `NotificationStatus` есть только `PENDING`, `SENDING`, `SENT`, `FAILED`; этого достаточно для статуса обработки, но недостаточно для Mailjet delivery lifecycle.

## Целевая архитектура

### 1. Provider selection

Добавить конфигурационный переключатель:

```properties
app.mail.provider=${APP_MAIL_PROVIDER:smtp}
app.mailjet.enabled=${APP_MAILJET_ENABLED:false}
app.mailjet.api-key=${MAILJET_API_KEY:}
app.mailjet.api-secret=${MAILJET_API_SECRET:}
app.mailjet.send-url=${MAILJET_SEND_URL:https://api.mailjet.com/v3.1/send}
app.mailjet.sandbox-mode=${MAILJET_SANDBOX_MODE:true}
app.mailjet.connect-timeout=${MAILJET_CONNECT_TIMEOUT:2s}
app.mailjet.read-timeout=${MAILJET_READ_TIMEOUT:5s}
```

Реализации:

- `SmtpEmailSender` активен при `app.mail.provider=smtp`.
- `MailjetEmailSender` активен при `app.mail.provider=mailjet`.

Для `NotificationProcessingService` лучше добавить метод `providerName()` в `EmailSender`, чтобы attempt provider был `SMTP` или `MAILJET` без hardcode.

### 2. Mailjet REST client

Сделать `MailjetEmailSender` поверх Spring `RestClient` или `WebClient`. Новая внешняя библиотека не обязательна, потому что `spring-boot-starter-web` уже есть в проекте.

Плюсы `RestClient`:

- меньше зависимостей;
- проще мокать HTTP в тестах;
- payload полностью контролируется нашим кодом;
- не зависим от версии официального SDK.

Payload для текущих HTML-шаблонов:

```json
{
  "SandboxMode": true,
  "Messages": [
    {
      "From": { "Email": "noreply@example.com", "Name": "AutoShop" },
      "To": [{ "Email": "ivan@example.com" }],
      "Subject": "AutoShop: заказ AS-2026-00042 создан",
      "HTMLPart": "<html>...</html>",
      "CustomID": "8f2cb0f6-41f0-4b79-a4d8-73d0d862fa33",
      "EventPayload": "eventType=ORDER_CREATED;template=ORDER_CREATED_EMAIL"
    }
  ]
}
```

Нужно расширить `EmailMessage` или ввести `EmailSendContext`, потому что для `CustomID` нужен `eventId`, которого сейчас в `EmailMessage` нет.

### 3. Ошибки и retry

Сейчас retry завязан на `MailException`. Для Mailjet лучше ввести provider-neutral исключения:

- `EmailSendException extends RuntimeException` с полем `retryable`.
- `EmailAuthenticationException` или `retryable=false` для 401/403.
- `EmailValidationException` или `retryable=false` для 400 и ошибок payload.
- `EmailTemporaryException` или `retryable=true` для 429, 5xx, timeout, connection reset.

Переходный вариант: `MailjetEmailSender` может бросать подходящие наследники `MailException`, чтобы меньше менять `NotificationProcessingService`. Но чистее вынести `RetryClassifier` с `MailException` на общий `EmailSendException`.

Обязательно проверять не только HTTP status, но и `Messages[].Status`. Если HTTP 200, но конкретное message вернуло `Status=error`, это failed attempt.

### 4. Сохранение идентификаторов Mailjet

Добавить миграцию Liquibase для полей:

```sql
ALTER TABLE notification
    ADD COLUMN provider VARCHAR(60),
    ADD COLUMN provider_message_id VARCHAR(80),
    ADD COLUMN provider_message_uuid VARCHAR(120),
    ADD COLUMN provider_message_href TEXT,
    ADD COLUMN provider_accepted_at TIMESTAMP;
```

Альтернатива с меньшим влиянием на `notification`: добавить отдельную таблицу `notification_provider_message`. Но для текущего сервиса один email на один notification, поэтому поля в `notification` проще.

После успешного ответа Mailjet:

- сохранять `MessageID` как `provider_message_id`;
- сохранять `MessageUUID` как `provider_message_uuid`;
- сохранять `MessageHref`;
- `NotificationStatus.SENT` трактовать как "accepted by provider", не как фактическую доставку в inbox получателя.

### 5. Webhook endpoint

Добавить endpoint:

```http
POST /api/mailjet/events
Authorization: Basic ...
Content-Type: application/json
```

Он должен:

1. Проверить basic auth или shared secret.
2. Принять JSON array событий.
3. Быстро сохранить raw events в БД.
4. Вернуть `200 OK`.
5. Обрабатывать события асинхронно отдельным service/job.

Таблица:

```sql
CREATE TABLE mailjet_event (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    email VARCHAR(255),
    message_id VARCHAR(80),
    message_guid VARCHAR(120),
    custom_id VARCHAR(120),
    payload TEXT,
    raw_payload JSONB NOT NULL,
    processing_status VARCHAR(40) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    error_message TEXT
);

CREATE INDEX ix_mailjet_event_custom_id ON mailjet_event (custom_id);
CREATE INDEX ix_mailjet_event_message_id ON mailjet_event (message_id);
CREATE INDEX ix_mailjet_event_status ON mailjet_event (processing_status);
```

Для дедупликации можно добавить hash по `event + time + MessageID + email + url/error`, потому что Mailjet event payload не содержит отдельного уникального event id.

Маппинг:

- `sent`: отметить delivery event как accepted/sent, сохранить `smtp_reply`.
- `open`: сохранить engagement event, не менять основной `NotificationStatus`.
- `click`: сохранить engagement event с `url`.
- `bounce`:
  - если `hard_bounce=true` или `blocked=true`, пометить notification как failed delivery или записать `delivery_status=BOUNCED`;
  - soft bounce оставить отдельным событием без немедленного `FAILED`.
- `blocked`: записать delivery failure; часть причин может быть временной или campaign-level, поэтому не все blocked равны permanent.
- `spam`: записать complaint; потенциально помечать email как suppressed в будущем сервисе контактов.
- `unsub`: записать unsubscribe; в будущем отправлять событие в core/customer service.

Не стоит превращать каждый `open/click` в изменение `NotificationStatus`: текущий статус описывает процесс отправки, а не аналитику поведения.

### 6. Регистрация webhook callback URLs

До появления production-домена:

- для локальной проверки использовать `ngrok` или `cloudflared tunnel`;
- в Mailjet callback URL указывать временный HTTPS URL с basic auth;
- `SandboxMode` не отправляет письма, поэтому webhooks для реальных событий появятся только при реальной отправке.

После появления домена:

```properties
app.mailjet.webhook.public-url=https://api.example.com/api/mailjet/events
app.mailjet.webhook.username=${MAILJET_WEBHOOK_USERNAME}
app.mailjet.webhook.password=${MAILJET_WEBHOOK_PASSWORD}
app.mailjet.webhook.auto-register=false
```

Добавить управляемый компонент `MailjetWebhookRegistrationService` или отдельный admin command, который для каждого события вызывает:

```http
POST https://api.mailjet.com/v3/REST/eventcallbackurl
{
  "EventType": "sent",
  "Url": "https://username:password@api.example.com/api/mailjet/events",
  "Version": 2
}
```

События для первого этапа: `sent`, `bounce`, `blocked`, `spam`, `unsub`. `open` и `click` можно включить позже, когда будет понятно, что аналитика действительно нужна.

## Пошаговый план работ

### Этап 0. Аккаунт и окружение

- Создать API key/secret в Mailjet.
- Проверить, можно ли подтвердить один sender email без домена. Если нельзя, работать только в `SandboxMode=true` до появления домена.
- После появления домена настроить SPF/DKIM через Mailjet.
- Добавить env vars в local/example config без реальных секретов.

### Этап 1. Конфигурация provider

- Расширить `AppMailProperties` или добавить `AppMailjetProperties`.
- Добавить conditional beans для `SmtpEmailSender` и `MailjetEmailSender`.
- Добавить `EmailSender.providerName()`.
- Обновить запись `notification_delivery_attempt.provider`.

### Этап 2. Send API v3.1

- Реализовать DTO для Mailjet request/response.
- Реализовать Basic Auth и timeout.
- Добавить `SandboxMode`.
- Добавить `CustomID` и `EventPayload`.
- Парсить `MessageID`, `MessageUUID`, `MessageHref`.
- Расширить БД/Entity для provider message ids.

### Этап 3. Ошибки

- Ввести общий тип email exceptions или адаптировать Mailjet ошибки к текущему `MailException`.
- Разделить retryable/non-retryable:
  - non-retryable: invalid request, authentication, unverified sender, malformed recipient.
  - retryable: timeout, network, 429, 5xx.
- Добавить тесты retry-классификации.

### Этап 4. Webhook приемник

- Добавить controller `MailjetEventController`.
- Добавить DTO с flexible fields, чтобы не падать на новых полях Mailjet.
- Добавить basic auth/shared secret validation.
- Сохранять raw payload в `mailjet_event`.
- Возвращать `200 OK` после успешного сохранения.
- Обработку вынести в отдельный service.

### Этап 5. Обработка событий

- Находить notification по `CustomID`, затем fallback по `MessageID`.
- Сохранять delivery/engagement факты отдельно от основного статуса.
- Для `hard_bounce`, `blocked`, `spam`, `unsub` подготовить доменные события на будущее, чтобы core-service мог обновлять настройки клиента.
- Сделать обработку идемпотентной.

### Этап 6. Регистрация callback URL

- Сделать ручную инструкцию curl для dev.
- После появления стабильного public URL добавить auto-register command, но выключить его по умолчанию.
- Не регистрировать callback на root URL: Mailjet требует конкретный путь.
- Использовать `Version=2`.
- Добавить опцию backup URL позже, когда будет второй публичный endpoint.

### Этап 7. Документация и эксплуатация

- Обновить `README.md`: Mailjet env vars, sandbox, verified sender, webhook setup.
- Добавить actuator/metrics:
  - счетчик отправок Mailjet success/error;
  - счетчик webhook events по типу;
  - retry counter;
  - latency Send API.
- Добавить redaction логов: не логировать API secret, full email body и basic auth webhook URL.

## Тестирование

- Unit tests:
  - mapping `EmailMessage` -> Mailjet JSON;
  - parsing successful response;
  - parsing per-message error;
  - retryable/non-retryable classification.
- Service tests:
  - `NotificationProcessingService` пишет provider `MAILJET`;
  - сохраняет provider message ids;
  - не ломает идемпотентность по `eventId`.
- Controller tests:
  - webhook принимает array с mixed events;
  - reject без auth;
  - возвращает `200 OK` после сохранения;
  - unknown event не валит endpoint.
- Optional integration test:
  - запускается только при `MAILJET_API_KEY`, `MAILJET_API_SECRET`, `MAILJET_INTEGRATION_TEST=true`;
  - сначала `SandboxMode=true`;
  - реальная отправка только отдельным ручным профилем.

## Риски и решения

- Нет домена: использовать `SandboxMode=true`, Mailhog оставить fallback, реальную доставку включить после verified sender/domain.
- Нет публичного URL для webhook: использовать tunnel в dev, production включить после домена.
- Mailjet grouped events: endpoint должен принимать массив и mixed event types.
- Повторная доставка webhook: сделать dedupe по hash и всегда быстро возвращать 200 после сохранения.
- PII в событиях: email, IP, user-agent и URL кликов хранить осознанно; не писать raw payload в обычные application logs.
- `NotificationStatus.SENT` может быть неправильно понят как delivered: в документации и коде трактовать его как "передано провайдеру"; delivery lifecycle хранить отдельно.

## Минимальный MVP

1. `app.mail.provider=mailjet`.
2. `MailjetEmailSender` через `POST /v3.1/send`.
3. `SandboxMode=true` по умолчанию.
4. `CustomID=eventId`.
5. Сохранение `MessageID`/`MessageUUID`.
6. Unit/service tests.
7. README с инструкцией, как включить реальную отправку после verified sender/domain.

Webhook часть можно делать вторым PR, но DTO и `CustomID` лучше заложить сразу, чтобы потом не менять контракт отправки.
