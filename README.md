# Лабораторна робота 3 — Обробка безпекових подій

Робота з дисципліни «Технології розробки на платформі Java». Сервіс `SecurityLogAnalyzer` аналізує скінченні й нескінченні потоки безпекових подій засобами Java Stream API, `Optional` та власного `Gatherer`.

Виконав: **Пляцок Владислав, група ІО-41**.

## Варіант

Номер залікової книжки **N = 4115**.

```text
V = N mod 3 = 4115 mod 3 = 2
```

Варіант 2: знайти першу подію `CRITICAL`, відібрати префікс журналу до дедлайну та виявити неперервні серії подій від однієї IP-адреси.

## Вміст

- [Звіт до лабораторної роботи](Пляцок_Владислав_ІО-41_Java_lab3.docx).
- [Java-проєкт з Gradle Wrapper і тестами](lab3-security-streams/).
- [Докладний опис API, алгоритму та перевірок](lab3-security-streams/README.md).

## Запуск

Потрібен **JDK 25 або новіший**. Gradle Wrapper 9.7.0 включено до проєкту. Версії JUnit 5.13.4 та JaCoCo 0.8.14 визначені у Version Catalog. Під час першого запуску потрібен інтернет для завантаження Gradle та залежностей.

Виконуйте команди з кореня репозиторію.

Windows PowerShell:

```powershell
cd lab3-security-streams
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat run
```

Linux або macOS:

```sh
cd lab3-security-streams
./gradlew clean build
./gradlew test
./gradlew run
```

Усі **29 тестів JUnit 5** пройдено. Тести використовують справжні нескінченні `Stream.iterate` і `Stream.generate`, таймаути та лічильники спожитих подій. Для класу `SecurityLogAnalyzer` покриття JaCoCo становить 100% рядків, гілок та інструкцій.

Для здачі прикріпіть посилання на цей публічний репозиторій у Classroom.
