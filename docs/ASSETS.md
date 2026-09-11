# 素材・出典

PIXELCITY は、同梱フォントを除くすべての素材を、このリポジトリのコードで作っている。
第三者の画像・音源・データを取り込んでいないので、商用配信の障害になる素材がない。

| 素材 | 作成元 | ライセンス |
|---|---|---|
| タイルのドット絵（地形・道路・区分・施設） | `ui/Sprites.kt` の文字列定義 | MIT |
| モニュメントのドット絵 8種 | `ui/MonumentSprites.kt` の文字列定義 | MIT |
| タイトル画面のビル群 | `ui/TitleView.kt` の描画コード | MIT |
| アプリアイコン | `res/drawable/ic_launcher_foreground.xml`（VectorDrawable） | MIT |
| 4階調パレット | `ui/GbPalette.kt` の色の数値 | 数値のみ。第三者の素材なし |
| DotGothic16-Regular.ttf | [Google Fonts](https://github.com/google/fonts/tree/main/ofl/dotgothic16) / [Fontworks 原典](https://github.com/fontworks-fonts/DotGothic16) | SIL OFL 1.1（改変せず同梱） |

同梱 TTF の SHA-256: `3ad9af88726d42b40f7f365f0dcac785af73cf20ea6f1d5b44e57cc21150b8f1`

## フォントの扱い

DotGothic16 は **SIL Open Font License 1.1**。OFL はアプリへの埋め込み・再配布・
商用利用を認めている。条件は次のとおりで、本プロジェクトはすべて満たしている。

- 著作権表示とライセンス全文を同梱する → `app/src/main/assets/licenses/DotGothic16-OFL.txt`
- フォント単体を販売しない → アプリの一部としてのみ配布
- 予約名（Reserved Font Name）を使った改変版を作らない → 改変していない
- ライセンス全文をユーザーが読める → タイトル画面の「ライセンス」から全文を表示

## モニュメントの扱い

東京タワー、凱旋門、コロッセオなどは実在の建造物だが、本アプリが持つのは
**名称と、その形の特徴をコードで描いた 16×16 のドット絵**だけ。
写真・図面・第三者の3Dモデルやドット絵は使用していない。
解説文もすべて書き下ろしで、百科事典などからの転載はない。

## 商標について

- **「SimCity」「シムシティ」は Electronic Arts の商標**。ストア掲載文・アプリ内・
  リポジトリのいずれでも、この名称や連想を狙う表現を使用しない。
  ジャンルは「都市経営シミュレーション」と記述する。
- **「ゲームボーイ」「Game Boy」は任天堂の商標**。ストア掲載文では使わず、
  「レトロ携帯機風」「8ビット風」「ドット絵」と記述する。
  採用しているのは 4階調の色の数値と 160×144 という解像度であり、
  任天堂の素材・ロゴ・書体・ROM は一切使用していない。

## 通信・個人情報

アプリは一切通信せず、広告・解析・課金を含まない。
保存先は端末内の SharedPreferences のみ。収集する個人情報はない。
