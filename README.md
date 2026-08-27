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
- Spring Data JPA (Hibernate) та MySQL 8
- Spring Data Redis і атомарні distributed rate limits
- Micrometer і Prometheus
- Flyway
- Gradle 9.6.1
- JUnit 6, Testcontainers і JaCoCo
- OpenAPI / Swagger UI

## Збірка і перевірка

~~~bash
./gradlew clean :api:check :api:integrationTest :api:bootJar
~~~

`:api:check` запускає unit та contract-тести й перевіряє 100% line coverage.
`:api:integrationTest` піднімає чисті MySQL 8.4 і Redis 8.2 через Testcontainers. Перевірки
застосовують production Flyway-міграції, доводять, що різні екземпляри API використовують один
атомарний rate limit, і що одночасне завершення однієї спроби двома запитами не може подвоїти
результат — пессимістичне блокування рядка допускає рівно одне успішне завершення.

Готовий executable JAR створюється в `api/build/libs`.

## Локальний запуск

Створіть порожню базу MySQL, запустіть Redis і передайте конфігурацію через змінні середовища:

~~~bash
docker run --rm --name quiz-redis -p 6379:6379 redis:8.2.8-alpine
~~~

В іншому терміналі:

~~~bash
export DB_URL="jdbc:mysql://localhost:3306/tests_db?characterEncoding=UTF-8&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="secret"
export REDIS_HOST="localhost"
export JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')"
export CORS_ALLOWED_ORIGINS="http://localhost:4173"
./gradlew :api:bootRun
~~~

У Windows задайте ті самі змінні середовища та виконайте `gradlew.bat :api:bootRun`.
Типовий порт — `8081`; його можна змінити через `API_PORT`. Production використовує Redis
за замовчуванням. Для ізольованої локальної розробки можна встановити
`RATE_LIMIT_BACKEND=memory`, `REDIS_HEALTH_ENABLED=false` і
`READINESS_HEALTH_INDICATORS=readinessState,db`, але цей режим не можна використовувати з кількома
екземплярами API.

Великі JSON, HTML, JavaScript, CSS і текстові відповіді стискаються gzip, якщо клієнт передає
`Accept-Encoding: gzip`. Типовий поріг — 1 KiB; його можна змінити через
`HTTP_COMPRESSION_MIN_RESPONSE_SIZE` або вимкнути компресію через `HTTP_COMPRESSION_ENABLED=false`.

Flyway автоматично перевіряє та застосовує міграції під час запуску API. Окремий ручний крок
перед стартом застосунку більше не потрібний.

`JWT_SECRET` має бути Base64-значенням щонайменше з 32 випадкових байтів.
Термін дії токена задається через `JWT_TTL` і типово становить 15 хвилин. Поки токен ще дійсний,
клієнт може обміняти його на новий із повним терміном дії через `POST /api/v1/auth/refresh`, не
вводячи пароль повторно — це дозволяє тривалим сесіям (наприклад, довгому проходженню тесту)
залишатися активними без повторного логіна.

## Основні маршрути

- `POST /api/v1/auth/login` — вхід;
- `POST /api/v1/auth/register` — реєстрація та отримання JWT;
- `POST /api/v1/auth/refresh` — обмін дійсного JWT на новий із повним терміном дії;
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
- `/actuator/metrics` — метрики, доступні лише адміністратору;
- `/actuator/prometheus` — endpoint для Prometheus scrape;
- `/swagger-ui.html` — інтерактивна OpenAPI-документація.

Захищені маршрути приймають `Authorization: Bearer <token>`. Адміністративні операції
доступні лише ролі `ADMIN`.

Колекції тестів, власних результатів та адміністративних тестів, користувачів і результатів
підтримують необов'язкові zero-based параметри `page` і `size` (`size` від 1 до 100, типово 20,
коли пагінацію ввімкнено). Без цих параметрів зберігається попередня поведінка з повним JSON-масивом.
Відповідь залишається масивом, а metadata повертається в `X-Page-Number`, `X-Page-Size`,
`X-Total-Count` і `X-Total-Pages`, тому наявний React-клієнт не потребує одночасного оновлення.

## Міграції бази даних

Production-міграції знаходяться в `api/src/main/resources/db/migration` і входять до JAR:

- `V1__baseline.sql` створює початкову схему та довідники;
- `V2__secure_attempts.sql` посилює зберігання паролів, спроб і відповідей;
- `V3__index_paginated_queries.sql` додає складені індекси для швидких сторінок власних та
  адміністративних результатів у порядку від найновіших.

Для наявної бази без `flyway_schema_history` спочатку створіть резервну копію та позначте
поточну схему як baseline версії 1 перед запуском нової версії API.

## Docker

~~~bash
docker build -t quizproject-api:local .
docker run --rm -p 8081:8081 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/tests_db?serverTimezone=UTC" \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=secret \
  -e REDIS_HOST=host.docker.internal \
  -e JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')" \
  -e CORS_ALLOWED_ORIGINS=http://localhost:4173 \
  quizproject-api:local
~~~

Образ запускає лише Spring Boot API від непривілейованого користувача.

## Kubernetes

Базові маніфести знаходяться в `deploy/kubernetes/backend`, а готові конфігурації оточень — у
`deploy/kubernetes/overlays/local` і `deploy/kubernetes/overlays/production`. Вони створюють:

- два екземпляри API;
- Redis 8.2 для спільних атомарних rate limits;
- `ClusterIP` Service;
- startup, liveness і readiness probes;
- resource requests/limits;
- `PodDisruptionBudget`;
- розподіл API pod-ів між Kubernetes nodes;
- безпечне завершення трафіку через `preStop` і Spring graceful shutdown;
- non-root контейнери з read-only root filesystem;
- NetworkPolicy, яка дозволяє доступ до Redis лише pod-ам API.

Production overlay також додає `HorizontalPodAutoscaler`: API масштабується від 2 до 6 pod-ів,
коли середнє використання CPU перевищує 70%. Для роботи HPA кластер повинен надавати resource
metrics через [Metrics Server](https://github.com/kubernetes-sigs/metrics-server) або сумісний
metrics API. Scale-down стабілізується протягом п'яти хвилин, щоб уникнути коливань кількості
pod-ів під нерівномірним навантаженням.

Pod template має стандартні `prometheus.io/*` annotations. Якщо в кластері встановлений
[Prometheus Operator](https://prometheus-operator.dev/), додатково застосуйте готові
`ServiceMonitor` і `PrometheusRule`:

~~~bash
kubectl apply -k deploy/kubernetes/monitoring
~~~

Monitoring bundle додає alerts для недоступності API, високої частки `5xx`, p95 latency понад
одну секунду та відмов Redis-backed rate limiter. Він не входить до основного overlay, тому
звичайний Kubernetes-кластер без Prometheus CRD продовжує приймати backend manifests.

Для локального кластера спочатку зберіть образ, створіть Secret із `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET` і `REDIS_PASSWORD`, після чого застосуйте local overlay:

~~~bash
docker build -t quizproject-api:local .
kubectl apply -f deploy/kubernetes/backend/namespace.yaml
kubectl -n quizproject create secret generic quiz-api-secrets \
  --from-env-file=deploy/kubernetes/backend/secret.env \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k deploy/kubernetes/overlays/local
kubectl -n quizproject rollout status deployment/quiz-api
~~~

Production overlay використовує `ghcr.io/vitaliilatysh/quizproject:master`. Для відтворюваного
розгортання застосовуйте manifest artifact із delivery workflow: у ньому image зафіксований
registry digest `sha256:...`.

Spring Boot запускає Flyway до переходу readiness probe у стан `UP`, тому pod не приймає трафік
зі схемою, яка ще не пройшла міграцію. Readiness також перевіряє Redis.

`TRUSTED_PROXY_CIDRS` повинен містити лише мережі фактичних ingress/load-balancer proxy.
Заголовок `X-Forwarded-For` ігнорується для запитів безпосередньо з недовіреної адреси.

## Delivery контейнера

Workflow **Backend container delivery** виконується для кожного pull request:

- збирає production Docker image;
- блокує зміни з виправними критичними та високими вразливостями за допомогою Trivy
  (сканування образу також включає пошук секретів у шарах);
- використовує точні commit SHA для сторонніх GitHub Actions, звірені з immutable release tags;
- запускає image від непривілейованого користувача з read-only filesystem разом із MySQL 8.4 і
  Redis 8.2 та перевіряє readiness, liveness і OpenAPI;
- рендерить local і production Kustomize overlays.

Після push у `master` workflow публікує multi-platform образи для `linux/amd64` і `linux/arm64`:

- `ghcr.io/vitaliilatysh/quizproject:master`;
- `ghcr.io/vitaliilatysh/quizproject:sha-<commit>`.

До образу додаються SBOM і build provenance. Опублікований digest підписується keyless-режимом
cosign (OIDC-токен GitHub Actions обмінюється на короткоживучий сертифікат Fulcio, тож ключі ніде
не зберігаються) і одразу перевіряється в тому ж прогоні — зламане підписування завалить реліз,
а не опублікує непідписаний образ. Перевірити опублікований образ самостійно:

~~~bash
cosign verify \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  --certificate-identity-regexp '^https://github.com/vitaliilatysh/quizproject/\.github/workflows/container\.yml@' \
  ghcr.io/vitaliilatysh/quizproject@sha256:...
~~~

Готовий production manifest з immutable image digest зберігається в GitHub Actions artifact
`kubernetes-manifest-<commit>` протягом 30 днів.

## Спостережуваність

Production console logs мають структурований Logstash JSON-формат. Кожна HTTP-відповідь містить
`X-Correlation-ID`; безпечне значення клієнта зберігається, а відсутнє або некоректне замінюється
UUID. Те саме значення потрапляє до MDC та completion log разом із методом, шляхом, статусом і
тривалістю запиту. Формат можна змінити змінною `LOG_FORMAT`.

Prometheus endpoint містить стандартні HTTP, JVM, HikariCP і Redis client metrics, а також
низькокардинальні бізнес-метрики:

- `quiz_authentication_attempts_total{outcome=...}`;
- `quiz_account_registrations_total`;
- `quiz_token_refreshes_total`;
- `quiz_attempts_total{state=...}`;
- `quiz_attempt_score_*`;
- `quiz_rate_limit_requests_total{scope=...,outcome=...}`.

Не використовуйте username, IP, attempt ID або інші необмежені значення як metric labels.

## Безпека

- паролі зберігаються як salted PBKDF2-HMAC-SHA256;
- CORS використовує allowlist із `CORS_ALLOWED_ORIGINS`;
- login та API мають окремі атомарні Redis rate limits, спільні для всіх pod-ів;
- IP клієнта визначається справа наліво через ланцюжок лише довірених proxy;
- відмова Redis закриває доступ контрольованою відповіддю `503`;
- помилки `401`, `403` і `429` повертаються в одному JSON-форматі;
- завершення спроби перевіряє власника, термін дії та допустимі відповіді;
- секрети не зберігаються в репозиторії.
