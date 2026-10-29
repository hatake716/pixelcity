# リリース手順

## コードと検証

- [ ] 現行仕様・掲載文・バージョン番号を最終実装に合わせる。
- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug`。
- [ ] 保存を消してよいエミュレータを明示し、`ANDROID_SERIAL=<emulator> ./gradlew connectedDebugAndroidTest`。
- [ ] 4都市、自由建設、調査、予算、ピンチ、保存再開を目視・操作確認する。
- [ ] チュートリアル全手順、地図重ね表示、配色、音、ランドマーク、破綻後の再開始を確認する。
- [ ] 既存端末に導入する場合は、署名互換とデータ維持を確かめて更新する。

## 配布用ビルド

```bash
./gradlew assembleRelease bundleRelease
```

`keystore.properties`をローカルに設定したときだけrelease署名を行う。署名情報はGitへ追加しない。
設定のない場合の `app-release-unsigned.apk` はそのまま配布・インストールするAPKではない。

- [ ] 署名済みAPK：`apksigner verify --verbose <signed.apk>` で検証し、インストールと起動を確認。
- [ ] AAB：Play向けの署名と内容を確認。AABを端末へ直接インストールしない。
- [ ] R8適用後のreleaseビルドでフォント・保存・描画・音を確認。
- [ ] 最終APK/AABのSHA-256、versionCode、versionName、applicationIdを記録。

## 外部配布

GitHub公開、端末配布、Playへのアップロード・審査・製品公開はそれぞれ別の操作。
依頼された範囲に応じて実行し、実際に完了した段階を記録する。

- [ ] 最終差分・リモート・既存の未コミット変更を確認し、対象ファイルだけをコミット。
- [ ] リリースには検証した署名済みAPKを添付。未署名APKを利用者向けに配布しない。
- [ ] Play掲載文、実画面のスクリーンショット、プライバシー説明、データ安全性回答を確認。
- [ ] Play用AAB準備、Consoleアップロード、審査申請、公開の結果を別々に報告。
