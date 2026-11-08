# WebRTC native notices — conservative bundle

This directory accompanies SoraCast's prebuilt WebRTC runtime. The files in `native/` reproduce upstream license, copyright, patent and notice texts. They are a deliberately broad attribution bundle, **not an assertion that every component named here is linked into the APK** and not an exact GN-generated dependency manifest.

## Pinned provenance

- Vendor: ScreenStream `3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a`, `webrtc-runtime/`.
- Declared WebRTC source: `1f975dfd761af6e5d76d28333191973b258d82a8`.
- WebRTC's pinned DEPS imports Chromium third_party `7c92732938de0ef7e28f5da231994723f938f407`; separately checked-out libraries use their own exact revisions from that DEPS file.
- Source evidence: [pinned WebRTC DEPS](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/DEPS), [pinned license generator](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/tools_webrtc/libs/generate_licenses.py).
- Local evidence: `provenance/deps-pinned.txt`, `provenance/generator-pinned.txt`, `provenance/sources-complete.json`. Every attempted source URL and its HTTP status is recorded. `NATIVE_FILES.sha256` records the retained text hashes.

The selection starts from all native libraries named in the pinned license generator, rather than guessing only the codecs used in a particular session. It includes BoringSSL and its notices, Fiat, SRTP, Opus, libvpx, libyuv, libaom, dav1d, JPEG including the actual IJG text, Abseil, crc32c, cpu_features, FFT/audio routines, protobuf/jsoncpp, zlib, LLVM runtimes, and the large pinned Android toolchain NOTICE. Additional Java/build-tool licenses and patent grants are retained conservatively.

The Android toolchain NOTICE is the tracked file in that pinned Chromium third_party tree. WebRTC DEPS declares Android NDK package `version:2@30.0.14608247`; downloading or rebuilding that toolchain was not needed to retain its checked-in notice file.

## Narrow exceptions and limits

1. The generator still maps `rtc_base/third_party/base64/LICENSE`, but that path returns 404 at the declared WebRTC revision. `native/base64-historical.txt` therefore preserves the Bob Withers / Stanley Yamane notice from official historical WebRTC commit `b55015e4e1aa2cc326b39a2e807fa4228dbe81d1`. It is labelled historical instead of being misrepresented as a pinned text. This is an additional attribution precaution, not proof that this old base64 implementation is linked.
2. Four Java/CIPD-generated license paths in the map return 404: Auto Service, Error Prone, Guava and old Android Support Annotations. Their declared map entries are retained in provenance. These Java packages are not copied into the SoraCast runtime source tree, and that tree has no `com.google`, `kotlin` or `org.jetbrains` imports. Their common Apache-2.0 license text is also supplied in `ZXing-Apache-2.0.txt`. This package does not claim to contain their exact generated JAR metadata or an exact manifest of a different Java AAR.
3. A vendor-generated `LICENSE.md` for the actual four binary builds was not available. This bundle instead supplies a conservative set of upstream notices based on the pinned generator and DEPS. It does not reproduce unknown custom vendor GN arguments or certify an independent native rebuild. That provenance limitation does not by itself establish a missing license text or a licensing violation.

Existing upstream source copyright headers must remain intact. Distribute this notice bundle with the APK (for example, its assets plus an accessible licenses entry), and with any separately redistributed native runtime. The root SoraCast MIT license does not replace these texts. No endorsement by upstream authors is implied.

Retrieved 2026-11-08. The file index below is generated from the retained text files, with no shortening of their license contents.

| Notice text | Source |
| --- | --- |
| [abseil.txt](native/abseil.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/abseil-cpp/LICENSE?format=TEXT) |
| [android-buildtools.txt](native/android-buildtools.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/android_build_tools/bundletool/LICENSE?format=TEXT) |
| [android-sdk.txt](native/android-sdk.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/android_sdk/LICENSE?format=TEXT) |
| [android-toolchain.txt](native/android-toolchain.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/android_toolchain/NOTICE?format=TEXT) |
| [aom-patents.txt](native/aom-patents.txt) | [upstream](https://aomedia.googlesource.com/aom/+/c213343c8d32bcae729fe09fcba16e1f371cb23b/PATENTS?format=TEXT) |
| [aom.txt](native/aom.txt) | [upstream](https://aomedia.googlesource.com/aom/+/c213343c8d32bcae729fe09fcba16e1f371cb23b/LICENSE?format=TEXT) |
| [authors.txt](native/authors.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/AUTHORS?format=TEXT) |
| [base64-historical.txt](native/base64-historical.txt) | [upstream](https://webrtc.googlesource.com/src/+/b55015e4e1aa2cc326b39a2e807fa4228dbe81d1/rtc_base/third_party/base64/LICENSE?format=TEXT) |
| [boringssl.txt](native/boringssl.txt) | [upstream](https://boringssl.googlesource.com/boringssl/+/f91f1447397c6719f9774dfb8e67329378e1f3d3/LICENSE?format=TEXT) |
| [compiler-rt.txt](native/compiler-rt.txt) | [upstream](https://chromium.googlesource.com/external/github.com/llvm/llvm-project/compiler-rt/+/b7f9fa6b211b362d3ca07ab2043419ebaf75d1d0/LICENSE.TXT?format=TEXT) |
| [cpufeatures.txt](native/cpufeatures.txt) | [upstream](https://chromium.googlesource.com/external/github.com/google/cpu_features/+/d3b2440fcfc25fe8e6d0d4a85f06d68e98312f5b/LICENSE?format=TEXT) |
| [crc32c.txt](native/crc32c.txt) | [upstream](https://chromium.googlesource.com/external/github.com/google/crc32c/+/d3d60ac6e0f16780bcfcc825385e1d338801a558/LICENSE?format=TEXT) |
| [dav1d.txt](native/dav1d.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/dav1d/LICENSE?format=TEXT) |
| [fft.txt](native/fft.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/modules/third_party/fft/LICENSE?format=TEXT) |
| [fiat.txt](native/fiat.txt) | [upstream](https://boringssl.googlesource.com/boringssl/+/f91f1447397c6719f9774dfb8e67329378e1f3d3/third_party/fiat/LICENSE?format=TEXT) |
| [g711.txt](native/g711.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/modules/third_party/g711/LICENSE?format=TEXT) |
| [g722.txt](native/g722.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/modules/third_party/g722/LICENSE?format=TEXT) |
| [ijar.txt](native/ijar.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/ijar/LICENSE?format=TEXT) |
| [jni-zero.txt](native/jni-zero.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/jni_zero/LICENSE?format=TEXT) |
| [jpeg-ijg.txt](native/jpeg-ijg.txt) | [upstream](https://chromium.googlesource.com/chromium/deps/libjpeg_turbo/+/640f254ad0fa03f6b1f29f89b7dd9366f2f6e533/README.ijg?format=TEXT) |
| [jpeg.txt](native/jpeg.txt) | [upstream](https://chromium.googlesource.com/chromium/deps/libjpeg_turbo/+/640f254ad0fa03f6b1f29f89b7dd9366f2f6e533/LICENSE.md?format=TEXT) |
| [jsoncpp.txt](native/jsoncpp.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/jsoncpp/LICENSE?format=TEXT) |
| [kotlin-stdlib.txt](native/kotlin-stdlib.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/kotlin_stdlib/LICENSE?format=TEXT) |
| [libcxx.txt](native/libcxx.txt) | [upstream](https://chromium.googlesource.com/external/github.com/llvm/llvm-project/libcxx/+/5abc7f839700f0f17338434e1c1c6a8c87c00c11/LICENSE.TXT?format=TEXT) |
| [libcxxabi.txt](native/libcxxabi.txt) | [upstream](https://chromium.googlesource.com/external/github.com/llvm/llvm-project/libcxxabi/+/8f11bb1d4438d0239d0dfc1bd9456a9f31629dda/LICENSE.TXT?format=TEXT) |
| [libsrtp.txt](native/libsrtp.txt) | [upstream](https://chromium.googlesource.com/chromium/deps/libsrtp/+/cd5d177bf1fde755ddb4c7f0d9ff7693f8b49e5e/LICENSE?format=TEXT) |
| [libunwind.txt](native/libunwind.txt) | [upstream](https://chromium.googlesource.com/external/github.com/llvm/llvm-project/libunwind/+/d6c7a21e978f0adaa43accaad53bc64f0b64f6ec/LICENSE.TXT?format=TEXT) |
| [libvpx.txt](native/libvpx.txt) | [upstream](https://chromium.googlesource.com/webm/libvpx/+/31af37b1bd2774d11a932c1cd9a3849328375f64/LICENSE?format=TEXT) |
| [libyuv.txt](native/libyuv.txt) | [upstream](https://chromium.googlesource.com/libyuv/libyuv/+/de63bd90f4396313f864ad58b65279e7894451a9/LICENSE?format=TEXT) |
| [llvm-libc.txt](native/llvm-libc.txt) | [upstream](https://chromium.googlesource.com/external/github.com/llvm/llvm-project/libc/+/be95a36286a288c9437f5ed991720ccece1a6391/LICENSE.TXT?format=TEXT) |
| [nasm.txt](native/nasm.txt) | [upstream](https://chromium.googlesource.com/chromium/deps/nasm/+/525a09a813be0f75b646ee93fc2a31c27b87d722/LICENSE?format=TEXT) |
| [ooura.txt](native/ooura.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/common_audio/third_party/ooura/LICENSE?format=TEXT) |
| [opus.txt](native/opus.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/opus/src/COPYING?format=TEXT) |
| [patents.txt](native/patents.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/PATENTS?format=TEXT) |
| [perfetto.txt](native/perfetto.txt) | [upstream](https://chromium.googlesource.com/external/github.com/google/perfetto/+/82ad4b69cbaf64e26c639061b1712756a9cc5e20/LICENSE?format=TEXT) |
| [pffft.txt](native/pffft.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/pffft/LICENSE?format=TEXT) |
| [portaudio.txt](native/portaudio.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/modules/third_party/portaudio/LICENSE?format=TEXT) |
| [protobuf-javascript.txt](native/protobuf-javascript.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/protobuf-javascript/LICENSE?format=TEXT) |
| [protobuf.txt](native/protobuf.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/protobuf/LICENSE?format=TEXT) |
| [rnnoise.txt](native/rnnoise.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/rnnoise/COPYING?format=TEXT) |
| [spl-sqrt-floor.txt](native/spl-sqrt-floor.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/common_audio/third_party/spl_sqrt_floor/LICENSE?format=TEXT) |
| [webrtc.txt](native/webrtc.txt) | [upstream](https://webrtc.googlesource.com/src/+/1f975dfd761af6e5d76d28333191973b258d82a8/LICENSE?format=TEXT) |
| [zlib.txt](native/zlib.txt) | [upstream](https://chromium.googlesource.com/chromium/src/third_party/+/7c92732938de0ef7e28f5da231994723f938f407/zlib/LICENSE?format=TEXT) |

