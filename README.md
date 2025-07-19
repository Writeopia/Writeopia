
<p align="center" width="100%">
  <a href="https://github.com/leandroBorgesFerreira/Writeopia/actions/workflows/main-merge.yml"><img src="https://github.com/leandroBorgesFerreira/Writeopia/actions/workflows/main-merge.yml/badge.svg" alt="Main merge"></a>
  <a href="https://search.maven.org/artifact/io.writeopia/writeopia-core"><img src="https://img.shields.io/maven-central/v/io.writeopia/writeopia-core" alt="Maven Central"/></a>
  <a href="https://pinterest.github.io/ktlint/" target="_blank"><img src="https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081" alt="ktlint badge" />
  </a>
</p>

<p align="center" width="100%">
  <img
   src="./images/icon_with_title.svg" alt="Writeopia logo"
   width="100"
   height="87"
  />
</p>

<p align="center">Write like nobody’s watching. Because they aren’t.<p/>

<p align="center" width="100%">
  <img
   src="./images/usage_screenshot.png" alt="Writeopia logo"
  />
</p>

This is the source code for Writopia, a text editing App and SDK.

# Application
Writeopia is a modern text editor that integrates AI while prioritizing offline functionality and user privacy. Unlike cloud-based AI assistants, Writeopia allows you to choose your own AI model and interact with it locally, ensuring that your data stays on your device.

## Why Writeopia?
Writeopia is built for writers, developers, and researchers who want the power of AI without sacrificing privacy. Whether you're drafting a novel, taking notes, or coding, Writeopia provides an intelligent and distraction-free environment.

## Documentation
You can check the documentation of the project in the [application documentation](https://docs.writeopia.io/docs/application/overview).

## App download
You can go to https://writeopia.io/download and download the app. It is available for macOS, Linux and Windows. We would love your feedback about the app!

## Development
You can check how to [work locally](https://docs.writeopia.io/docs/local-dev/getting-started) if you would like to contribute with fixes, improvements or try Writeopia from source code.

## Contributing
Writeopia is a free project, so we would love your participation. Please check our issues and suggest changes before using the issues tab so we can evaluate and architecture new solutions together.

If you're looking for a place to get started you can look at:
- Issues with [good first issue](https://github.com/leandroBorgesFerreira/Writeopia/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) label
- [Documentation](https://github.com/leandroBorgesFerreira/Writeopia/tree/Documentation/documentation/writeopia_docs) for the project
- Bugs and other issues listed on GitHub
- Analise the apps and looks for new bugs.

## Commands
Some useful commands for this project:

To start the desktop app:

```
./gradlew application:composeApp:run
```

To start the web app:

```
./gradlew jsBrowserRun
```

Running the tests of the project:

```
./gradlew jvmTest
```

To start the android app use Android Studio.

# SDK
The SDK allows you to create a text editor an use it instead of a simple `TextField`. You can use this library to create a better story editing experience within apps, such as social media (posts), travel logs, blog apps (like Medium), and chats (like Slack). By using this library, you can provide a great experience for editing text with media.

The SDK is under MIT license you can freely use it. 

You can more in the [SDK documentation](https://docs.writeopia.io/docs/sdk/overview)
