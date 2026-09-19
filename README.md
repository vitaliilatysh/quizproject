# Quiz Project API

Spring Boot REST API для створення та проходження тестів. Студенти можуть реєструватися,
проходити тести й переглядати результати, а адміністратори — керувати користувачами,
предметами, тестами, запитаннями та результатами.

React-клієнт розвивається окремо в [quizproject-web](https://github.com/vitaliilatysh/quizproject-web).
Legacy JSP/Servlet WAR більше не є частиною backend.

## Технології

- Java 25
- Spring Boot 4.1
- Spring Security, короткоживучі JWT і ротація refresh-сесій
- Spring Data JPA (Hibernate) та MySQL 8
- Spring Data Redis і атомарні distributed rate limits
- Micrometer і Prometheus
- Flyway
- Gradle 9.7.1
- JUnit 6, Testcontainers і JaCoCo
- SonarCloud із блокувальним quality gate
- OpenAPI / Swagger UI

## Збірка і перевірка

~~~bash
./gradlew clean :api:check :api:integrationTest :api:bootJar
~~~

`:api:check` запускає unit та contract-тести й перевіряє покриття:

```
INSTRUCTION  100.00%
BRANCH        99.44%   (1 гілка з 178)
LINE         100.00%
METHOD       100.00%
CLASS        100.00%
```

`jacocoTestCoverageVerification` тримає рядки й методи на 100%, а гілки — за **кількістю**
непокритих, не за часткою: `MISSEDCOUNT` максимум 1. Рівно одну гілку в модулі не покриє
жоден тест — охоронець `question != null` у `QuestionAdminService.questionsWithAnswers`. Він будує
мапу запитань тесту, а потім проходить відповіді того самого тесту; це два запити з
однаковим `quizId` в одній read-only REPEATABLE_READ транзакції, тож запитання кожної
відповіді вже в мапі й `null` прийти не може. Частка дозволила б цій одній непомітно стати
п'ятьма; кількість каже, скільки саме недосяжних відомо, і валить збірку на наступній.

Ті самі три лічильники гейтить і `quizproject-web` через `npm run coverage:check`.
`:api:integrationTest` піднімає чисті MySQL 8.4 і Redis 8.2 через Testcontainers. Перевірки
застосовують production Flyway-міграції, доводять, що різні екземпляри API використовують один
атомарний rate limit, і що одночасне завершення однієї спроби двома запитами не може подвоїти
результат — пессимістичне блокування рядка допускає рівно одне успішне завершення.

Готовий executable JAR створюється в `api/build/libs`.

### Залежності зафіксовані локами

`dependencyLocking` увімкнено для всіх конфігурацій, а стан зберігається в `gradle.lockfile`
кореня та модуля `api`. Збірка звіряє кожну резолюцію з локом і падає, якщо вона розійшлася,
тому **не запускайте `--write-locks` у CI** — там це мовчки прийняло б те, що upstream віддає
зараз, тобто рівно те, від чого локи й захищають. Зміна залежності означає локальний
`./gradlew --write-locks` і коміт оновлених локів.

Dependabot оновлює Gradle-залежності щотижня і регенерує `gradle.lockfile` разом із версією в
`build.gradle`, включно з транзитивними змінами, тож ручний крок для його PR не потрібний.

Версія Tomcat навмисно піднята над тим, чим керує Spring Boot 4.1.1
(`ext['tomcat.version'] = '11.0.25'` в `api/build.gradle`): на керовану 11.0.24 припадають три
CRITICAL-адвізорі про обхід автентифікації та контролю доступу. Перевизначення прибирається,
щойно з'явиться реліз Boot, який сам керує 11.0.25 або новішою; передчасне прибирання помітить
Trivy-скан у `container.yml`.

## Якість коду

Workflow **SonarQube analysis** аналізує код у SonarCloud на кожен pull request і на push у
`master`. Проєкт — `vitaliilatysh_quizproject` в організації `vitaliilatysh`; SQL-міграції
виключені з аналізу (`sonar.exclusions`).

~~~bash
./gradlew :api:test :api:jacocoTestReport :api:jacocoTestCoverageVerification sonar
~~~

`sonar.qualitygate.wait=true`, тому крок чекає на вердикт quality gate і падає, якщо той не
пройдений — аналіз тут блокує, а не лише звітує. Покриття Sonar читає з
`api/build/reports/jacoco/test/jacocoTestReport.xml`, який створює `jacocoTestReport` на основі
`:api:test`. Тому workflow **навмисно не запускає** `:api:integrationTest`: його execution data
до цього звіту не потрапляє, а другий стек MySQL і Redis через Testcontainers на кожен PR коштує
часу без користі для аналізу. `:api:integrationTest` залишається в `ci.yml`.

Потрібен repository secret `SONAR_TOKEN`. Для PR від Dependabot job пропускається цілком:
такі прогони не отримують секретів репозиторію, тож `sonar` падав би на кожному bump. Гейт
покриття при цьому не втрачається — `ci.yml` виконує `:api:check` (а отже
`jacocoTestCoverageVerification`) і на цих PR теж.

## Безперервна інтеграція

Чотири workflow у `.github/workflows`:

| Workflow | Коли | Що робить |
| --- | --- | --- |
| `ci.yml` — **CI** | PR, push у `master` | `./gradlew clean :api:check :api:integrationTest :api:bootJar`, потім рендерить і перевіряє Kustomize overlays |
| `sonarqube.yml` — **SonarQube analysis** | PR, push у `master` | тести, JaCoCo-звіт, аналіз SonarCloud і quality gate |
| `container.yml` — **Backend container delivery** | PR, push у `master` | збірка образу, Trivy, smoke-перевірка проти MySQL і Redis; після `master` — публікація, SBOM і підпис cosign |
| `deploy.yml` — **Deploy** | після успішного delivery з `master`, або вручну | розгортання за digest у `staging`, у production — лише вручну через protected environment |

Java 25 і Gradle wrapper (з перевіркою wrapper) встановлює спільний composite action
`.github/actions/setup-java-gradle`, тому pinned SHA сторонніх actions живуть в одному місці.
Сторонні actions скрізь закріплені за commit SHA, а Dependabot тримає їх актуальними.

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

Публічний каталог `GET /api/v1/quizzes` і окремі тести повертають weak `ETag` та можуть
кешуватися браузером або proxy протягом однієї хвилини з обов'язковою подальшою перевіркою.
TTL задається через `PUBLIC_QUIZ_CACHE_MAX_AGE`; умовний запит з `If-None-Match` отримує
`304 Not Modified`, якщо представлення не змінилося.

Flyway автоматично перевіряє та застосовує міграції під час запуску API. Окремий ручний крок
перед стартом застосунку більше не потрібний.

`JWT_SECRET` має бути Base64-значенням щонайменше з 32 випадкових байтів. Access JWT живе 15 хвилин
(`JWT_TTL`), а opaque refresh token — 7 днів (`REFRESH_TOKEN_TTL`). Login і registration повертають
обидва токени. Refresh передається JSON-тілом `{"refreshToken":"..."}` у
`POST /api/v1/auth/refresh`; успішний обмін ротує refresh token і продовжує його sliding TTL.

У MySQL зберігається лише SHA-256 hash refresh token. Повторне використання вже ротованого token
вважається replay-атакою і відкликає всю сесію. Access JWT містить `sid`, а кожен захищений запит
перевіряє активність відповідної сесії в базі. Тому logout, зміна пароля, блокування або видалення
акаунта негайно роблять недійсними і access, і refresh token на всіх pod-ах. Після розгортання цієї
версії старі JWT без `sid` будуть відхилені — користувачеві треба один раз увійти знову.

### Змінні середовища

Повний перелік із `api/src/main/resources/application.yml`. Обов'язкова рівно одна — `JWT_SECRET`;
решта має придатні для локальної розробки значення за замовчуванням.

| Змінна | Типово | Що робить |
| --- | --- | --- |
| `JWT_SECRET` | — (**обов'язкова**) | Base64 щонайменше з 32 випадкових байтів для підпису access JWT |
| `JWT_TTL` | `PT15M` | час життя access JWT |
| `REFRESH_TOKEN_TTL` | `P7D` | sliding TTL opaque refresh token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | allowlist origin-ів для CORS |
| `DB_URL` | `jdbc:mysql://localhost:3306/tests_db?...` | JDBC URL MySQL |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / порожній | облікові дані бази |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | адреса Redis |
| `REDIS_PASSWORD` | порожній | пароль Redis |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_TIMEOUT` | `2s` / `2s` | таймаути клієнта Redis |
| `REDIS_HEALTH_ENABLED` | `true` | чи входить Redis у health-перевірку |
| `READINESS_HEALTH_INDICATORS` | `readinessState,db,redis` | склад readiness-групи |
| `RATE_LIMIT_BACKEND` | `redis` | `redis` або `memory` (лише один екземпляр API) |
| `API_RATE_LIMIT_REQUESTS` | `120` | ліміт запитів до API у вікні |
| `LOGIN_RATE_LIMIT_REQUESTS` | `5` | ліміт спроб входу у вікні |
| `API_RATE_LIMIT_WINDOW` | `PT1M` | розмір вікна rate limit |
| `API_RATE_LIMIT_MAX_CLIENTS` | `10000` | максимум відстежуваних клієнтів у in-memory backend |
| `TRUSTED_PROXY_CIDRS` | `127.0.0.1/32,::1/128` | мережі proxy, яким довіряють `X-Forwarded-For` |
| `API_PORT` | `8081` | порт API |
| `MANAGEMENT_PORT` | `9081` | окремий порт actuator |
| `SHUTDOWN_TIMEOUT` | `20s` | ліміт graceful shutdown |
| `HTTP_COMPRESSION_ENABLED` | `true` | gzip для великих відповідей |
| `HTTP_COMPRESSION_MIN_RESPONSE_SIZE` | `1KB` | поріг компресії |
| `PUBLIC_QUIZ_CACHE_MAX_AGE` | `PT1M` | TTL кешу публічного каталогу |
| `LOG_FORMAT` | `logstash` | формат структурованих console logs |

## Основні маршрути

- `POST /api/v1/auth/login` — вхід;
- `POST /api/v1/auth/register` — реєстрація та отримання пари access/refresh;
- `POST /api/v1/auth/refresh` — ротація refresh token і отримання нової пари;
- `POST /api/v1/auth/logout` — відкликання поточної сесії;
- `GET /api/v1/users/me` — профіль;
- `PUT /api/v1/users/me/password` — зміна пароля;
- `GET /api/v1/quizzes` — список тестів (з `search`, `complexity` і пагінацією);
- `GET /api/v1/quizzes/summary` — `totalQuizzes` і `totalSubjects` для зведення;
- `GET /api/v1/quizzes/{id}` — один тест;
- `POST /api/v1/quizzes/{id}/attempts` — початок спроби;
- `GET /api/v1/attempts/{id}` — поточна спроба;
- `POST /api/v1/attempts/{id}/complete` — завершення спроби;
- `GET /api/v1/results/me` — результати користувача;
- `/api/v1/admin/**` — адміністративні операції;
- `/swagger-ui.html` — інтерактивна OpenAPI-документація.

Actuator слухає окремий порт (`MANAGEMENT_PORT`, типово `9081`), а не порт API
(`API_PORT`, типово `8081`). `/actuator/prometheus` мусить лишатися відкритим, бо
scraper не має чим автентифікуватися, і віддає він весь реєстр — лічильники
автентифікації, версію JVM, пул з'єднань, серію на кожен URI API. Тому цей порт не
опублікований у Service `quiz-api`: до нього дістаються kubelet (проби), Prometheus
(через headless Service `quiz-api-management`) і оператор через `kubectl port-forward`.

- `/actuator/health` — стан застосунку;
- `/actuator/metrics` — метрики, доступні лише адміністратору;
- `/actuator/prometheus` — endpoint для Prometheus scrape.

Помилки повертаються однією формою — `{timestamp, status, error, message, path}` — для всіх
випадків: валідація, 404, 405, 415, конфлікти та непередбачені збої (500). `message` для 500
фіксований (`Unexpected server error`), а сам виняток іде в лог під тим самим `X-Correlation-ID`,
що й у відповіді.

Захищені маршрути приймають `Authorization: Bearer <token>`. Адміністративні операції
доступні лише ролі `ADMIN`.

Колекції тестів, власних результатів та адміністративних тестів, користувачів і результатів
підтримують необов'язкові zero-based параметри `page` і `size` (`size` від 1 до 100, типово 20,
коли пагінацію ввімкнено). Без цих параметрів зберігається попередня поведінка з повним JSON-масивом.
Відповідь залишається масивом, а metadata повертається в `X-Page-Number`, `X-Page-Size`,
`X-Total-Count` і `X-Total-Pages`, тому наявний React-клієнт не потребує одночасного оновлення.

`GET /api/v1/quizzes` додатково приймає `search` і `complexity`:

- `search` (до 50 символів) шукає підрядок у назві тесту **або** назві предмета, без урахування
  регістру. Символи `%` і `_` трактуються як звичайний текст, а не як шаблони LIKE;
- `complexity` (до 25 символів) звужує за міткою рівня **як вона збережена** — `low`, `medium`,
  `high`, `advanced` — і може повторюватися, щоб прийняти кілька: `?complexity=high&complexity=advanced`.

Обидва застосовуються базою даних, а не клієнтом. Це принципово для пагінованого ендпоінта:
відфільтрувати одну завантажену сторінку означало б мовчки приховати всі збіги на інших сторінках.

API навмисно не приймає узагальнені категорії на кшталт «easy/medium/hard». Такі групування
належать конкретному інтерфейсу, а мітки рівнів у базі — закритий набір із чотирьох значень.
Клієнт, який показує три кнопки, надсилає відповідні мітки сам.

`GET /api/v1/quizzes/summary` повертає `totalQuizzes` і `totalSubjects` для клієнтів, які показують
зведення, не завантажуючи весь каталог. Пагінований список на це відповісти не може: `X-Total-Count`
каже, скільки тестів збігається, але не скільки різних предметів вони охоплюють.

`totalSubjects` рахує предмети, які мають **хоча б один тест**, а не всі рядки таблиці `subjects`.
Саме так це число рахувалося на клієнті, тож підрахунок таблиці мовчки завищив би показник.

## Міграції бази даних

Production-міграції знаходяться в `api/src/main/resources/db/migration` і входять до JAR:

- `V1__baseline.sql` створює початкову схему та довідники;
- `V2__secure_attempts.sql` посилює зберігання паролів, спроб і відповідей;
- `V3__index_paginated_queries.sql` додає складені індекси для швидких сторінок власних та
  адміністративних результатів у порядку від найновіших;
- `V4__snapshot_attempt_questions.sql` фіксує склад запитань на момент початку спроби;
- `V5__invalidate_tokens_on_password_change.sql` додає історичну мітку зміни пароля;
- `V6__refresh_sessions.sql` додає серверні refresh-сесії з ротацією та відкликанням.

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
`deploy/kubernetes/overlays/local`, `deploy/kubernetes/overlays/staging` і
`deploy/kubernetes/overlays/production`. Вони створюють:

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

## Розгортання

Workflow **Deploy** застосовує те, що опублікував container delivery. Він запускається сам після
успішної публікації образу з `master` і розгортає у `staging`; production розгортається лише
вручну (`workflow_dispatch`) і через protected environment, тобто з підтвердженням людини.

Розгортається **digest, а не тег**. Тег `sha-<commit>` резолвиться в digest один раз, підпис
cosign перевіряється саме над цим digest, і всі подальші кроки посилаються тільки на нього —
переспрямований тег не може підмінити образ між перевіркою підпису й `kubectl apply`. Підпис,
який ніхто не перевіряє в момент розгортання, був би декоративним.

Порядок кроків: перевірка готовності namespace → резолв digest → перевірка підпису →
`kubectl apply --dry-run=server` проти живого кластера → `apply` → `rollout status` →
перевірка, що Service має готовий endpoint. Якщо rollout не піднявся, workflow друкує події та
логи подів і робить `rollout undo` (за наявності попередньої ревізії).

### Що потрібно налаштувати один раз

GitHub secrets (значення не зберігаються в репозиторії й не потрапляють у логи):

| Secret | Що це |
| --- | --- |
| `OCI_CLI_USER` | OCID користувача OCI |
| `OCI_CLI_TENANCY` | OCID тенансі |
| `OCI_CLI_FINGERPRINT` | fingerprint API-ключа |
| `OCI_CLI_KEY_CONTENT` | приватний API-ключ у PEM |
| `OCI_CLI_REGION` | регіон, наприклад `eu-frankfurt-1` |
| `OKE_CLUSTER_OCID` | OCID кластера OKE |

Окремо від розгортання workflow **SonarQube analysis** потребує `SONAR_TOKEN`; без нього job не
запуститься, але гейт покриття в `ci.yml` продовжує працювати.

GitHub environments: `staging` (без обмежень) і `production` (required reviewers — саме це
робить розгортання в production свідомою дією).

Namespace і секрети додатків створюються **руками, один раз**, і workflow відмовляється
розгортати, доки їх немає — краще зупинитися з поясненням, ніж підняти поди, що падатимуть на
відсутньому секреті. Тримати пароль бази в GitHub і переливати його в кластер щопрогону було б
гіршим компромісом:

~~~bash
kubectl create namespace quizproject-staging
cp deploy/kubernetes/backend/secret.env.example deploy/kubernetes/backend/secret.env
# замініть кожен плейсхолдер, потім:
kubectl create secret generic quiz-api-secrets \
  --namespace quizproject-staging \
  --from-env-file=deploy/kubernetes/backend/secret.env
~~~

Staging живе в тому ж кластері, що й production, в окремому namespace `quizproject-staging`.
Від production він відрізняється лише там, де мусить: одна репліка, без HorizontalPodAutoscaler,
власний CORS-хост і PodDisruptionBudget із `maxUnavailable: 1` замість `minAvailable: 1` —
останнє при одній репліці назавжди заблокувало б drain вузла, а вузли тут спільні з production.
Проби, ресурси й security context навмисно однакові: інакше staging перестає передбачати
production.

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
- legacy-паролі з відкритим текстом (стара схема мала `password VARCHAR(15)`, а `V2` лише розширив
  колонку, не конвертуючи значень) перехешовуються під час першого ж успішного входу, тож із
  перенесеної бази вони вимиваються самі, а не лишаються назавжди;
- CORS використовує allowlist із `CORS_ALLOWED_ORIGINS`;
- refresh token зберігається лише як SHA-256 hash, ротується після кожного обміну й має replay-захист;
- logout, зміна пароля та блокування акаунта негайно відкликають серверні сесії;
- login та API мають окремі атомарні Redis rate limits, спільні для всіх pod-ів;
- IP клієнта визначається справа наліво через ланцюжок лише довірених proxy;
- відмова Redis закриває доступ контрольованою відповіддю `503`;
- помилки `401`, `403` і `429` повертаються в одному JSON-форматі;
- завершення спроби перевіряє власника, термін дії та допустимі відповіді;
- секрети не зберігаються в репозиторії.
