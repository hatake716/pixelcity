# 写真素材・3D建築・操作UI刷新の検証（0.4.0）

2026-09-12。パッケージ `io.github.hatake716.pixelcity`、versionName `0.4.0`、versionCode `7`。

## 実施結果

| 対象 | 結果 |
| --- | --- |
| JVM単体テスト | 222件成功、失敗・エラー・スキップ0。うち6件は旧描画の回帰確認用プレビュー |
| Android端末テスト全体 | API35の専用エミュレータで53件成功、失敗・エラー・スキップ0 |
| 最終素材差し替え後 | `VisualRenewalTest` 6件を再実行、すべて成功 |
| Lint debug / release | それぞれエラー0、警告37 |
| Releaseビルド | R8・リソース縮小を含む `assembleRelease` 成功。署名設定のないrelease APKは未署名 |
| 素材検証 | 出典17件、画像113件、記念建築GLB8件の形式・ハッシュ・メタデータを検証 |
| 最終debug APK | 署名検証成功。113画像のSHA-256が出典側の生成マニフェストと一致 |
| Pixel 10a | 0.3.1 → 0.4.0を上書きインストール。起動成功、保存データ維持 |

全体テスト後に変更したのは記念建築4点の形状・質感、素材クレジットと文書。
最終素材で表示テストを再実行し、最終APKからも画像を検証した。
JVMの旧描画プレビューは現在のAndroid描画経路の表示確認には数えない。

## 表示と操作

検証環境はAndroid 15 / API35、1080×2400、420dpi、縦画面。
タイトル、動く背景、港町の通常・拡大表示、記念建築8種、都市情報の全6タブ、設定末尾、
チュートリアル冒頭・選択場面、3シナリオの政策画面を画像で確認した。

- 新しい高解像度画像が全建築・全車両を網羅し、透明な背景を持つことを検証。
- タイトル背景の時刻差分と、「はじめから」からGameViewへの遷移を検証。
- タイトルの通常サイズ・システム文字サイズ160%でクリック対象の高さ48dp以上を確認。
- 都市情報を末尾までスクロールし、最後の災害設定のヒット領域を確認。
- 8件のピンチテストで拡大・縮小・上下限、2指の中心固定、微小変化、同時移動を検証。
- セーブ互換性、税率、調査、チュートリアル、全3シナリオの進行を全体テストで確認。

最終画像：

- [タイトル](images/realistic-title.png)
- [港町](images/realistic-harbour.png)
- [記念建築](images/realistic-landmarks.png)

実機での手操作によるピンチの体感評価や、フレーム時間の計測は未実施。
「常時60fps」や全端末で同じ操作感を保証する測定結果ではない。
新しいタイトル・メニューはネイティブViewだが、本編のすべてのCanvas操作がTalkBackに対応したわけではない。

## 素材と3Dデータ

`tools/realistic/verify_assets.py`で元ファイルのSHA-256、CC0表記、GLB2.0のヘッダー・バイト長、
頂点・UV・写真の出典URL・作者・ライセンス・写真ハッシュを検査。
記念建築は写真を観察して制作したゲーム用メッシュであり、写真測量スキャンではない。
写真に写らない部分や細部は推定・簡略化している。

最終APKには113枚のモデル画像、地面画像、Outfitフォントとライセンスを収録。
元写真・大きな3Dソース・端末テストのデータはAPKに含めていない。

## 実機更新

接続中のPixel 10aを明示して `adb install --no-incremental -r` を実行し、`Success`。
versionCode7 / versionName0.4.0を確認。アプリの削除・データ初期化・実機へのテスト投入は行っていない。
起動は `am start -W` で `Status: ok`、COLD、TotalTime907ms。
起動したプロセスのAndroidRuntimeエラー出力なし。

SharedPreferencesは更新前にローカル退避し、更新直後・起動後ともバイト単位で一致。
保存内容そのものは公開用文書に収録しない。

## 成果物

- インストール用署名済みdebug APK：`artifacts/PIXELCITY-0.4.0-debug.apk`
  - 9,452,683 bytes
  - SHA-256：`d39641fa84e533d6a0d6c4d2f166b7514bc8655a7fbd1e34587ad66835dae3bc`
  - Android Debug証明書SHA-256：`2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87`
- 写真・GLB・出典ZIP：`artifacts/PIXELCITY-0.4.0-landmarks.zip`（58,342,592 bytes）
- 全体テスト証跡：`artifacts/realistic/validation/full-suite/`
- 最終素材テスト：`artifacts/realistic/validation/final-assets-build.log`
- 生成物検査：`artifacts/realistic/asset-validation.json`
- 実機更新記録：`artifacts/realistic/device/update-evidence.json`

Lintの37警告にはKTXへの置換候補、依存ライブラリの更新通知、未使用リソース、文字列結合、
描画内の割り当てなどを含む。エラー0と警告0を混同しない。
GitHubへのコミット・push、Play Consoleへのアップロード・公開はこの作業では実施していない。
