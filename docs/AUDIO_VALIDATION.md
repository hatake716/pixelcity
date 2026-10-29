# 0.3.1 音の刷新の検証記録

2026-09-12、ローカルの変更を含むビルド。
音の構成・再生の仕様は [AUDIO_DESIGN.md](AUDIO_DESIGN.md)。

## 成果物

- APK: `artifacts/PIXELCITY-0.3.1-debug.apk`
- package: `io.github.hatake716.pixelcity`
- versionName: 0.3.1 / versionCode: 6
- 5,627,205 bytes、開発署名（APK Signature Scheme v2の検証成功）
- SHA-256: `f3461942b5d6a9c121fab55bce5d13aea19b714b5d13e45b379283aad6883fda`
- リリースのR8ビルドも成功。配布用署名が未設定のためリリースAPKは未署名。

## 自動検証

```sh
ANDROID_SERIAL=emulator-5580 ./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest assembleRelease --console=plain
```

- BUILD SUCCESSFUL、1分14秒。
- JVM: 222件成功（単体検証216件＋既存のプレビュー生成6件）。失敗・スキップなし。
- Android: 44件成功。既存40件と新規AudioPlaybackTestの4件。失敗・スキップなし。
- Lint: エラー0件、警告45件。
- Android 15 / API 35、専用のemulator-5580を使用。実機にテストデータを投入していない。
- `git diff --check` 成功。

BGM全曲の長さ、音量、無音、DCオフセット、クリッピング、ループ端を検証。
SEの短さ・識別可能な波形・減衰・再現性を検証。増幅時のクリッピング回帰も追加。
AndroidではUIスレッドを塞がない曲生成、再生位置の進行、600msのクロスフェード後の旧トラック解放、
一時停止位置の保持、重複resumeで巻き戻らないこと、ミュート・破棄後の遅延再生抑止、SE同時発音制限、
追加入力がなくてもSEのトラックが解放されることを確認。
既存シナリオUI試験にも、ルート選択直後に適切な曲を要求する確認を追加。
エミュレーターのPixelcityAudio / AndroidRuntimeログにエラー出力なし。

全20音のPCMでクリッピングしたサンプルは0。BGMのRMSは3,040〜3,680 / 32,768、ピークは15,263〜19,801。
測定値は `artifacts/audio-renewal/levels.json`、ビルドログは同ディレクトリの `gradle-final.log`。
自動測定とAudioTrackの状態確認は、利用者による聴感評価を代替するものではない。

## 実機更新

先行する実機インストール依頼に続き、Pixel 10a（62251JEA315065）を0.3.0から0.3.1へ更新。
`adb -s 62251JEA315065 install --no-incremental -r` がSuccess。
署名は先行版と一致。アプリのアンインストール・データ消去・端末の音量変更は行っていない。
更新前後で `shared_prefs/pixelcity.xml` のSHA-256が一致し、セーブ／設定を保持したことを確認。
更新後のversionCode 6 / versionName 0.3.1と、MainActivityの起動成功を確認。
実機上でのゲームの自動操作や聴感評価は行っていない。

GitHubへのcommit/push、Google Playへのアップロード・公開は行っていない。

## 試聴

- [大都市BGM・24秒](../artifacts/audio-renewal/audio/city-preview.mp3)
- [全15種類のSE](../artifacts/audio-renewal/audio/sfx-preview.mp3)

SEプレビューの順番：タップ→開く→閉じる→道路→住宅→商業→工業→施設建設→撤去→配置不可→進行→人口の節目→モニュメント完成→災害→破産。
各曲全体とSE個別のWAVも同じディレクトリに保存。
