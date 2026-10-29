# 0.2.1 就任日誌の検証記録

検証日: 2026-09-12。ローカルの変更を含むビルド。

## 成果物

- 開発署名付きAPK: `artifacts/PIXELCITY-0.2.1-debug.apk`
- パッケージ: `io.github.hatake716.pixelcity`
- versionName: `0.2.1` / versionCode: `3`
- 最低Android: 8.0（API 26）/ targetSdk: 36
- サイズ: 3,902,096 bytes
- SHA-256: `915efbca15c1a650ed7ddede2e84b9e754d3ed9b8c583d6cdc816bc4d92082d4`
- `apksigner verify --verbose` 成功（v2署名）。

`assembleRelease` もR8を含めて成功した。リリース用の署名鍵は設定されていないため、
そのAPKは未署名。配布用署名、実機へのインストール、GitHub・Google Playへの公開は行っていない。

## 最終チェック

```sh
ANDROID_SERIAL=emulator-5580 ./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease --console=plain
```

- Gradle: BUILD SUCCESSFUL
- JVM: 207件成功。うち201件が単体検証、6件が既存の画像・音声プレビュー生成。
- Android: 36件成功。Android 15 / API 35、1080×2400の専用エミュレーターで実施。
- Lint: エラー0件、警告46件。既存のSDK・依存関係・スタイル関連などの警告は残る。
- `git diff --check`: 成功。

ログ: `artifacts/story-validation/gradle-final.log`。

## 確認したゲーム体験

- 実Activityの「はじめから」から、秘書との会話、通常の建設、入居、住宅調査、商店街の成長、
  診療所の選択、情報・予算操作、3か月連続黒字、最後の手紙までをAndroid上で通して操作。
- 単体検証では広場・診療所の両分岐を32×32・128×128の街で完走。
  標準配置ではゲーム内6か月で修了し、続く24か月も追加補助金なしで経営を継続。
  この比較は災害を無効にして実施。本編の災害設定は変更しない。
- 会話本文のタップでは進まない。明示的な返答で作業を開始する。
- 建設途中の保存とActivity再作成で、会話確認・配置進捗・資金を維持。
- 選択した公共投資と連続黒字の記録を、実際のセーブ形式で復元。
- 停電・断水・孤立道路・撤去・赤字が進捗や助言に反映される。
- 候補地から離れた道路配置でも、実際の道路沿いに候補を提案。
- 予算の税率変更が見込み収支へすぐ反映され、資金と月は変化しない。
- メニューを読んだ時間が、閉じた後のゲーム時間に加算されない。
- 旧チュートリアルの途中保存では、街を保ち、新しい日誌の引継ぎ説明から再開。

## 実画面

最終AndroidテストでGameViewを描画した画像。合成したモックではない。

- [着任時の会話](../artifacts/story-validation/final-screens/opening.png)
- [公共投資の選択](../artifacts/story-validation/final-screens/choice.png)
- [現在の街の予算](../artifacts/story-validation/final-screens/budget.png)
- [最後の手紙](../artifacts/story-validation/final-screens/ending.png)

物語と進行条件の詳細: [TUTORIAL_STORY.md](TUTORIAL_STORY.md)。
