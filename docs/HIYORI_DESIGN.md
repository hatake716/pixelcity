# 秘書ひよりのキャラクターデザイン

0.2.2で就任日誌の肖像を刷新。2026-09-12制作。

## デザイン

市長と一緒に街を育てる、20代半ばの成人の秘書。
かわいらしさ、親しみやすさ、仕事を任せられる落ち着きを表現した。

- 栗色の柔らかなウェーブと、クリーム色のリボンでまとめたハーフアップ。
- 翡翠色の瞳、目元の光、ほんのり赤い頬、やさしい笑顔。
- 深緑のジャケット、アイボリーのブラウス、首元のリボン。
- 街の明るい未来をイメージした、小さな金色の太陽のピン。
- 紙色の会話欄になじむ、温かいクリーム色の背景。

## 同梱・描画

素材: `app/src/main/res/drawable-nodpi/hiyori_portrait.png`。
内蔵のimagegenツールで生成したオリジナル画像を、そのままPNGとして同梱した。
外部のキャラクター画像・写真・参考イラストの切り抜きは使用していない。
生成画像の追加編集、外部API・CLIによる生成は行っていない。

会話欄は112×112論理ピクセルの肖像枠と、名前・場面名・進行状況を並べる。
旧44×52の簡易肖像から拡大。元画像は初回にサンプリングして読み込み、GameView内で再利用する。
街のパレットへ減色せず、枠の内側へAndroid Canvasで縮小描画し、瞳や髪の色を保つ。
予算などの別画面、日誌終了後には肖像を重ねない。

## 生成プロンプト

制作方法: built-in `image_gen` / `imagegen` skill。

```text
Use case: stylized-concept
Asset type: original character portrait for the secretary dialogue panel of PIXELCITY, a warm, charming city-building game.
Primary request: Create a SUPER CUTE, beautiful anime heroine secretary named Hiyori, an unmistakably adult woman in her mid-twenties. She is the player's welcoming, capable city-hall secretary. Make an exceptionally appealing, polished character illustration players will love seeing in every dialogue.
Subject/design: warm chestnut-brown shoulder-length hair with soft flowing curls, long gently swept bangs, a small half-up hair arrangement secured by an elegant cream ribbon visible near the side of her head; luminous large emerald/jade green eyes with fine lashes and beautiful clear catchlights; rosy cheeks; charming joyful closed-mouth smile; refined attractive adult facial proportions. Deep forest-green tailored office blazer, ivory high-neck blouse with a neat cream ribbon at the collar, tiny brass sun-shaped lapel pin. No school uniform. Professional, kind and enthusiastic. Head tilted just a little, meeting viewer's gaze.
Style: exquisite clean 2D Japanese anime game character art, elegant detailed linework, soft warm cel shading with delicate highlights, readable strong silhouette, lovely expressive eyes, tastefully understated office clothing. Detailed face and hair, simple background. NOT pixel art, not 3D.
Composition: ONE character, centered square portrait, head and shoulders with upper chest filling the canvas, full top of hair included with 5 percent margin, both shoulders visible, lower chest cut naturally by the bottom of frame. Face occupies a large part of the picture for excellent readability at 200x200 pixels. No hands or extra objects. No panels or multiple views.
Background: perfectly flat warm pale cream #f3e4cc, no scenery, no border, no lettering.
Lighting/mood: gentle morning light, warm and approachable, a bright optimistic smile.
Constraints: original fictional character; distinctly adult, not a child, not a chibi; modest professional outfit; no text, watermark, logos, signatures, or UI. 1024x1024 square image.
```

## 実装・表示確認

Android 15 / API 35の専用エミュレーター（1080×2400）で確認した。

- `assembleDebug` 成功。
- `StoryFlowTest` 3件成功。新規開始から結末までの会話・選択・建設・予算操作、保存復元、メニューの時間停止を再確認。
- 開始画面と公共投資の選択画面を目視し、肖像・名前・本文・返答ボタンが重ならないことを確認。
- `lintDebug` エラー0件、既存警告46件。`git diff --check` 成功。
- [実画面：着任](../artifacts/hiyori-validation/opening.png) / [公共投資の選択](../artifacts/hiyori-validation/choice.png)。
- 実行ログ: `artifacts/hiyori-validation/gradle.log`。

開発署名付きAPK: `artifacts/PIXELCITY-0.2.2-debug.apk`（versionCode 4、5,843,915 bytes）。
`apksigner verify` 成功、v2署名。実機へのインストール・外部公開は行っていない。

APK SHA-256: `33e3125b04554cc5299c6bcb46905859d4cbd676729b3830da6d31c9f4219094`。
肖像PNG SHA-256: `b3ae72fa049b6492cd8f9b0ff087af3f6e74ab36d0f1e49863245dcf76f15c7c`。
