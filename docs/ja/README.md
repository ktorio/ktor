<div align="center">

  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/ktorio/ktor/main/.github/images/ktor-logo-for-dark.svg">
    <img alt="Ktor ロゴ" src="https://raw.githubusercontent.com/ktorio/ktor/main/.github/images/ktor-logo-for-light.svg">
  </picture>

</div>

[![JetBrains公式プロジェクト](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Maven Central](https://img.shields.io/maven-central/v/io.ktor/ktor-server)](https://central.sonatype.com/search?namespace=io.ktor)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Slackチャンネル](https://img.shields.io/badge/chat-slack-green.svg?logo=slack)](https://kotlinlang.slack.com/messages/ktor/)
[![GitHubライセンス](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Gitpodで貢献](https://img.shields.io/badge/Contribute%20with-Gitpod-908a85?logo=gitpod)](https://gitpod.io/#https://github.com/ktorio/ktor)

Ktorは、Kotlinでゼロから開発された非同期フレームワークであり、マイクロサービスやWebアプリケーションの作成などに適しています。

## 依存関係の追加
プロジェクトに以下の依存関係を追加してください：

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-netty:$ktor_version")
}
```

## アプリケーションの作成と機能のインストール

```kotlin
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.engine.*

fun main(args: Array<String>) {
    embeddedServer(Netty, 8080) {
        routing {
            get("/") {
                call.respondText("Hello, world!", ContentType.Text.Html)
            }
        }
    }.start(wait = true)
}
```

また、[Ktor Gradle Plugin](https://github.com/ktorio/ktor-build-plugins) を使用すると、BOMの設定、タスクの実行、デプロイの管理が可能です。

```kotlin
plugins {
    id("io.ktor.plugin") version "3.0.0"
}

dependencies {
    implementation("io.ktor:ktor-server-netty")
}
```

## アプリケーションの実行

```shell
./gradlew run
```

* `localhost:8080` で組み込みWebサーバーが起動
* ルーティングを設定し、ルートパスへのGETリクエストに `Hello, world!` を返す

## Ktorの使用開始

Ktorを使ってKotlinのHTTPまたはRESTfulアプリケーションを開発：[start.ktor.io](https://start.ktor.io)

## Ktorの特徴

### 柔軟な設計

Ktorフレームワークは、ロギング、テンプレート、メッセージング、永続化、シリアライズ、依存性注入などに関して厳格な制約を課しません。必要に応じて、簡単なインターフェースを実装するだけで拡張できます。

### 非同期処理

KtorのパイプラインとAPIは、Kotlinのコルーチンを活用しており、スレッドブロックを防ぎつつ、シンプルな非同期プログラミングモデルを提供します。

### テストの容易さ

Ktorアプリケーションは、実際のネットワーク通信を行わない特別なテスト環境でホスト可能であり、統合テストも簡単に実行できます。

## JetBrains製品

Ktorは公式の[JetBrains](https://jetbrains.com)製品であり、JetBrainsのチームを中心に開発されています。

## ドキュメント

詳細な説明やクイックスタートガイドについては、公式サイト[ktor.io](http://ktor.io)をご覧ください。

* [Gradleでの導入](https://ktor.io/docs/gradle.html)
* [Mavenでの導入](https://ktor.io/docs/maven.html)
* [IntelliJ IDEAでの導入](https://ktor.io/docs/intellij-idea.html)

## 問題報告 / サポート

バグ報告や機能リクエストは[こちらのIssue Tracker](https://youtrack.jetbrains.com/issues/KTOR)へ。
質問は[StackOverflow](https://stackoverflow.com/questions/tagged/ktor)で受け付けています。
また、[Kotlin Slack Ktorチャンネル](https://app.slack.com/client/T09229ZC6/C0A974TJ9)でもコミュニティサポートを提供しています。

## セキュリティ脆弱性の報告

Ktorのセキュリティ上の問題を発見した場合は、[JetBrainsの責任ある開示プロセス](https://www.jetbrains.com/legal/terms/responsible-disclosure.html)を通じて報告してください。

## 貢献

貢献する前に、[コントリビューションガイド](CONTRIBUTING.md)および[行動規範](CODE_OF_CONDUCT.md)をご確認ください。

