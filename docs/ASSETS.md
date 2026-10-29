# 球体都市のフェルトモデル（0.6.0）

既存の322種類の建築・車両・人物デザインを、球面上で描画する3Dメッシュへ変換。
`assets/globe/`には通常モデル322個と遠景モデル322個、頂点数・外形範囲を記録した`manifest.json`を収録する。独自モデルと生成コードのMITライセンス、記念建築のCC0参考写真の来歴を引き継ぐ。

再生成はBlenderで`tools/realistic/export_globe_models.py`を実行し、続いてPythonで`tools/realistic/build_globe_lods.py`を実行する。
後者は遠景モデルと選択判定用の外形範囲を生成するため、必ず両方を実行する。
`.meshdata`はgzip形式の本体に、頂点数と位置・法線・色を記録した独自形式。Androidの包装処理による`.gz`の自動展開を避けるため専用拡張子を使う。
通常モデルは最大95,532頂点、遠景モデルは最大1,791頂点。縫い糸の個別形状を省略し、球体表示では織り目をシェーダーで描く。
画像版の建築・地形はメニュー・タイトル・旧描画の回帰確認に引き続き使用する。

---

# フェルト素材の導入（0.4.1）

建築・車・樹木113画像と、草地・砂浜・道路・海の4素材を独自制作のフェルト表現へ変更。
素材・生成コードはMIT。外部写真テクスチャと植物メッシュは現行描画に使用しない。
[構成と再生成](FELT_VISUALS.md)、[現行素材台帳](../visual-source/felt-sources.json)。
記念建築の形状を作る際のCC0参考写真は、以下の0.4.0の記録を引き継ぐ。

---

# 素材と作成元

## 現在の3D・写真素材（0.4.0）

現在の都市描画は`ui/RealisticCityRenderer.kt`と`assets/realistic/`を使用する。
独自の3D建築・車両は`tools/realistic/build_assets.py`から生成する。
写真参照8点、PBR素材7点、植物モデル2点はすべてCC0。
出典・作者・ライセンスURL・改変・ハッシュを`visual-source/sources.json`に記録する。
モニュメントは写真を参考に制作した3Dモデルで、測量・写真測量の成果物ではない。
タイトル書体OutfitはGoogle Fonts配布のSIL OFL 1.1。本文はAndroid標準フォント。
生成方法と表現の範囲は[REALISTIC_VISUALS.md](REALISTIC_VISUALS.md)を参照。

## 以前のパレット描画（0.3.xまで）

以下は履歴・比較用として保持する旧描画の来歴であり、現在の本編の素材一覧ではない。

コードと独自素材はリポジトリのMITライセンスに従う。同梱フォントには別のライセンスを適用する。

| 素材 | 作成元 | ライセンス・備考 |
|---|---|---|
| 住宅・商業・工業54種、公共/インフラ20種 | `ui/Architecture.kt`、`ui/IsoBuildings.kt` | オリジナルの手続き描画、MIT |
| 草・水・岸・道路・線路・農地 | `ui/IsoTiles.kt` | 手続き描画、MIT |
| 世界の建築8種 | `ui/MonumentSprites.kt` | 特徴を模式的に描いたオリジナル絵、MIT |
| 歩行者・樹木・ヨット・公園・街路設備 | `ui/Architecture.kt` | オリジナル描画、MIT |
| 車7種と走行アニメーション | `ui/Vehicles.kt`、`ui/TrafficAnimation.kt` | オリジナル描画、MIT |
| 127色のパレットと縮小処理 | `ui/Palette.kt`、`ui/SpriteScale.kt` | 独自コード、MIT |
| タイトル・お手本都市 | `ui/TitleView.kt`、`ui/ShowcaseCity.kt` | 本編と同じ描画処理、MIT |
| 秘書ひよりの肖像 | `res/drawable-nodpi/hiyori_portrait.png` | 内蔵imagegenで制作したオリジナルのキャラクター画像。[生成条件・来歴](HIYORI_DESIGN.md) |
| 就任日誌・会話欄 | `ui/TutorialPanel.kt`、`game/Tutorial.kt` | 独自のUIコードと会話、MIT |
| アイコン | `res/drawable-nodpi/launcher_felt_*.png`、`tools/realistic/build_icon.py` | 独自制作のフェルト3Dモデル。通常・テーマアイコン対応、MIT。[制作と確認](FELT_ICON.md) |
| BGM・効果音 | `audio/Bgm.kt`、`audio/Sfx.kt`、`audio/Instruments.kt`、`audio/Waves.kt` | 独自の5曲・15効果音を合成したPCM、MIT。[音の設計](AUDIO_DESIGN.md) |
| DotGothic16-Regular.ttf | Google Fonts / Fontworks DotGothic16 | SIL OFL 1.1、改変せず同梱 |

フォントの出典は [Google Fontsの配布元](https://github.com/google/fonts/tree/main/ofl/dotgothic16) と
[DotGothic16のプロジェクト](https://github.com/fontworks-fonts/DotGothic16)。
著作権表示と全文は `app/src/main/assets/licenses/DotGothic16-OFL.txt` にあり、アプリのライセンス画面から読める。
同梱TTFのSHA-256：`3ad9af88726d42b40f7f365f0dcac785af73cf20ea6f1d5b44e57cc21150b8f1`。

## 建築刷新の参考資料

依頼時に提示された4点の街のイラストは、色彩・屋根の変化・緑・店先・街の賑わいを考えるための参考とした。
その画像ファイル、切り抜き、ロゴ、看板、第三者のスプライトはアプリやリポジトリに収録しない。
新しい建築と街路の絵はArchitecture.ktの形状と描画処理から生成する。
0.3.xまでの建築には生成AI画像や写真テクスチャ、外部の3Dモデルは使用していなかった。
0.2.2以降の秘書ひよりの肖像には、上記の生成AI画像を同梱する。

モニュメントは写真や測量データの再現ではなく、ゲーム用の模式表現。
素材の来歴の記録は、実在の建築物・名称などに関する包括的な権利確認を意味するものではない。
公開時の権利表示・掲載内容は、配布物と地域に合わせて確認する。

## 保存と通信

アプリにネットワーク権限、広告、解析SDK、課金機能はない。
セーブと設定はSharedPreferencesに保存する。AndroidのOSバックアップと端末移行に対象設定を含めているため、
「保存データは常に端末の外へ出ない」「アンインストール後に必ず戻らない」とは説明しない。


## 地球の観測資料（0.5.1）

- NASA Blue Marble Next Generation、2004年7月の衛星合成画像。NASA Earth Observatory / Reto Stöckli。
- NASA公開のGEBCO由来の陸域標高・海底地形の濃淡図。NASA Earth Observatory / Jesse Allen、GEBCO / British Oceanographic Data Centre。
- 原資料URL、取得ファイルのSHA-256、加工後ファイルのSHA-256、権利条件、変換内容は `app/src/main/assets/earth/sources.json` に収録。
- 衛星画像を4096×2048へ縮小。地形資料を512×512の等積格子へ再投影。表示上の高さを65倍に強調。
- 素材の出典と加工内容はアプリのライセンス画面でも読める。NASAの使用ガイドとGEBCOの公開データ条件に従い出典を記載。コードのMITライセンスとは区別する。
- 再生成スクリプトは `tools/planet/`。元の大容量画像は `artifacts/earth/` に保存し、Git管理には含めない。
