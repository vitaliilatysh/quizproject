# Quiz Project

Вебзастосунок для створення та проходження тестів. Студенти можуть обирати тести, відповідати на запитання й переглядати результати, а адміністратори — керувати предметами, тестами та запитаннями.

## Технології

- Java 25
- Java Servlet API 4.0 (`javax.servlet`) і JSP
- Gradle 9.6.1
- MySQL 8
- JUnit 4 та Mockito

## Збірка і тестування

```bash
./gradlew clean test war
```

У Windows використовуйте `gradlew.bat`. Готовий WAR-файл буде створено в каталозі `build/libs`.

## Запуск

1. Створіть базу даних за допомогою `resources/create_tests_db.sql`.
2. За потреби змініть параметри підключення до MySQL у `web/META-INF/context.xml`.
3. Розгорніть WAR-файл у контейнері сервлетів, сумісному із Servlet API 4.0, наприклад Apache Tomcat 9.

Для кожного pull request і push у `master` GitHub Actions автоматично запускає збірку, тести та створення WAR-файлу.
