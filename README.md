# AutoShop NotificationService

Separate notification microservice for AutoShop. It consumes order events from Kafka, renders email templates, sends email through SMTP, stores delivery state, and protects email delivery with retry, DLQ, and idempotency by `eventId`.

## Run Locally

Infrastructure is shared with the AutoShop local compose:

```bash
docker compose --profile messaging up -d postgres kafka mailhog
```

Create the notification database if it does not exist yet:

```sql
CREATE DATABASE notifications_db;
```

Start the service:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Mailhog

```text
SMTP: localhost:1025
UI: http://localhost:8025
```

## Kafka

```text
Topic: autoshop.order-events
DLT: autoshop.order-events.dlt
Consumer group: notification-service
```

## Supported Events

- `ORDER_CREATED`
- `ORDER_STATUS_CHANGED`
- `ORDER_COMPLETED`

Each event must use the common envelope:

```json
{
  "eventId": "8f2cb0f6-41f0-4b79-a4d8-73d0d862fa33",
  "eventType": "ORDER_CREATED",
  "occurredAt": "2026-04-19T10:15:30Z",
  "source": "autoshop-core",
  "version": 1,
  "correlationId": "order-42-created",
  "payload": {
    "orderId": 42,
    "orderNumber": "AS-2026-00042",
    "customerId": 7,
    "customerFirstName": "Ivan",
    "customerLastName": "Petrov",
    "customerEmail": "ivan@example.com",
    "vehicleId": 12,
    "vehicleBrand": "Toyota",
    "vehicleModel": "Camry",
    "vehiclePlateNumber": "A123BC77",
    "createdAt": "2026-04-19T10:15:30Z"
  }
}
```

## Idempotency

`eventId` is required and must be globally unique. The service stores every received event in `notification_event_inbox` and stores a unique notification per `eventId + channel`, so Kafka redelivery does not send duplicate emails after a successful send.

## Health

```http
GET /actuator/health
GET /actuator/info
```
