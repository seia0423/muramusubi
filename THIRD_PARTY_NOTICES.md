# Third-party notices

## Countered's Settlement Roads

Mura Musubiの地形対応経路探索、村接続、道路幅展開、道路素材の考え方は、次の公開プロジェクトを調査し、NeoForge 26.2向けに再構成したものです。

- Project: Countered's Settlement Roads
- Source: https://github.com/Coun7ered/settlement-roads-new
- Source revision: `ff2b59deed23a0c0e57f4ae368d20acd2da412b1`
- Upstream license: CC0 1.0 Universal
- License text: https://github.com/Coun7ered/settlement-roads-new/blob/ff2b59deed23a0c0e57f4ae368d20acd2da412b1/LICENSE

移植時にはFabric初期化、Fabric Attachment API、Fabricのワールド生成イベント、Yarn名、MidnightConfigへの依存を使用せず、NeoForge 26.2のAPIとMojang名へ置き換えています。また、元実装のワールド非同期アクセスは採用せず、ブロック変更をサーバーティック上で処理しています。
