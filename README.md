# Quiz Project

Вебзастосунок для створення та проходження тестів. Студенти можуть обирати тести, відповідати на запитання й переглядати результати, а адміністратори — керувати предметами, тестами та запитаннями.

## Технології

- Java 25
- Java Servlet API 4.0 (`javax.servlet`) і JSP
- Spring Boot 4.1 REST API
- Gradle 9.6.1
- MySQL 8
- JUnit 4/6 та Mockito
- Flyway і Testcontainers

## Збірка і тестування

```bash
./gradlew clean test war :api:check :api:bootJar
```

У Windows використовуйте `gradlew.bat`. Готовий WAR-файл буде створено в каталозі `build/libs`,
а виконуваний API JAR — у `api/build/libs`.

## Запуск

1. Створіть порожню базу `tests_db` і застосуйте Flyway-міграції командою з розділу нижче.
2. Перед запуском передайте Tomcat системні властивості `DB_URL`, `DB_USERNAME` і `DB_PASSWORD`.
   Наприклад: `-DDB_URL=jdbc:mysql://localhost:3306/tests_db?characterEncoding=UTF-8`.
3. Зареєструйте першого користувача через застосунок і надайте йому роль адміністратора SQL-командою,
   `UPDATE users SET role_id = 1 WHERE login = '<initial-admin-login>';`. Схема навмисно не містить стандартних паролів.
4. Розгорніть WAR-файл у контейнері сервлетів, сумісному із Servlet API 4.0, наприклад Apache Tomcat 9.

Для кожного pull request і push у `master` GitHub Actions автоматично запускає збірку, тести та створення WAR-файлу й API JAR.

## REST API (Spring Boot)

Модуль `api` — окремий Spring Boot 4.1 застосунок на Java 25. Він працює з тією самою MySQL-схемою,
але запускається незалежно від Servlet/JSP WAR:

```bash
DB_URL=jdbc:mysql://localhost:3306/tests_db DB_USERNAME=root DB_PASSWORD=secret \
JWT_SECRET=<base64-encoded-32-byte-secret> \
  ./gradlew :api:bootRun
```

У Windows передайте ці значення як змінні середовища та виконайте `gradlew.bat :api:bootRun`.
Типовий API-порт — `8081`; його можна змінити через `API_PORT`.

`JWT_SECRET` є обов’язковим Base64-значенням щонайменше з 32 випадкових байтів. Наприклад, секрет можна
згенерувати командою `openssl rand -base64 32`. Термін дії токена типово становить 15 хвилин і
налаштовується через `JWT_TTL`. Дозволені браузерні джерела задаються списком `CORS_ALLOWED_ORIGINS`.

- `POST /api/v1/auth/login` — отримання JWT за логіном і паролем;
- `GET /api/v1/quizzes` — список тестів;
- `GET /api/v1/quizzes/{id}` — один тест;
- `POST /api/v1/quizzes/{id}/attempts` — старт захищеної спроби проходження тесту;
- `GET /api/v1/attempts/{id}` — отримання власної спроби із запитаннями без ознак правильних відповідей;
- `POST /api/v1/attempts/{id}/complete` — одноразове надсилання відповідей і розрахунок результату;
- `GET /api/v1/results/me` — результати поточного користувача з роллю `USER` або `ADMIN`;
- `GET /api/v1/admin/status` — перевірка доступу лише для ролі `ADMIN`;
- `GET /actuator/health` — перевірка стану;
- `/swagger-ui.html` — інтерактивна OpenAPI-документація.

Захищені маршрути приймають заголовок `Authorization: Bearer <token>`. API повертає однакові JSON-помилки
для `401`, `403` і `429`; ліміти запитів налаштовуються через `API_RATE_LIMIT_REQUESTS`,
`LOGIN_RATE_LIMIT_REQUESTS`, `API_RATE_LIMIT_WINDOW` та `API_RATE_LIMIT_MAX_CLIENTS`.

Для проходження тесту отримайте JWT, створіть спробу, а потім передайте вибрані ідентифікатори відповідей:

```bash
curl -X POST http://localhost:8081/api/v1/quizzes/1/attempts \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8081/api/v1/attempts/42/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"answerIds":[1,5,6]}'
```

API перевіряє власника та термін дії спроби, приймає лише відповіді з вибраного тесту, зберігає їх і
завершує спробу атомарно. Повторне завершення повертає `409 Conflict`.

## Міграції бази даних

Версійовані Flyway-міграції зберігаються в `resources/db/migration`. Нова база створюється міграцією `V1`, а `V2` додає обмеження безпеки для паролів, спроб і відповідей.

Для застосування міграцій передайте `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` і виконайте:

```bash
./gradlew flywayMigrate
```

Інтеграційний тест автоматично піднімає MySQL 8 у Docker, застосовує всі міграції та перевіряє реальний CRUD репозиторію:

```bash
./gradlew integrationTest
```

## Міграція безпеки

Для наявної бази без історії Flyway спочатку зробіть резервну копію та позначте поточну схему як версію `1`, після чого застосуйте `V2`. Старі `resources/create_tests_db.sql` і `resources/migrate_p0_security.sql` залишені лише для сумісності.

- Облікові дані БД передаються тільки через `DB_URL`, `DB_USERNAME` і `DB_PASSWORD`; стандартних секретів у репозиторії немає.
- Новий пароль повинен містити 8–128 символів без пробілів. Паролі зберігаються як salted PBKDF2-HMAC-SHA256. Старий пароль у відкритому вигляді автоматично оновлюється після наступного успішного входу.
- Завершення тесту прив’язане до автентифікованого користувача та конкретної спроби, перевіряється за серверним часом і фіксується один раз у транзакції БД.
