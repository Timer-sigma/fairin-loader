# 🚀 Гайд: выложить сайт Fairin на GitHub Pages

Сайт лежит в папке `site/` и полностью статический (HTML+CSS+PNG) — ничего собирать не нужно.

## Шаг 0. Замените имя репозитория в ссылках
В `site/index.html` замените `YOUR-NAME` на свой логин GitHub (ссылки в «Скачать» и в футере).
Например: `https://github.com/Fairin/fairin-loader`.

## Шаг 1. Создайте репозиторий
1. Зайдите на https://github.com/new
2. Repository name: `fairin-loader` (или любой).
3. Public (для бесплатного Pages), без галочки README (он уже есть).
4. Create repository.

## Шаг 2. Запушьте проект
В папке `C:\Fairin\fairin-loader`:

```bash
git init
git add .
git commit -m "Fairin Loader: ядро, инсталлер, сервер, сайт"
git branch -M main
git remote add origin https://github.com/YOUR-NAME/fairin-loader.git
git push -u origin main
```

> `.gitignore` уже настроен: `build/`, `.gradle/`, логи и т.п. не уйдут в репозиторий.
> exe/jar лучше выкладывать в Releases, но можно положить и в репозиторий.

## Шаг 3. Включите GitHub Pages
1. Откройте **Settings → Pages** (вкладка слева).
2. **Source**: `Deploy from a branch`.
3. **Branch**: `main`, папка **`/site`** ← важно, сайт лежит в подпапке!
4. Save.

Через 1–2 минуты сайт будет доступен по адресу:
```
https://YOUR-NAME.github.io/fairin-loader/
```

## Альтернатива: корень репозитория с именем `<login>.github.io`
Если создать репозиторий с именем **`YOUR-NAME.github.io`**, то сайт будет на
`https://YOUR-NAME.github.io/` (без `/fairin-loader/`). В этом случае в Settings → Pages
выберите Branch `main` + папку `/ (root)`, либо просто скопируйте содержимое `site/` в корень.

## Обновление сайта
Правки в `site/` → `git add . && git commit -m "site update" && git push`.
GitHub Pages пересоберёт сайт автоматически (следите за вкладкой **Actions**).

## Свой домен (по желанию)
1. Купите домен у любого регистратора.
2. Settings → Pages → **Custom domain**: впишите `fairin.example.com`.
3. У регистратора добавьте CNAME-запись: `fairin` → `YOUR-NAME.github.io`.
4. Подождите DNS (до 24 ч), включите **Enforce HTTPS**.

## Частые проблемы
| Симптом | Причина / решение |
|---|---|
| 404 на сайте | Pages смотрит не на `/site`; проверьте Settings → Pages → Branch: `main` + `/site` |
| Иконки не грузятся | Пути должны быть относительными (`assets/logo.png`) — так и сделано |
| Сайт не обновляется | Кэш браузера / подожди 1–2 мин; статус — во вкладке Actions |
| Stylesheet 404 | Регистр имён файлов: `style.css` — точно в нижнем регистре |
