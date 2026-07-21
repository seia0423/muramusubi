# Mura Musubi（むらむすび）

Minecraft 26.2 / NeoForge 26.2向けに、村と村を自然な道で結ぶサーバーサイドMODです。

現在は開発の土台となる `0.1.0-SNAPSHOT` です。MODの読み込み、共通設定、道路中心線の計算、管理者向けプレビューコマンド、単体テストまで実装しています。ワールドのブロックはまだ変更しません。

## 必要環境

- Minecraft Java Edition 26.2
- NeoForge 26.2.0.25-beta以上
- Java 25

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

ゲーム内では権限レベル2以上で次のコマンドを使用できます。

```text
/muramusubi status
/muramusubi plan <fromX> <fromZ> <toX> <toZ>
```

`plan` は直線の道路候補を計算するだけで、地形やワールドには変更を加えません。

## 開発方針

詳しい段階分けは [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) を参照してください。

## ライセンス

現時点では `All Rights Reserved` です。公開・配布方針に合わせて正式リリース前に決定します。
