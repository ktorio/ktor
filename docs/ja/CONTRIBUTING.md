## 貢献方法

まず初めに、Ktor への貢献を検討していただきありがとうございます！

貢献にはさまざまな方法があります：

- コードの提供
- ドキュメントの改善
- コミュニティサポート
- フィードバックやバグ報告

どの方法で貢献する場合でも、必ず[行動規範](CODE_OF_CONDUCT.md)を確認し、遵守してください。

### コードの貢献

Ktor のバックログには、多くのバグ修正や新機能の実装タスクがあります。自由に選択できますが、まずは[取り組みやすいタスク](https://youtrack.jetbrains.com/issues?q=%23Ktor%20%20%20%23%7BUp%20For%20Grabs%7D%20%20%23Unresolved%20)から始めることを推奨します。

#### プロジェクトのビルド

> **重要**
> このプロジェクトは JDK 21 を必要とします。JDK 21 をインストールしたうえでビルドを実行してください。
> IntelliJ IDEA を使用する場合は、**「プロジェクト構造」>「プロジェクト」>「SDK」** で JDK 21 を選択してください。

Ktor は Gradle を使用してビルドされます。Ktor はマルチプラットフォーム対応のため、JVM、ネイティブ、JavaScript 用にビルドできます。

プロジェクトをビルドし、対応するアーティファクトを作成するには、以下のコマンドを実行してください。

```sh
./gradlew assemble
```

テストを実行するには、以下のコマンドを使用します。

```sh
./gradlew jvmTest
```

JVM 上のすべてのテストを実行します。最低限これを実行してください。他のプラットフォーム向けのコードを記述する場合は、それに対応するテストも実行する必要があります。

利用可能なタスクを確認するには、以下のコマンドを実行してください。

```sh
./gradlew tasks
```

#### 開発環境のセットアップ

開発に使用する OS に応じて、追加のライブラリやツールのインストールが必要です。

**Linux**

以下のコマンドを実行して `libcurl` および `libncurses` をインストールしてください。

```sh
sudo apt update
sudo apt install libcurl4-openssl-dev libncurses-dev
```

**macOS**

[Homebrew](https://brew.sh) を使用して `libcurl` および `libncurses` をインストールできます。

```sh
brew install curl ncurses
```

macOS または iOS をターゲットとする場合は、Xcode および Xcode コマンドラインツールのインストールが必要です。

**Windows**

Windows での開発には [Cygwin](http://cygwin.com/) の使用を推奨します。これにより `libncurses` などの必要なライブラリが提供されます。

#### IntelliJ IDEA でのプロジェクトのインポート

Ktor のプロジェクトフォルダを開くだけで、IntelliJ IDEA が Gradle プロジェクトとして自動検出し、インポートします。すべてのビルドやテストの操作を Gradle に委任するよう、[Gradle 設定](https://www.jetbrains.com/help/idea/gradle-settings.html) を確認してください。

### プルリクエスト（PR）

貢献は GitHub の[プルリクエスト](https://help.github.com/en/articles/about-pull-requests)を通じて行います。

1. Ktor リポジトリをフォークし、フォーク先で作業してください。
2. [新しい PR を作成](https://github.com/ktorio/ktor/compare)し、**main ブランチ** にマージをリクエストしてください。
3. 説明を明確にし、関連するチケットやバグ報告がある場合は KTOR-{NUM} の形式で記載してください。
4. 新機能を追加する場合、その価値やユースケースを説明し、Ktor フレームワークに適した変更である理由を明示してください。
5. ドキュメントの更新が必要な場合は、[YouTrack](https://youtrack.jetbrains.com/issues/KTOR) に新しいチケットを作成してください。
6. すべてのコード変更にはテストを含め、既存のテストを破壊しないようにしてください。

### スタイルガイド

- コードは公式の[Kotlin コーディング規約](https://kotlinlang.org/docs/reference/coding-conventions.html)に従うこと。
- `io.ktor.*` のパッケージでは、常にワイルドカードインポートを使用すること。
- すべての新しいソースファイルには著作権ヘッダーを記述すること。
- すべてのパブリック API（関数、クラス、オブジェクトなど）には適切なドキュメントを追加すること。
- 非公開にすべきだが技術的制約で公開されている API には `@InternalAPI` を付与すること。

### コミットメッセージ

- 英語で記述すること。
- 現在形・命令形で記述すること（例：「Fix」ではなく「Fixes」）。
- バグ修正のコミットには `KTOR-{NUM}` の形式で YouTrack のバグ番号を付けること。

### ドキュメントへの貢献

Ktor の公式ドキュメントは、[ktor-documentation](https://github.com/ktorio/ktor-documentation) リポジトリにあります。[貢献ガイド](https://github.com/ktorio/ktor-documentation#contributing)を確認してください。

### コミュニティサポート

[Ktor のサポートチャネル](https://ktor.io/support) に参加し、他の開発者を助けることで貢献できます。これは学習にも最適です。

### フィードバック・バグ報告

バグ報告や機能リクエストは [YouTrack](https://youtrack.jetbrains.com/issues/KTOR) で行ってください。

