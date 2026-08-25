# Quiz Project API

Spring Boot REST API для створення та проходження тестів. Студенти можуть реєструватися,
проходити тести й переглядати результати, а адміністратори — керувати користувачами,
предметами, тестами, запитаннями та результатами.

React-клієнт розвивається окремо в [quizproject-web](https://github.com/vitaliilatysh/quizproject-web).
Legacy JSP/Servlet WAR більше не є частиною backend.

## Технології

- Java 25
- Spring Boot 4.1
- Spring Security і короткоживучі JWT
- Spring JDBC та MySQL 8
- Flyway
- Gradle 9.6.1
- JUnit 6, Testcontainers і JaCoCo
- OpenAPI / Swagger UI

## Збірка і перевірка

~~~bash
./gradlew clean :api:check :api:integrationTest :api:bootJar
~~~

`:api:check` запускає unit та contract-тести й перевіряє 100% line coverage.
`:api:integrationTest` піднімає чистий MySQL 8.4 через Testcontainers, запускає Spring Boot,
застосовує production Flyway-міграції та перевіряє схему.

Готовий executable JAR створюється в `api/build/libs`.

## Локальний запуск

Створіть порожню базу MySQL і передайте конфігурацію через змінні середовища:

~~~bash
export DB_URL="jdbc:mysql://localhost:3306/tests_db?characterEncoding=UTF-8&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="secret"
export JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')"
export CORS_ALLOWED_ORIGINS="http://localhost:4173"
./gradlew :api:bootRun
~~~

У Windows задайте ті самі змінні середовища та виконайте `gradlew.bat :api:bootRun`.
Типовий порт — `8081`; його можна змінити через `API_PORT`.

Flyway автоматично перевіряє та застосовує міграції під час запуску API. Окремий ручний крок
перед стартом застосунку більше не потрібний.

`JWT_SECRET` має бути Base64-значенням щонайменше з 32 випадкових байтів.
Термін дії токена задається через `JWT_TTL` і типово становить 15 хвилин.

## Основні маршрути

- `POST /api/v1/auth/login` — вхід;
- `POST /api/v1/auth/register` — реєстрація та отримання JWT;
- `GET /api/v1/users/me` — профіль;
- `PUT /api/v1/users/me/password` — зміна пароля;
- `GET /api/v1/quizzes` — список тестів;
- `GET /api/v1/quizzes/{id}` — один тест;
- `POST /api/v1/quizzes/{id}/attempts` — початок спроби;
- `GET /api/v1/attempts/{id}` — поточна спроба;
- `POST /api/v1/attempts/{id}/complete` — завершення спроби;
- `GET /api/v1/results/me` — результати користувача;
- `/api/v1/admin/**` — адміністративні операції;
- `/actuator/health` — стан застосунку;
- `/swagger-ui.html` — інтерактивна OpenAPI-документація.

Захищені маршрути приймають `Authorization: Bearer <token>`. Адміністративні операції
доступні лише ролі `ADMIN`.

## Міграції бази даних

Production-міграції знаходяться в `api/src/main/resources/db/migration` і входять до JAR:

- `V1__baseline.sql` створює початкову схему та довідники;
- `V2__secure_attempts.sql` посилює зберігання паролів, спроб і відповідей.

Для наявної бази без `flyway_schema_history` спочатку створіть резервну копію та позначте
поточну схему як baseline версії 1 перед запуском нової версії API.

## Docker

~~~bash
docker build -t quizproject-api:local .
docker run --rm -p 8081:8081 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/tests_db?serverTimezone=UTC" \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=secret \
  -e JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')" \
  -e CORS_ALLOWED_ORIGINS=http://localhost:4173 \
  quizproject-api:local
~~~

Образ запускає лише Spring Boot API від непривілейованого користувача.

## Kubernetes

Маніфести знаходяться в `deploy/kubernetes/backend` і створюють:

- два екземпляри API;
- `ClusterIP` Service;
- startup, liveness і readiness probes;
- resource requests/limits;
- `PodDisruptionBudget`;
- non-root контейнер із read-only root filesystem.

Створіть Secret із `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` і `JWT_SECRET`, після чого застосуйте
Kustomize-конфігурацію:

~~~bash
kubectl apply -f deploy/kubernetes/backend/namespace.yaml
kubectl -n quizproject create secret generic quiz-api-secrets \
  --from-env-file=deploy/kubernetes/backend/secret.env \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k deploy/kubernetes/backend
kubectl -n quizproject rollout status deployment/quiz-api
~~~

Spring Boot запускає Flyway до переходу readiness probe у стан `UP`, тому pod не приймає трафік
зі схемою, яка ще не пройшла міграцію.

## Безпека

- паролі зберігаються як salted PBKDF2-HMAC-SHA256;
- CORS використовує allowlist із `CORS_ALLOWED_ORIGINS`;
- login та API мають окремі rate limits;
- помилки `401`, `403` і `429` повертаються в одному JSON-форматі;
- завершення спроби перевіряє власника, термін дії та допустимі відповіді;
- секрети не зберігаються в репозиторії.
