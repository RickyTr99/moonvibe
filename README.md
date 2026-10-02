# MoonVibe

MoonVibe is an Android client for game streaming from your PC with [Sunshine](https://github.com/LizardByte/Sunshine) or [Apollo](https://github.com/ClassicOldSong/Apollo).

MoonVibe brings together the best of several Moonlight versions in one app: [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android), the [MoreOrLess fork](https://github.com/MoreOrLessSoftware/moonlight-android) it is built on, and [Artemis](https://github.com/ClassicOldSong/moonlight-android). On top of that, the whole user experience is reworked: a new interface, simpler navigation, and full support for both touch and gamepad.

> [!WARNING]
> MoonVibe is experimental: expect bugs and changes between releases.

## What you get

**A reworked user experience**
- New Material 3 interface: a dark design with animations, rebuilt for the home screen, the game library and the settings.
- Every screen works with your finger or a controller. LB/RB switch tabs, and button hints show while you use a gamepad.
- A unified in-game menu: quick actions, custom commands and stream options in one place while streaming.

**Features from several Moonlight versions**
- From MoreOrLess: Quick Launch apps, per-app settings, brightness while streaming, low latency decoder options and more.
- From Artemis: a full PC keyboard on screen, touchscreen modes (multi-touch, mouse, trackpad) and configurable 3/4/5-finger taps, with more features on the way.

## Download

Get the APK from the [releases](https://github.com/RickyTr99/moonvibe/releases).

## Building

- Install Android Studio and the Android NDK.
- Run `git submodule update --init --recursive` from the project folder.
- Build the APK with Android Studio or Gradle (`gradlew assembleNonRootDebug`).

The project path must not contain spaces, because `ndk-build` fails on them.

## Credits

- [Moonlight for Android](https://github.com/moonlight-stream/moonlight-android) by [Cameron Gutman](https://github.com/cgutman) and contributors
- [MoreOrLess](https://github.com/MoreOrLessSoftware/moonlight-android): the fork MoonVibe is based on
- [Artemis](https://github.com/ClassicOldSong/moonlight-android) by ClassicOldSong: the source of many ported features

## License

GPL-3.0, like Moonlight. See [LICENSE.txt](LICENSE.txt).
