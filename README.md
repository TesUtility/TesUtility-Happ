# TesUtility (Happ / Incy Style VLESS Reality VPN)

<p align="center">
  <img src="app/src/main/res/drawable/tesutility_logo_1791695229555.jpg" width="160" alt="TesUtility Logo" style="border-radius: 28px;" />
</p>

<p align="center">
  <strong>Современный высокопроизводительный VLESS Reality VPN клиент для Android в эстетике Happ и Incy.</strong>
</p>

---

## 🚀 Особенности

- **Дизайн в стиле Happ / Incy**:
  - Анимированная крупная кнопка питания с неоновым пульсирующим кольцом состояния.
  - Карточки серверов с флагами стран, замером задержки (ping ms) и типом протокола.
  - Панель мониторинга трафика: скорость загрузки/отдачи (KB/s, MB/s), время сессии и суммарный объём.
  - Тёмная неоновая тема OLED Obsidian (`#090D14` с акцентами Electric Cyan и Emerald).

- **Поддержка протоколов VLESS Reality**:
  - `VLESS` + `Reality` с шифрованием `none`.
  - Поток `xtls-rprx-vision`.
  - Транспорты: `tcp`, `grpc`, `ws`.
  - Поддержка Reality параметров: `pbk` (публичный ключ), `sid` (Short ID), `sni` (Server Name), `fp` (fingerprint), `spx` (SpiderX).
  - Генерация полной конфигурации Xray JSON с объектами `streamSettings.realitySettings`.

- **QR-коды и буфер обмена**:
  - Мгновенный импорт конфигураций из буфера обмена в один тап.
  - Сканирование QR-кодов из изображений галереи.
  - Генерация высокочётких QR-кодов для любой конфигурации с возможностью поделиться ссылкой.
  - Поддержка схемы `vless://` на уровне системы.

- **Маршрутизация и безопасность**:
  - Режимы маршрутизации: «Обход РФ и LAN», «Глобальный прокси», «По правилам».
  - AdGuard DNS фильтрация рекламы и трекеров.
  - DNS резолверы: Cloudflare (1.1.1.1), Google (8.8.8.8), Quad9 (9.9.9.9), AdGuard DNS.
  - Kill Switch (блокировка интернета при разрыве).
  - Консоль логов ядра `libv2ray` в реальном времени.

- **Системный VPN**:
  - Полноценная служба `VlessVpnService` на базе `android.net.VpnService`.
  - Уведомление в шторке с кнопкой быстрого отключения.
  - Реальный замер задержки через прямое TCP-подключение к хосту и порту.

---

## 🛠 Архитектура

- **Язык**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **База данных**: Room Persistence Library (`AppDatabase`, `VlessDao`)
- **Ядро**: `libv2ray.aar` (`Xray-core` с поддержкой Reality и XTLS-Vision)
- **Сборка**: Gradle Kotlin DSL

---

## 📦 Сборка и установка

```bash
# Клонирование репозитория
git clone https://github.com/diniksss55570/TesUtility-Happ.git
cd TesUtility-Happ

# Сборка Debug APK
gradle assembleDebug

# Запуск тестов
gradle testDebugUnitTest
```

Готовый APK будет находиться по пути:
`app/build/outputs/apk/debug/app-debug.apk`
