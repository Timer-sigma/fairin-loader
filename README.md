# Fairin Loader

Лёгкий (lightweight) загрузчик модов для Minecraft **1.14 → 1.26.x** (включая новые
date-based версии 26.1+). Работает поверх **официальных Mojang-маппингов** — ядро
лоадера не содержит хардкода версий, поэтому поддерживает и будущие релизы.

- **Клиент**: профиль для ванильного лаунчера (как Fabric) — GUI-инсталлер `FairinInstaller.exe`
- **Сервер**: `fairin-server-launch.jar` + `start.bat` (bundler 1.18+ и fat jar 1.14–1.17, `server_mappings`)
- **Сайт**: статический, готов к GitHub Pages — см. [PAGES-GUIDE.md](PAGES-GUIDE.md), файлы в `site/`


## Как это работает

```
ванильный лаунчер
   └─> профиль fairin-<version> (mainClass = com.fairin.loader.launch.FairinLauncher)
          ├─ 1. резолв version JSON (inheritsFrom → ваниль)
          ├─ 2. докачка библиотек/клиента при нехватке
          ├─ 3. ОДИН раз на версию: remap обф-клиента в официальные имена
          │      (client.txt) + вплетение хуков  →  кэш fairin-cache/remapped-<v>.jar
          ├─ 4. скан mods/ (@FairinMod через ASM, без загрузки классов)
          ├─ 5. инициализация модов + @OnInit
          └─ 6. запуск net.minecraft.client.main.Main
```

- **Remap**: ASM `ClassRemapper`, obf → official. Резолв унаследованных методов/полей
  по иерархии классов (BFS по супер-классам и интерфейсам) + корректный remap
  `invokedynamic` MethodHandle-констант (record/lambda bootstrap'ы).
- **Хуки**: дата-дривен из `fairin-hooks.json` (ресурс лоадера). По умолчанию:
  `EventBus.fireTick()` → хвост `Minecraft.runTick(Z)V`. Вплетение — только в
  stack-neutral точки (HEAD метода / перед RETURN void-метода), фреймы не ломаются.
- **Моды**: обычные jar'ы в `.minecraft/mods/` с одним классом, помеченным
  `@com.fairin.api.FairinMod`.

## Сборка

```bat
C:\Fairin\tools\gradle-8.10.2\bin\gradle.bat build
```

Артефакты: `loader/build/libs/fairin-loader-0.1.0.jar` (самодостаточный: ASM вшит),
`example/build/libs/example-0.1.0.jar` (пример мода).

## Сервер (Fabric-style)

```
Installer GUI → режим Server → папка + версия → Install
```
Создаётся папка сервера:
```
fairin-server-launch.jar   # тонкий лаунчер (Class-Path на fairin/fairin-loader-*.jar)
fairin-server.json         # { "mcVersion": "1.21.11" }
start.bat / start.sh       # запуск (java -Djava.net.preferIPv4Stack=true -jar fairin-server-launch.jar nogui)
eula.txt                   # поставьте eula=true
mods/                      # серверные моды
```
ServerLauncher сам: скачает server.jar Mojang, распакует bundler (1.18+: inner jar + 39+ библиотек),
ремапнет по `server_mappings` (кэш `fairin-cache/remapped-server-<v>.jar`), загрузит `mods/`,
вызовет `net.minecraft.server.Main`.

## Сборка .exe-инсталлера

1. `gradle :loader:jar` — jar (ASM вшит внутрь, Main-Class = Installer GUI).
2. PNG→ICO: см. скрипт в README (System.Drawing → ICO-контейнер).
3. [launch4j](https://repo1.maven.org/maven2/net/sf/launch4j/launch4j/3.50/) (`-core.jar` + `-workdir-win32.jar`):
   `java -cp launch4j.jar;lib/* net.sf.launch4j.Main fairin-installer.xml` → `dist/FairinInstaller.exe`.

## Сайт (GitHub Pages)

Статический сайт в `site/` (index.html + style.css + assets из иконок проекта).
Инструкция по выгрузке — [PAGES-GUIDE.md](PAGES-GUIDE.md) (в Settings → Pages выбрать
Branch `main`, папку `/site`).


## Установка (ванильный лаунчер)

GUI (рекомендуется): запустите `dist/FairinInstaller.exe` → режим Client → версия → Install.

Консоль:
```bat
java -jar fairin-loader-0.1.0.jar --mc 1.21.11
```

Затем выбрать профиль **fairin-1.21.11** в лаунчере. Инсталлятор кладёт jar лоадера
в `libraries/com/fairin/...` и создаёт `versions/fairin-1.21.11/fairin-1.21.11.json`.

> Флаг `-Djava.net.preferIPv4Stack=true` включён в профиль автоматически (Java на
> некоторых системах зависает на IPv6 при обращении к piston-meta).

## API модов

```java
@FairinMod(id = "example", name = "Fairin Example", version = "0.1.0")
public class ExampleMod {
    @OnInit
    public void init() { ... }              // до открытия окна игры

    @OnTick
    public void tick() { ... }              // каждый клиентский тик
}
```

Модам доступны **официальные имена классов Minecraft** (вся игра ремапнута в
Mojang-имена): `net.minecraft.client.Minecraft.getInstance()` и т.д.

## Статус / ограничения

- ✅ Клиент 1.14.4+ (маппинги Mojang доступны с 1.14.4); версии без `client_mappings`
  запускаются без remap (моды без хуков в игровые классы).
- ✅ Сервер 1.14.4+ (bundler 1.18+ и fat jar 1.14–1.17), `server_mappings`.
- ✅ GUI + консольный инсталлер (клиент и сервер), `FairinInstaller.exe` через launch4j.
- ✅ Проверено на реальном запуске 1.21.11: окно игры, тики мода, сервер до `Done`.
- ⏳ Кэш remap в несколько потоков, разрешение зависимостей между модами,
  серверные хуки (аналог @OnTick для DedicatedServer.tickServer).
- Remap-кэш валидируется по размеру/mtime исходного jar и числу хуков.

## Структура

```
loader/src/main/java/com/fairin/
  api/       FairinMod, OnInit, OnTick, EventBus (MethodHandle-dispatch)
  loader/
    launch/    FairinLauncher (mainClass профиля)
    server/    ServerLauncher, Bundler (выделенный сервер)
    version/   VersionJson (inheritsFrom, библиотеки, client/server jar)
    mapping/   Mappings (client.txt/server.txt), FairinRemapper, Hierarchy, TypeDesc
    transform/ ClassProcessor, HookInjector, RemapPipeline (кэш)
    mods/      ModScanner, ModInstance
    installer/ Installer (CLI+GUI entry), GuiInstaller, ManifestHelper
    Boot, FairinAgent (опциональный -javaagent режим), TinyJson
site/          статический сайт для GitHub Pages (assets из C:\Fairin\Icons)
dist/          FairinInstaller.exe
```
