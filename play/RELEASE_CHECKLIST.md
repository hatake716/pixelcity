# リリース手順

## ビルド前

- [ ] `docs/SPEC.md` と実装が食い違っていないか
- [ ] `versionCode` を +1、`versionName` を更新（`app/build.gradle.kts`）
- [ ] `./gradlew :app:testDebugUnitTest` が通る
- [ ] `./gradlew :app:lintDebug` に新しい警告がない

## ビルド

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease
```

`keystore.properties`（gitignore 済み）を置くと release が署名される。
未設定なら `app-release-unsigned.apk` が出る。

## 動作確認（エミュレータまたは実機）

release ビルドは minify 済みなので、**必ず release で確認する**。
ProGuard でフォントの読み込みが壊れていないか、ここでしか分からない。

- [ ] タイトルが表示される（日本語が崩れていない）
- [ ] チュートリアルを最後まで進められる
- [ ] チュートリアル完走後、人口が増えて黒字で回る
- [ ] チュートリアルを飛ばして自由に建設できる
- [ ] 保存して再起動すると、街と人口が戻る
- [ ] 人口2000でモニュメントが解禁され、建てられる
- [ ] 破綻するとゲームオーバーになり、もう一度 はじめられる
- [ ] ライセンス画面に OFL 全文が出る

## Play Console

- [ ] データ安全性フォーム: 収集なし・共有なし・通信なし
- [ ] 掲載文に「シムシティ」「ゲームボーイ」を書いていない（`play/STORE_LISTING.md` 参照）
- [ ] スクリーンショット6点
- [ ] 内部テストで1回配信してから製品版へ

## タグとリリース

```bash
git tag -a vX.Y.Z -m "PIXELCITY vX.Y.Z"
git push origin vX.Y.Z
gh release create vX.Y.Z --title "PIXELCITY vX.Y.Z" --notes-file <ノート> \
  app/build/outputs/apk/release/app-release-unsigned.apk LICENSE
```
