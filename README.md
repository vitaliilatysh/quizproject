# Quiz Project API

Spring Boot REST API для створення та проходження тестів. Студенти реєструються,
проходять тести й переглядають результати; адміністратори керують користувачами,
предметами, тестами, запитаннями та результатами.

Frontend: [quizproject-web](https://github.com/vitaliilatysh/quizproject-web).
Публічний застосунок: [http://164.92.163.71/](http://164.92.163.71/).

## Поточна архітектура

Один Gradle-модуль `api`; код у `api/src/main/java/ua/nure/latysh/quizzes/api`
організований за функціями:

| Пакет | Відповідальність |
| --- | --- |
| `identity` | авторизація, профіль, користувачі, ролі, статуси та refresh-сесії |
| `quiz` | каталог, предмети, рівні, запитання, відповіді та їх адміністрування |
| `attempt` | спроби, знімки запитань, дедлайни й завершення |
| `result` | результати, підсумки та адміністративні звіти |
| `admin` | HTTP-маршрути адміністрування |
| `security`, `config`, `observability`, `support` | захист, конфігурація, метрики та обробка помилок |

Сутності, репозиторії, сервіси та DTO розміщені поруч із відповідною функцією.
Транзакційні межі належать сервісам; один UTC `Clock` передається через конструктори.
Запис запитання повертає змінену сутність без повторного читання всього тесту.

Specification для каталогу, Strategy для rate limiting і Facade для спроб описані в
[архітектурних патернах](docs/backend-patterns.md).

## Технології

- Java 25, Spring Boot 4.1, Spring Security;
- Spring Data JPA / Hibernate, MySQL 8.4, Flyway;
- Spring Data Redis, JWT і ротація refresh-сесій;
- Micrometer / Prometheus, OpenAPI / Swagger UI;
- Gradle Wrapper, JUnit 6, Testcontainers, JaCoCo та SonarCloud.

Точні версії: [api/build.gradle](api/build.gradle), [build.gradle](build.gradle),
[Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties) та закомічені `gradle.lockfile`.
Перевизначення версій Tomcat і Jackson у `api/build.gradle` також входять до lockfile.

## Локальний запуск

Потрібні JDK 25 і Docker із Compose v2. Для запуску API підготуйте MySQL та Redis,
наприклад в окремих локальних контейнерах:

~~~bash
docker run --detach --rm --name quiz-mysql \
  --publish 127.0.0.1:3306:3306 \
  --env MYSQL_DATABASE=tests_db \
  --env MYSQL_USER=quiz_app \
  --env MYSQL_PASSWORD=local-db-password \
  --env MYSQL_ROOT_PASSWORD=local-root-password \
  mysql:8.4

docker run --detach --rm --name quiz-redis \
  --publish 127.0.0.1:6379:6379 redis:8.2.8-alpine
~~~

Після готовності MySQL запустіть API з кореня репозиторію:

~~~bash
export DB_URL="jdbc:mysql://localhost:3306/tests_db?characterEncoding=UTF-8&serverTimezone=UTC"
export DB_USERNAME="quiz_app"
export DB_PASSWORD="local-db-password"
export REDIS_HOST="localhost"
export JWT_SECRET="$(openssl rand -base64 32)"
export CORS_ALLOWED_ORIGINS="http://127.0.0.1:4173"
./gradlew :api:bootRun
~~~

У Windows задайте ті самі змінні та виконайте `gradlew.bat :api:bootRun`.
Flyway застосовує міграції під час запуску.

| Локальна адреса | Призначення |
| --- | --- |
| `http://localhost:8081/api/v1/quizzes` | публічний каталог |
| `http://localhost:8081/swagger-ui.html` | Swagger UI |
| `http://localhost:8081/v3/api-docs` | OpenAPI JSON |
| `http://localhost:9081/actuator/health/readiness` | readiness на окремому management-порту |

Для ізольованої розробки без Redis можна встановити `RATE_LIMIT_BACKEND=memory`,
`REDIS_HEALTH_ENABLED=false` і `READINESS_HEALTH_INDICATORS=readinessState,db`.
Цей режим розрахований на один екземпляр API.

## Збірка і перевірки

~~~bash
./gradlew clean :api:check :api:integrationTest :api:bootJar
~~~

- `:api:check` запускає unit/contract-тести та вимагає **100% покриття рядків, методів і гілок**.
- `:api:integrationTest` потребує Docker: Testcontainers піднімає MySQL і Redis,
  перевіряє Flyway-міграції, конкурентні операції, rate limits, кешування та HTTP-поведінку.
- `:api:bootJar` створює executable JAR у `api/build/libs`.
- JaCoCo-звіти доступні в `api/build/reports/jacoco/test`.

Швидка перевірка без інтеграційного стека:

~~~bash
./gradlew :api:check
~~~

Експорт контракту для frontend:

~~~bash
./gradlew :api:test --tests '*OpenApiExportTest' --no-daemon
~~~

Результат: `api/build/openapi/openapi.json`. Frontend CI порівнює цей експорт
з актуального `master` зі своїм snapshot і згенерованими типами.

Залежності зафіксовані в кореневому та `api/gradle.lockfile`.
Після ручної зміни залежностей оновіть lockfile локально через
`./gradlew --write-locks` і закомітьте його. CI перевіряє наявні локи;
Dependabot оновлює їх разом із залежностями.

## Основні маршрути

| Метод і шлях | Призначення |
| --- | --- |
| `POST /api/v1/auth/register`, `POST /api/v1/auth/login` | створення акаунта / вхід, пара access і refresh token |
| `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` | ротація токенів / відкликання сесії |
| `GET /api/v1/users/me`, `PUT /api/v1/users/me/password` | профіль / зміна пароля |
| `GET /api/v1/quizzes`, `GET /api/v1/quizzes/{id}` | каталог / окремий тест |
| `GET /api/v1/quizzes/summary` | кількість тестів і предметів із тестами |
| `POST /api/v1/quizzes/{id}/attempts` | початок спроби |
| `GET /api/v1/attempts/{id}`, `POST /api/v1/attempts/{id}/complete` | читання / завершення спроби |
| `GET /api/v1/results/me`, `GET /api/v1/results/me/summary` | історія / підсумки за всю історію |
| `/api/v1/admin/**` | операції ролі `ADMIN` |

Захищені маршрути приймають `Authorization: Bearer <token>`.
Каталог підтримує `search` і повторюваний `complexity`
(`low`, `medium`, `high`, `advanced`); пошук виконує база даних.

Колекції тестів, власних результатів, адміністративних користувачів і результатів
підтримують zero-based `page` і `size` (1–100, типово 20).
Відповідь — JSON-масив; метадані передаються в `X-Page-Number`, `X-Page-Size`,
`X-Total-Count` та `X-Total-Pages`. Без параметрів пагінації повертається повний список.

Помилки мають форму `{timestamp, status, error, message, path}`.
`X-Correlation-ID` пов'язує відповідь із записом у журналі.
Публічний каталог підтримує weak `ETag`, `If-None-Match` і `304 Not Modified`.

## Конфігурація

Джерело значень за замовчуванням — [application.yml](api/src/main/resources/application.yml).
`JWT_SECRET` обов'язковий; для MySQL і Redis потрібні доступні сервіси та відповідні облікові дані.

| Змінна | Типово | Призначення |
| --- | --- | --- |
| `JWT_SECRET` | обов'язкова | Base64 щонайменше з 32 випадкових байтів |
| `JWT_TTL` / `REFRESH_TOKEN_TTL` | `PT15M` / `P7D` | час життя access / refresh token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | дозволені origin; для Vite задайте `http://127.0.0.1:4173` |
| `DB_URL` | локальний MySQL, база `tests_db` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / порожній | облікові дані MySQL |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / порожній | підключення до Redis |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_TIMEOUT` | `2s` / `2s` | таймаути Redis |
| `RATE_LIMIT_BACKEND` | `redis` | `redis` або локальний `memory` |
| `API_RATE_LIMIT_REQUESTS` / `LOGIN_RATE_LIMIT_REQUESTS` | `120` / `5` | загальний / auth-ліміт |
| `API_RATE_LIMIT_WINDOW` / `API_RATE_LIMIT_MAX_CLIENTS` | `PT1M` / `10000` | вікно / місткість in-memory лічильників |
| `TRUSTED_PROXY_CIDRS` | `127.0.0.1/32,::1/128` | довірені proxy для `X-Forwarded-For` |
| `REDIS_HEALTH_ENABLED` | `true` | перевірка Redis |
| `READINESS_HEALTH_INDICATORS` | `readinessState,db,redis` | склад readiness |
| `API_PORT` / `MANAGEMENT_PORT` | `8081` / `9081` | окремі API / Actuator конектори |
| `SWAGGER_UI_ENABLED` / `API_DOCS_ENABLED` | `true` / `true` | Swagger UI / OpenAPI |
| `HTTP_COMPRESSION_ENABLED` / `HTTP_COMPRESSION_MIN_RESPONSE_SIZE` | `true` / `1KB` | gzip |
| `PUBLIC_QUIZ_CACHE_MAX_AGE` | `PT1M` | TTL публічного каталогу |
| `SHUTDOWN_TIMEOUT` / `LOG_FORMAT` | `20s` / `logstash` | graceful shutdown / формат логів |

## CI/CD і залежності

| Workflow | Запуск | Перевірки / дія |
| --- | --- | --- |
| [CI](.github/workflows/ci.yml) | PR, push у `master`, вручну | Python-тести automation, Compose, unit/contract/integration-тести, покриття, JAR і OpenAPI artifact |
| [SonarQube analysis](.github/workflows/sonarqube.yml) | PR, push у `master`, вручну | JaCoCo, покриття Python-скриптів, SonarCloud і блокувальний quality gate |
| [Backend container delivery](.github/workflows/container.yml) | PR, push у `master`, вручну | Docker build, Trivy, smoke-тести MySQL/Redis і Compose; публікація образу з основної гілки |
| [Dependabot auto-merge](.github/workflows/dependabot-auto-merge.yml) | після перевірок, кожні 15 хвилин, вручну | squash перевірених Dependabot PR |
| [DigitalOcean preflight](.github/workflows/digitalocean-preflight.yml) | вручну / зміни власних файлів у `master` | перевірка SSH і метаданих сервера |
| [Prepare existing DigitalOcean deployment](.github/workflows/digitalocean-prepare.yml) | вручну / зміни власних файлів у `master` | підготовка наявного Compose-стека та резервної копії |
| [Deploy to DigitalOcean](.github/workflows/deploy-digitalocean.yml) | production вручну; staging за opt-in | перевірка підпису й release gates, SSH rollout за digest |

SonarCloud потребує `SONAR_TOKEN`; job пропускається для запусків від `dependabot[bot]`.
Гейт покриття залишається в CI. Для локального Sonar-аналізу:

~~~bash
./gradlew :api:test :api:jacocoTestReport :api:jacocoTestCoverageVerification sonar
~~~

Dependabot auto-merge перевіряє всі обов'язкові workflows для поточного SHA,
конфлікти та `Changes requested`. Після мержа через `GITHUB_TOKEN` він явно запускає
перевірки й публікацію на `master`; deployment окремо запускається вручну.

## Контейнер і DigitalOcean

~~~bash
docker build -t quizproject-api:local .
~~~

Production працює на Droplet `quiz-project-prod` через Docker Compose:
API + MySQL + Redis; Caddy і frontend належать окремому репозиторію.
Публічний `/api` проксується до API; база, Redis і management-порт залишаються
внутрішніми. Production Compose вимикає Swagger UI та OpenAPI.

Delivery публікує `ghcr.io/vitaliilatysh/quizproject:master` і `sha-<commit>`
для `linux/amd64` та `linux/arm64`, додає SBOM/provenance і підписує digest через cosign.

Ручний production deploy у GitHub Actions:

1. Дочекайтеся успішних CI, SonarQube analysis та Backend container delivery на `master`.
2. Відкрийте **Actions → Deploy to DigitalOcean → Run workflow**.
3. Оберіть `master`, `environment=production`; `image_tag` можна залишити порожнім
   для `sha-<commit>` вибраної версії.
4. Якщо оновлюєте обидва компоненти, спочатку розгорніть backend, потім frontend.

У GitHub environment потрібні variables `DO_HOST`, `DO_SSH_USER`, `DO_SSH_PORT`
та secrets `DO_SSH_KEY`, `DO_KNOWN_HOSTS`. Паролі та JWT залишаються на сервері.
Автоматичний staging вмикається окремо через `DO_STAGING_ENABLED=true`.

Спільний серверний lock координує обидва репозиторії; невдалий rollout повертає
попередній image/configuration. Відкат образу не відкочує Flyway-міграції.
Інструкції: [DigitalOcean deployment](docs/digitalocean-deployment.md) і
[резервне копіювання та відновлення](docs/database-recovery.md).

`deploy/kubernetes` містить довідкові Kustomize-маніфести для окремого ручного
розгортання; поточний deployment workflow використовує Compose.

## Безпека та спостережуваність

- Паролі зберігаються як salted PBKDF2-HMAC-SHA256; застарілі записи оновлюються під час входу.
- У базі зберігається SHA-256 hash refresh token; ротація має replay-захист.
- Logout, зміна пароля й блокування акаунта відкликають серверні сесії.
- Redis забезпечує спільні атомарні ліміти; його відмова дає контрольований `503`.
- Завершення спроби перевіряє власника, snapshot, дедлайн і відповіді;
  блокування захищає від подвійного результату.
- Структуровані JSON-логи містять `X-Correlation-ID`; Prometheus на management-порту
  надає HTTP/JVM/Redis та бізнес-метрики.
- Flyway-міграції з `api/src/main/resources/db/migration` входять до JAR.
  Перед підключенням старої бази без історії Flyway дотримуйтесь процедури резервного копіювання.

