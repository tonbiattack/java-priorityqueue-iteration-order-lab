# `PriorityQueue`を反復してバッチ抽出し、優先度の低いチケットを先に配信する

Java標準ライブラリの`PriorityQueue`を題材に、**反復順を優先順と誤認してバッチを選び、優先度の低いチケットを先に配信してしまう**問題を、失敗するテスト、原因の直接観測、最小修正、回帰テストの順に追うデバッグ教材です。既定ブランチの`main`は成功状態に保ち、意図的に失敗する状態はGit履歴に独立して残します。

## この題材で守る契約

> 優先度`1`、`4`、`2`の順に`urgent`、`normal`、`soon`を投入し、二件を配信するとき、`urgent`と`soon`をこの順に返して履歴へ記録し、待機キューには`normal`だけを残す。

| 段階 | 実施内容 | 確認すること |
| --- | --- | --- |
| 再現 | 三つの異なる優先度を投入し、二件をバッチ配信する | 直接結果と履歴が`[urgent, normal]`となり、`soon`が待機キューに残る |
| 観測 | 同じキューをstreamと`poll()`で走査する | stream順は`[urgent, normal, soon]`、`poll()`順は`[urgent, soon, normal]`になる |
| 修正 | `poll()`で最大件数まで取り出す | バッチ選択そのものが優先順になる |
| 回帰防止 | 同じサービステストを再実行する | バッチ結果、履歴、待機状態がすべて優先度の契約を満たす |

## 必要な環境

| 項目 | バージョン |
| --- | --- |
| JDK | 21 |
| Maven | 3.8以上 |
| テストランナー | JUnit Jupiter 5.11.4 |
| アプリケーションフレームワーク | 不使用 |

## 最短の開始手順

```bash
mvn --batch-mode clean test
```

検証済みの`main`では、3テストがすべて成功します。

## バグを再現する

```bash
git checkout ee5d595
mvn --batch-mode test -Dtest=TicketDispatchServiceTest
# expected: <[urgent, soon]> but was: <[urgent, normal]>
# 配信済み履歴も [urgent, normal] となり、soonが待機キューに残る

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

バグコミットでは設定やコンパイルではなく、優先順のバッチ抽出契約だけが失敗します。完全な出力は[`evidence/01-bug-service-test-output.txt`](evidence/01-bug-service-test-output.txt)に保存しています。

## 原因の要点

`PriorityQueue`は、最小優先度の要素を先頭として保持します。しかしiteratorとSpliteratorは、要素を特定の順序で走査する保証を持ちません。[1] したがって、`waiting.stream().limit(2)`は優先度が高い二件を選ぶAPIではありません。

優先順に取り出す操作は`poll()`です。`poll()`はキューの先頭を取得し、同時に削除します。[1] 本教材では`poll()`を最大件数まで繰り返すことで、選択・待機キューからの削除・配信履歴への追加を同じ優先順で行います。

## プロジェクト構成

```text
.
├── docs/
│   ├── debugging-record.md      # 観測・仮説・原因・修正・回帰保証
│   ├── novelty-report.md        # 既存Java記事との四軸比較
│   └── topic-brief.md           # 実装前に固定した契約と再現境界
├── evidence/
│   ├── 01-bug-service-test-output.txt
│   ├── 02-priorityqueue-observation-output.txt
│   └── 03-fixed-full-test-output.txt
├── src/main/java/.../dispatch/
│   ├── SupportTicket.java
│   └── TicketDispatchService.java
└── src/test/java/.../dispatch/
    ├── PriorityQueueObservationTest.java
    └── TicketDispatchServiceTest.java
```

詳細な調査手順は[デバッグ記録](docs/debugging-record.md)、既存コンテンツとの差分は[題材重複調査レポート](docs/novelty-report.md)を参照してください。

## スコープ

この教材は、異なる優先度を持つ単一スレッドのインメモリキューだけを対象にします。同じ優先度の安定順序、並行アクセス、優先度の更新、再試行、永続化、複数ワーカー・複数ノードへの配信は対象外です。実運用で同順位の順序を保証する必要がある場合は、優先度に加えてシーケンス番号などをComparatorへ組み込んでください。

## References

[1] [Oracle: `PriorityQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html)
