# PIXELCITY 0.2.0 建築刷新・修正の検証記録

検証日: 2026-09-12。基点: main / 9a19527edfb78d9d12c3a18f334e481ca5504912。
変更はローカル作業ツリー。GitHubへのpush・リリース作成・Google Playへの提出は行っていない。

## 実装

- R/C/I × 3段階 × 6形態 = 54種類を新しい手続き建築へ置換。公共・インフラ20種類も更新。
- 色の異なる左官・煉瓦壁、屋根窓、瓦・マンサード屋根、煙突、日よけ、花箱、店先、屋上庭園、カフェの席を描画。
- 草・海・岸・道路・交差点・公園・樹木を調整。景観として人・ヨットを追加。
- 4番目のお手本「こもれびの みなとまち」を追加。既存3都市とともにそのまま経営可能。
- 道路種別の接続、系統ごとの電力、省エネ時の実供給、交通容量、ゴミ処理優先度・埋立再建設を修正。
- 保存v4: 固定ID、旧v1〜v3互換、需要・収支保持、読み込み時の派生値復元、不正保存の保持。
- 税率ボタン、チュートリアル進行、2本指開始と取消時の選択復元、撤去費込みの費用を修正。
- しらべるモード、建物の見えている屋根を選ぶ判定、OS戻るの市長メニューを追加。
- タイトルへ戻った後の削除済み都市再保存を防止。停止時の無限再描画を除去。
- 面積平均で縮小し、地面・建物レイヤーをキャッシュ。上限付きの画像キャッシュ。
- README・現行仕様・素材の来歴・バランス・ストア原稿・リリース手順を更新。

## 自動検証

実行コマンド:

```bash
ANDROID_SERIAL=emulator-5580 ./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease -Ppixelcity.preview="$PWD/artifacts/renewal-preview" --console=plain
```

- BUILD SUCCESSFUL。JVM 208件、失敗・エラー・skip 0。内訳は202件の検証と6件の画像/音声出力ユーティリティ。
- Android 15/API 35、1080×2400の使い捨てエミュレータで33件成功。
- 実Activityの表示座標をタップして税率変更、調査、削除後のpause/resume、OS戻るを確認。
- 保存直後の比較と、その後12か月の中断あり/なしの一致を確認。
- 4お手本都市の24か月運営テスト成功。
- Lint: エラー0、警告46。SDK・依存更新9、UseKtx15、SetTextI18n14、未使用リソース4、旧API分岐2、縦画面設定2。警告を抑制して0にはしていない。
- R8を含むreleaseビルド成功。releaseは未署名。署名済み製品APK/AABやPlay提出物ではない。

[Gradleログ](../artifacts/renewal-validation/gradle-final.log) / [結果JSON](../artifacts/renewal-validation/results.json) /
[Lint結果](../app/build/reports/lint-results-debug.html)

## 画面・操作

最終debug APKをインストールし、タイトル → お手本選択 → 海辺の街を開始。
建物調査を開いて道路・電気・水道・地域指標・次の発展条件を確認。
予算画面の税率を7%から8%へ変更し、資金108451・人口8472・1910年4月を保持した状態で保存。
アプリを終了して再起動し、保存枠1から再開して税率・年月・資金を確認。

[ゲーム画面](../artifacts/renewal-validation/game.png) /
[建物調査](../artifacts/renewal-validation/inspection.png) /
[予算](../artifacts/renewal-validation/budget.png) /
[市長メニュー](../artifacts/renewal-validation/mayor-menu.png) /
[保存枠](../artifacts/renewal-validation/save-slot.png) /
[再開後](../artifacts/renewal-validation/resumed-budget.png)

街並みのPNGは本編と同じCityRendererの出力。コンセプト画像ではない。
[海辺の全景](images/harbour.png) / [街区の細部](images/street-detail.png)

## 描画の計測範囲

同一のheadlessエミュレータ（SwiftShader）、debug版、海辺のお手本でのAndroid gfxinfoの観測。
キャッシュ前は中央値150ms、キャッシュ後の498フレームの標本は中央値40ms、95パーセンタイル48ms。
[キャッシュ後の生データ](../artifacts/renewal-validation/graphics-after-cache.txt)。
実機のFPSや全端末での性能保証を意味しない。コールド起動・初回画像生成・ズーム時には描き直しが発生する。

## 確認用APK

- バージョン0.2.0 / versionCode 2 / package io.github.hatake716.pixelcity。
- Android 8.0/API 26以降、target API 36。
- [PIXELCITY-0.2.0-debug.apk](../artifacts/PIXELCITY-0.2.0-debug.apk)、3869198 bytes。
- 開発用署名。apksignerでv2署名検証成功。INTERNET権限なし。
- SHA-256: `0a4d5d84e931f9d9acafb1c0705d649e2d033029a622881a5abe28624f230754`

## 現在の範囲

2Dアイソメトリック・タイル単位の経営ゲーム。個々の市民の通勤経路、自由曲線道路、橋や立体交差、
3Dの回転カメラ、実際の地下レイヤーは未実装。人やヨットは景観の絵で、独立した生活・物流主体ではない。
日常利用の実機へのインストール・実機の操作受入・本番署名・外部公開は今回実施していない。
