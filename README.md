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
- `/api/v1/admin/subjects` — створення, перейменування та видалення предметів;
- `/api/v1/admin/quizzes` — CRUD тестів і довідник складності;
- `/api/v1/admin/quizzes/{id}/questions` — перегляд і створення запитань із чотирма варіантами;
- `/api/v1/admin/questions/{id}` — редагування та видалення запитань;
- `/api/v1/admin/users` — список користувачів і керування їхнім статусом;
- `/api/v1/admin/results` — усі завершені результати з необов’язковим часовим діапазоном;
- `GET /actuator/health` — перевірка стану;
- `/swagger-ui.html` — інтерактивна OpenAPI-документація.

Захищені маршрути приймають заголовок `Authorization: Bearer <token>`. API повертає однакові JSON-помилки
для `401`, `403` і `429`; ліміти запитів налаштовуються через `API_RATE_LIMIT_REQUESTS`,
`LOGIN_RATE_LIMIT_REQUESTS`, `API_RATE_LIMIT_WINDOW` та `API_RATE_LIMIT_MAX_CLIENTS`.

Адміністративні mutation-endpoint-и приймають `POST`, `PUT`, `PATCH` і `DELETE`, доступні лише JWT із
роллю `ADMIN` та використовуються окремим React frontend замість старих JSP-форм.

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

## Docker і Kubernetes для REST API

Кореневий `Dockerfile` збирає лише Spring Boot-модуль `api` у Java 25 runtime-образ. Контейнер працює
від непривілейованого користувача та слухає порт `8081`:

```bash
docker build -t ghcr.io/vitaliilatysh/quizproject-api:latest .
docker push ghcr.io/vitaliilatysh/quizproject-api:latest
```

MySQL має бути доступним із кластера, а міграції потрібно застосувати до розгортання API. Для запуску
готових Kubernetes-ресурсів:

1. У `deploy/kubernetes/backend/config-map.yaml` замініть `CORS_ALLOWED_ORIGINS` на адресу фронтенду.
2. Скопіюйте `deploy/kubernetes/backend/secret.env.example` у `secret.env` і заповніть реальні значення.
   Файл `secret.env` і маніфест `secret.yaml` ігноруються Git.
3. Для незмінного production-релізу замініть `newTag` у `deploy/kubernetes/backend/kustomization.yaml`
   на тег або digest опублікованого образу.
4. Створіть namespace і Secret, а потім застосуйте Kustomize-конфігурацію:

```bash
kubectl apply -f deploy/kubernetes/backend/namespace.yaml
kubectl -n quizproject create secret generic quiz-api-secrets \
  --from-env-file=deploy/kubernetes/backend/secret.env \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k deploy/kubernetes/backend
kubectl -n quizproject rollout status deployment/quiz-api
```

Для локальної перевірки без Ingress відкрийте Service через port-forward:

```bash
kubectl -n quizproject port-forward service/quiz-api 8081:80
curl http://localhost:8081/actuator/health/readiness
```

Маніфести створюють два екземпляри API, `ClusterIP` Service, startup/liveness/readiness probes,
ліміти ресурсів і `PodDisruptionBudget`. Ingress буде зручніше додати разом із фронтендом, щоб в одному
місці налаштувати домен, TLS та маршрути `/api` і `/`.

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
