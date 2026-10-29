# 0.3.0 三つの都市シナリオの検証記録

2026-09-12、ローカルの変更を含むビルド。

## APK

- `artifacts/PIXELCITY-0.3.0-debug.apk`
- `io.github.hatake716.pixelcity` / versionName 0.3.0 / versionCode 5
- Android 8.0（API 26）以降、targetSdk 36
- 5,916,054 bytes
- SHA-256: `e9d70917c6791a1e4088a1202d0b52a26eb5e8d39e48f73f4fe7e1def0181812`
- 開発署名の検証成功（APK Signature Scheme v2）
- 検証用の完成都市JSONが本体APKに入っていないことも確認。

## 最終チェック

```sh
ANDROID_SERIAL=emulator-5580 ./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease --console=plain
```

- BUILD SUCCESSFUL（59秒）
- JVM: 219件成功。単体検証213件と、既存の画像・音声プレビュー生成6件。
- Android: 40件成功、失敗・スキップなし。
- Android 15 / API 35の専用エミュレーター、1080×2400で実施。
- Lint: エラー0件、既存警告46件。
- R8を含むリリースビルド成功。配布用鍵が未設定のためリリースAPKは未署名。
- `git diff --check` 成功。

ログ: `artifacts/scenario-validation/gradle-final.log`。
シナリオの単体検証結果: `artifacts/scenario-validation/campaign-test-results.xml`。
実機へのインストール、GitHub・Google Playへの公開は行っていない。

## 都市の完成条件と継続経営

各ルートの完成規模の検証用都市から、通常の月次計算と政策選択で全章を進めた結果。
下表は小さな就任時の街から建設した所要期間を示すものではない。
第一案・第二案は、それぞれ5章すべてでその側の政策を選んだケース。

| 都市 | 選択 | 完成時人口 | 完成時の月間黒字 | さらに12か月後の人口 |
|---|---|---:|---:|---:|
| 大都市 | 第一案 | 48,000 | 6,335 | 48,000 |
| 大都市 | 第二案 | 48,000 | 6,189 | 48,000 |
| 田園都市 | 第一案 | 7,200 | 276 | 7,200 |
| 田園都市 | 第二案 | 7,200 | 262 | 7,200 |
| 工業都市 | 第一案 | 38,400 | 3,664 | 38,400 |
| 工業都市 | 第二案 | 38,400 | 3,651 | 38,400 |

いずれも全完成条件と6か月連続黒字を満たし、完成後12か月も資金が正で破綻しないことを確認。
通常の災害は無効にして比較したが、田園ルート固有の天候リスクは有効のまま。

また、初期費用と維持費の請求、資金不足時の非採用、断水や赤字による記録のリセット、
無接続施設を数えないこと、防災による実被害と応急費の減少、工業協定収入の停止条件、
保存・再読込による同じ月の二重計上防止、旧版読み込み、不完全なシナリオデータの拒否も検証。

## Android上の操作

- 「はじめから」から就任日誌を完走し、最後の返答で三つの分岐が表示される。
- それぞれのルートを選び、政策採用・費用・都市の保持・手帳の各ページ・保存再開を確認。
- 資金不足でも、採用せずに街へ戻って経営を続けられる。
- 本文タップでは選択が発生せず、手帳の閲覧時間は閉じた後に加算されない。
- 完成した検証用都市を読み、ルートごとの結末から同じ都市の経営へ戻る。
- 完成報告後は建設した場所へカメラを合わせる。保存した建物は置換しない。

## 実画面

以下はAndroidのGameViewを描画した画像。都市の画像は上記の検証用都市。

- [三つの分岐](../artifacts/scenario-validation/screens/branches.png)
- [大都市の導入](../artifacts/scenario-validation/screens/metropolis-opening.png)
- [田園都市の導入](../artifacts/scenario-validation/screens/garden-opening.png)
- [工業都市の導入](../artifacts/scenario-validation/screens/industrial-opening.png)
- [大都市の完成報告](../artifacts/scenario-validation/screens/metropolis-ending.png) / [都市](../artifacts/scenario-validation/screens/metropolis-completed-city.png)
- [田園都市の完成報告](../artifacts/scenario-validation/screens/garden-ending.png) / [都市](../artifacts/scenario-validation/screens/garden-completed-city.png)
- [工業都市の完成報告](../artifacts/scenario-validation/screens/industrial-ending.png) / [都市](../artifacts/scenario-validation/screens/industrial-completed-city.png)

物語・仕組みの詳細: [SCENARIOS.md](SCENARIOS.md)。
