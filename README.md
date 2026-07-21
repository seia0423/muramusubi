# Mura Musubi（むらむすび）

Minecraft 26.2 / NeoForge 26.2向けに、村と村を地形に沿った道で結ぶサーバーサイドMODです。

現在は最小実装の `0.1.0-SNAPSHOT` です。Countered's Settlement Roadsの公開ソースを調査し、村構造物の検出、地形対応A*経路探索、道路幅の展開、ブロック敷設をNeoForge 26.2向けに移植・再構成しています。

## 実装済み

- `#minecraft:village` を使った周辺の村構造物の検出
- 4ブロック格子・8方向のA*経路探索
- 高低差、川・海、周辺地形の不安定さを考慮した経路コスト
- 未生成地形を直接変更せず、チャンク生成器から高さとバイオームを取得する探索
- 道路幅に応じた土の道、粗い土、砂利、丸石の混合
- 水面にオーク板材を置く最小限の橋
- 村中心付近を変更しないための始点・終点余白
- 1ティック当たりの上限を守る道路敷設キュー
- 発見済み村、接続済みペア、敷設途中キューのワールド保存
- サーバー再起動後の道路敷設再開と、同じ村ペアの重複生成防止
- 保存済みの村を最小全域木で結ぶ道路ネットワーク計画
- Minecraft API非依存の経路探索単体テスト

## 必要環境

- Minecraft Java Edition 26.2
- NeoForge 26.2.0.25-beta以上
- Java 25

## ゲーム内コマンド

権限レベル2以上で使用します。実際にブロックを変更する前に、ワールドのバックアップを作成してください。

```text
/muramusubi status
/muramusubi plan <fromX> <fromZ> <toX> <toZ>
/muramusubi connect <fromX> <fromZ> <toX> <toZ>
/muramusubi connect-nearest
/muramusubi connect-network
/muramusubi roads
/muramusubi forget <fromX> <fromZ> <toX> <toZ>
```

- `status`: 設定値、発見済み村、接続数、生成中道路、敷設待ちブロック数を表示します。
- `plan`: 指定座標間の地形対応経路だけを計算し、ワールドを変更しません。
- `connect`: 指定座標間の道路を計算し、敷設キューへ追加します。
- `connect-nearest`: 現在地の周辺から接続可能な2つの村を検出し、道路を敷設します。
- `connect-network`: これまでに発見・保存された村を最小全域木で結びます。
- `roads`: 保存済み接続を最大10本まで表示します。
- `forget`: 接続記録と生成待ちを解除します。設置済み道路ブロックは削除しません。

`connect-nearest` で目的の組み合わせが見つからない場合は、2つの村の座標を確認して `connect` を使用できます。

## 設定

初回起動後の共通設定で次を変更できます。

- `maxConnectionDistance`: 村間の最大接続距離
- `roadWidth`: 道幅
- `maxBlocksPerTick`: 1ティックに変更するブロック数
- `maxPathfindingSteps`: A*探索地点数の上限
- `endpointClearance`: 村中心付近で道路を置かない距離
- `maxNetworkRoadsPerCommand`: 1回のネットワーク生成で追加する道路本数

## 開発用コマンド

Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runServer
```

Linux / macOS:

```sh
./gradlew build
./gradlew runServer
```

## 現在の制限

- 村の発見時に自動生成する機能はまだなく、管理コマンドで開始します。
- `forget` は記録と生成待ちだけを解除し、設置済み道路のブロックを元に戻しません。
- `connect-network` の対象は、先に `connect-nearest` で発見・保存された村です。
- 元MODの街灯、標識、ウェイポイント、複数の装飾デザインはまだ移植していません。
- 大規模な橋、トンネル、他の地形生成MODとの互換性は未検証です。

今後の段階分けは [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) を参照してください。

## 元プロジェクトとライセンス

経路探索と道路生成の設計は [Coun7ered/settlement-roads-new](https://github.com/Coun7ered/settlement-roads-new) の公開ソース（`ff2b59deed23a0c0e57f4ae368d20acd2da412b1`）を基にしています。対象ソースはCC0-1.0です。詳しくは [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。

Mura Musubi独自部分の現時点の表記は `All Rights Reserved` です。公開・配布方針に合わせて正式リリース前に決定します。
