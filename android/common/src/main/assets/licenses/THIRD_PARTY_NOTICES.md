# Сторонние компоненты

MIT-лицензия в корне распространяется на собственный код SoraCast. Она не заменяет лицензии сторонних файлов. Существующие copyright-заголовки в исходниках сохраняются.

| Компонент | Использование | Лицензия и источник |
| --- | --- | --- |
| WebRTC Android Java API | `android/runtime/src/main/java/org/webrtc/` | BSD-3-Clause, [текст](licenses/WebRTC-BSD.txt), [upstream](https://webrtc.googlesource.com/src/+/main/LICENSE) |
| JNI Zero Java runtime | `android/runtime/src/main/java/org/jni_zero/` | BSD-3-Clause, [текст](licenses/JNI-Zero-BSD.txt), [upstream](https://chromium.googlesource.com/chromium/src/+/main/third_party/jni_zero/LICENSE) |
| Изменения WebRTC из ScreenStream | Поставщик согласованных Java/JNI и двух native-патчей | MIT для собственного кода ScreenStream, [текст](licenses/ScreenStream-MIT.txt), [закреплённый исходник](https://github.com/dkrivoruchko/ScreenStream/tree/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a/webrtc-runtime) |
| ZXing Core 3.5.3 | Чтение и создание QR-кодов | Apache-2.0, [текст](licenses/ZXing-Apache-2.0.txt), [исходники](https://github.com/zxing/zxing/tree/zxing-3.5.3/core) |
| AndroidX Annotation 1.9.1 | `compileOnly`, аннотации при сборке | Apache-2.0, [текст](licenses/ZXing-Apache-2.0.txt), [исходники AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/annotation/) |
| Gradle Wrapper 8.13 | Запуск инструмента сборки | Apache-2.0, [текст](licenses/ZXing-Apache-2.0.txt), [Gradle 8.13](https://github.com/gradle/gradle/tree/v8.13.0) |

JUnit 4.13.2 используется только для тестов и не входит в приложение. Его исходники или JAR не поставляются с этим деревом; зависимость разрешается сборщиком. SDK, JDK и прочие инструменты сборки устанавливаются отдельно.

Дополнительно сохранены [патентный grant WebRTC](licenses/WebRTC-PATENTS.txt), [список авторов WebRTC](licenses/WebRTC-AUTHORS.txt) и [NOTICE репозитория ZXing](licenses/ZXing-NOTICE.txt). ZXing NOTICE сохранён целиком как уведомление upstream; это не утверждение о включении всех перечисленных там компонентов в `core`.

## Происхождение WebRTC runtime

Java-исходники и готовые native-файлы взяты из **одной** версии [ScreenStream](https://github.com/dkrivoruchko/ScreenStream) — commit `3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a`.

[README этого runtime](https://github.com/dkrivoruchko/ScreenStream/blob/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a/webrtc-runtime/README.md) заявляет WebRTC `150.0.7871.63`, branch `refs/branch-heads/7871`, upstream commit `1f975dfd761af6e5d76d28333191973b258d82a8`. Это сведения поставщика; независимая native-пересборка и подтверждение всего его build graph не выполнялись.

ScreenStream документирует два native-патча: `rtp-sender-generate-keyframe.patch` и `audio-record-data-callback.patch`. SoraCast дополнительно меняет Java-путь создания AudioRecord для захвата внутреннего звука без микрофона. Native `.so` не изменялись.

Четыре `libjingle_peerconnection_so.so` получаются отдельно скриптом `tools/fetch-runtime.ps1` из:

```text
https://raw.githubusercontent.com/dkrivoruchko/ScreenStream/3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a/webrtc-runtime/src/main/jniLibs/<ABI>/libjingle_peerconnection_so.so
```

Ожидаемые SHA-256 из закреплённого README:

| ABI | SHA-256 |
| --- | --- |
| armeabi-v7a | `08fc563e06d308687bf207aee609dd3d8c663af67980bdb4bde0491642b47931` |
| arm64-v8a | `57784faf0d9fed384673b799f870f44818cec590c557883fd3abe696ab59847c` |
| x86 | `9fd7ec63b8a1d62a940aaa931536c77cd86d7c9b6d353c70fe3c4c638759b3f9` |
| x86_64 | `d5120ecc7f314a1573db61caf02a53e0dbae0666ad9cd6585c743d25758432fc` |

## Статус уведомлений для native-бинарников

Для поставляемого runtime подготовлен [консервативный пакет уведомлений](licenses/NATIVE_NOTICES.md): 43 полных текста из upstream, включая криптографию, медиакодеки, аудиопроцедуры и toolchain. Основной набор взят по точным ревизиям закреплённого WebRTC DEPS и карте лицензий его генератора. Все тексты сохранены без сокращения; URL, HTTP-статусы и SHA-256 записаны рядом.

Официальная [сборка Android AAR WebRTC](https://webrtc.googlesource.com/src/+/main/tools_webrtc/android/build_aar.py) вызывает [генератор лицензий](https://webrtc.googlesource.com/src/+/main/tools_webrtc/libs/generate_licenses.py). Генератор определяет зависимости через GN для выбранных целей и всех ABI, затем объединяет соответствующие тексты в `LICENSE.md`. Среди поддерживаемых им библиотек есть BoringSSL, libsrtp, Opus, libvpx, libyuv и другие; **этот перечень не устанавливает состав конкретных `.so`**.

Вместо неизвестного точного GN-манифеста поставляется расширенный набор уведомлений по закреплённой upstream-карте. Один исторический текст base64 добавлен отдельно и явно помечен: соответствующий путь в текущей карте устарел. Четыре отсутствующих Java/CIPD-пути перечислены в [описании границ](licenses/NATIVE_NOTICES.md); эти Java-пакеты не копируются в исходники runtime. Этот набор не выдаётся за vendor-generated `LICENSE.md` и не устанавливает, что все перечисленные компоненты входят в APK. Сама по себе неповторённая native-сборка не доказывает отсутствия лицензии или нарушения.

При распространении APK или отдельных `.so` сохраняйте пакет `licenses/` вместе с артефактом; для APK он может быть включён в assets с доступным пунктом лицензий. Отдельная загрузка runtime не отменяет этих условий. MIT ScreenStream и BSD WebRTC не заменяют уведомления встроенных сторонних библиотек.

## Источники сохранённых текстов

Проверено 2026-11-08. `ScreenStream-MIT.txt`, `WebRTC-BSD.txt` и `ZXing-Apache-2.0.txt` сохранены из ранее собранных текстов проекта. Начальные `JNI-Zero-BSD.txt`, `WebRTC-PATENTS.txt` и `WebRTC-AUTHORS.txt` получены из официальных деревьев `main`; pinned-версии дополнительно сохранены как `native/jni-zero.txt`, `native/patents.txt` и `native/authors.txt`. JNI Zero LICENSE blob main: `3e34df65794d8467d50b4690fb42fee83d3c2579`.

- [JNI Zero LICENSE](https://chromium.googlesource.com/chromium/src/+/main/third_party/jni_zero/LICENSE)
- [WebRTC PATENTS](https://webrtc.googlesource.com/src/+/main/PATENTS)
- [WebRTC AUTHORS](https://webrtc.googlesource.com/src/+/main/AUTHORS)
- [ZXing 3.5.3 NOTICE](https://github.com/zxing/zxing/blob/zxing-3.5.3/NOTICE)

SoraCast не заявляет об одобрении проекта авторами перечисленных компонентов.
