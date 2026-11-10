# Лабораторна робота 3 — Обробка безпекових подій

Сервіс `SecurityLogAnalyzer` аналізує скінченні та нескінченні потоки подій за допомогою ледачих операцій Java Stream API, `Optional` і власного `Gatherer`. Проєкт до дисципліни «Технології розробки на платформі Java».

## Варіант

Номер залікової книжки **N = 4115**.

```text
V = N mod 3
4115 = 3 × 1371 + 2
V = 2
```

Варіант 2 — обробка безпекових подій: пошук першої критичної події, вибірка префікса до дедлайну та виявлення неперервних серій від однієї IP-адреси.

## Середовище

- JDK **25** або новіший; компіляція з `--release 25`.
- Gradle Wrapper **9.7.0** включено в проєкт разом із SHA-256 перевіркою дистрибутива.
- JUnit **5.13.4** та JaCoCo **0.8.14** визначено в `gradle/libs.versions.toml`.
- Під час першого запуску потрібен інтернет для завантаження Gradle та залежностей.

Використовується стабільний API `java.util.stream.Gatherer`, доступний з Java 24. Прапорець `--enable-preview` не потрібен. Усі класи розташовано в пакеті `ua.kpi.comsys.streams`.

## API та поведінка

Модель події:

```java
public record SecurityLog(
        String ipAddress,
        String severityLevel,
        String eventDescription,
        LocalDateTime timestamp) { }
```

Методи сервісу:

```java
public Optional<SecurityLog> findFirstCriticalEvent(Stream<SecurityLog> stream)
public List<SecurityLog> getLogsBeforeDeadline(
        Stream<SecurityLog> stream, LocalDateTime deadline)
public List<String> detectIpBursts(
        Stream<SecurityLog> stream, int burstThreshold, int maxIpsToReport)
```

`findFirstCriticalEvent` застосовує `filter` і `findFirst`, шукаючи точний рівень `"CRITICAL"` з урахуванням регістру. Для скінченного потоку без збігів повертається `Optional.empty()`. Результат у демонстрації оброблено через `map` та `orElse`, без небезпечного `Optional.get()`.

`getLogsBeforeDeadline` використовує `takeWhile`: повертає початкові події, для яких `timestamp.isBefore(deadline)`. Подія, час якої дорівнює дедлайну або перевищує його, зчитується для перевірки, але до результату не потрапляє. Після неї обробка зупиняється. Вхід має бути впорядкований за неспадним часом; це вибірка префікса, а не пошук усіх пізніших подій із меншим часом.

`detectIpBursts` створює власний `Gatherer.ofSequential` зі станом: поточна IP-адреса та довжина її серії. На зміні IP лічильник обнуляється. IP потрапляє у звіт одразу на досягненні `burstThreshold`, лише один раз за одну неперервну серію. Та сама IP може бути у звіті повторно після серії іншої IP. Лічильник насичується на порозі, тому довга серія не призводить до переповнення. `limit(maxIpsToReport)` обмежує кількість результатів; значення `false` від `downstream.push` передається інтегратором назад для припинення зчитування джерела.

Усі методи перевіряють обов'язкові аргументи та кидають `IllegalArgumentException` для `null`-потоку, `null`-дедлайну або непозитивних `burstThreshold` і `maxIpsToReport`. Події та їхні потрібні поля мають містити коректні ненульові значення; модель `record` не виконує окремої валідації полів. Повернені списки незмінні (`Stream.toList()`).

Сервіс обробляє події послідовно через `sequential()`. Для визначення першої події, префікса та серій порядок є частиною змісту даних: використовуйте впорядковане джерело, наприклад `Stream.iterate`. `Stream.generate` формально створює невпорядкований потік; у тестах він використовується для постійної однакової події, де порядок не впливає на результат. Кожен потік можна використати лише один раз; закриттям джерел вводу-виводу керує викликач через `try-with-resources`.

### Умова завершення нескінченних джерел

Коротке замикання завершує аналіз, коли за скінченну кількість кроків знайдено критичну подію, досягнуто дедлайн або сформовано потрібну кількість звітів про серії. Якщо нескінченний потік ніколи не містить потрібної події або умови зупинки, гарантувати завершення неможливо. Наприклад, `limit(2)` після Gatherer не завершить нескінченну серію однієї IP, яка створює лише один звіт. Для таких джерел викликач має задати скінченну межу входу (`stream.limit(...)`) або іншу зовнішню умову завершення.

### Складність

Нехай K — кількість фактично прочитаних подій. Кожен метод має часову складність O(K). Пошук використовує O(1) додаткової пам'яті, вибірка до дедлайну — O(R) для R повернених подій, виявлення серій — O(1) для стану Gatherer та O(M) для M звітів, де M ≤ maxIpsToReport. Увесь нескінченний потік у пам'яті не зберігається.

## Запуск

З кореня цього проєкту в PowerShell:

```powershell
.\gradlew.bat clean build
.\gradlew.bat test --rerun-tasks
.\gradlew.bat run
```

Linux або macOS:

```sh
chmod +x gradlew
./gradlew clean build
./gradlew test --rerun-tasks
./gradlew run
```

`build` компілює код і запускає тести. Для нескінченних джерел тести мають таймаут `@Timeout(2)` та обмежують допустиму кількість викликів генератора, перевіряючи фактичне коротке замикання.

### Перевірені результати

10 листопада 2026 року на Windows з Oracle JDK **25.0.1** команда `clean build run` завершилася успішно. Усі **29 тестів** пройдено без помилок і пропусків. Для класу `SecurityLogAnalyzer` JaCoCo підтвердив **32/32 рядки, 12/12 гілок та 113/113 інструкцій** — 100% за кожною з цих метрик. Покриття наведено саме для класу сервісу, а не для демонстраційної програми.

Тести охоплюють порожні та скінченні потоки, точне порівняння рівня критичності, межу дедлайну, неперервність серій, повторну IP після зміни адреси, поріг 1, неповну останню серію, незалежність стану між викликами, аргументи `null` і непозитивні параметри. Нескінченні `Stream.iterate` та `Stream.generate` перевіряються лічильниками споживання; тести підтверджують, що пошук і Gatherer не зчитують зайвих подій, а `takeWhile` зчитує одну подію для перевірки межі.

Після запуску створюються:

- JUnit HTML: `build/reports/tests/test/index.html`.
- JUnit XML: `build/test-results/test/`.
- JaCoCo HTML: `build/reports/jacoco/test/html/index.html`.
- JaCoCo XML: `build/reports/jacoco/test/jacocoTestReport.xml`.
- JAR: `build/libs/lab3-security-streams-1.0.0.jar`.

## Демонстрація

`SecurityLogDemo` створює новий нескінченний `Stream.iterate` для кожного виклику; IP змінюється кожні три події, а подія з індексом 5 є критичною. Початковий час фіксований, тому результат відтворюється:

```text
First CRITICAL: 198.51.100.20 @ 2026-11-10T10:00:05
Events before 2026-11-10T10:00:04: 4
First 3 bursts (threshold 3): [192.0.2.10, 198.51.100.20, 192.0.2.10]
Empty input: No critical event
```

## Структура

```text
lab3-security-streams/
├── build.gradle
├── settings.gradle
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── src/
    ├── main/java/ua/kpi/comsys/streams/
    │   ├── SecurityLog.java
    │   ├── SecurityLogAnalyzer.java
    │   └── SecurityLogDemo.java
    └── test/java/ua/kpi/comsys/streams/
        └── SecurityLogAnalyzerTest.java
```

## Репозиторій та здача

Публічний репозиторій: [L3nsss/lab3-security-streams](https://github.com/L3nsss/lab3-security-streams).

Проєкт міститься в підпапці `lab3-security-streams`, а [звіт до лабораторної роботи](../Пляцок_Владислав_ІО-41_Java_lab3.docx) — у корені репозиторію. Наведені вище команди Gradle виконуються з цієї підпапки.

Прикріпіть посилання на публічний репозиторій у Classroom відповідно до завдання.

## Документація

- [Java 25 Stream API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Stream.html).
- [Java 25 Gatherer API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Gatherer.html).
