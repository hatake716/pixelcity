# フェルト版の検証（0.4.1）

2026-09-12。`io.github.hatake716.pixelcity`、versionName0.4.1、versionCode8。

| 対象 | 結果 |
| --- | --- |
| JVM単体テスト | 222件成功、失敗・エラー・スキップ0 |
| Android端末テスト | 専用エミュレータで53件成功、失敗・エラー・スキップ0 |
| 最後の描画修正 | 道路の布地をタイル境界で切りそろえた後、表示テスト6件を再実行、全件成功 |
| Lint debug / release | 各エラー0、警告37 |
| Releaseビルド | R8・リソース縮小を含むassembleRelease成功。release署名は未設定 |
| 素材 | 113画像すべてfinish=felt。草地・砂浜・道路・海の4画像もフェルト版のハッシュと一致 |
| 出典・3D | 既存CC0出典17件とGLB8点、独自フェルト素材・生成コードのハッシュを検証 |
| 実機 | Pixel 10aへ0.4.0から上書き更新。起動成功、保存データを保持 |

単体テストのうち6件は旧描画のプレビュー用。現行描画の確認はAndroid上で実施した。
漢字への変更で旧表記に依存していた既存テストの期待文字列も修正した。

## 画面確認

Android15/API35、1080×2400、420dpi、縦画面で確認。
タイトル・港町・拡大表示・記念建築8点・予算・記念建築選択・地図・条例を目視確認。
文字サイズ160%のタイトル、設定の末尾へのスクロール、ピンチ、チュートリアルと3シナリオは端末テストで確認。

- [街並み](images/felt-harbour.png)
- [拡大した街並み](images/felt-harbour-detail.png)
- [タイトル](images/felt-title.png)
- [予算](images/felt-panel-budget.png)
- [記念建築の選択](images/felt-panel-monuments.png)

200か所以上の項目名・案内を漢字表記へ修正。
「じゅうたく」「しゅうにゅう」「でんりょく」「じょうれい」「しらべる」「よさん」等の
旧表記が実行時の文字列に残っていないことを検索で確認した。
「ひより」などの固有名、助詞・送り仮名、会話の自然な表現は保持。

## 実機と配布物

日常使用のPixel 10aではテストを実行せず、端末を明示した
`adb install --no-incremental -r`で更新した。削除・初期化・ジェスチャー注入は行っていない。
更新直前・更新後・起動後のSharedPreferencesがバイト単位で一致。
`am start -W`でStatus: ok、COLD、TotalTime1441ms。起動プロセスのAndroidRuntimeエラー出力なし。
実機上の手操作による体感評価・フレーム時間計測は今回の検証には含まない。

インストールしたdebug署名APK：`artifacts/PIXELCITY-0.4.1-debug.apk`。

- サイズ：9,442,380 bytes
- SHA-256：`a7af93be9bfe97bec73bf27d07df05ec382ff2cda11783a5fea6bc63f8cb5b8e`
- 署名証明書SHA-256：`2c53b411c1193758289715cc230989564a571259b2eb4f5702b774c07a515e87`
- 元写真・GLB・端末テスト用データは配布APKに含めない。

全体テスト証跡：`artifacts/felt/validation/full-suite/`。
最終描画確認：`artifacts/felt/validation/surface-check.log`、`final-surface/`。
素材検証：`artifacts/felt/asset-validation.json`。実機更新記録：`artifacts/felt/device/update-evidence.json`。
保存内容はローカル退避のみとし、この文書には含めない。

Lintの37警告はKTXの提案、依存更新通知、未使用リソース、描画時の割り当て等。
GitHubへのコミット・push、Play Consoleへのアップロード・公開はこの作業では行っていない。
