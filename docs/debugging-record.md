# E004: `PriorityQueue`を反復して優先度の低いチケットを先に配信する

## 目的

チケットのpriorityは値が小さいほど優先されます。`urgent`（1）、`normal`（4）、`soon`（2）を順に投入し、二件を配信する場合、`urgent`と`soon`をこの順で選び、配信済み履歴へ記録し、`normal`だけを待機させる必要があります。

## 実行環境と再現境界

このラボはJava 21、Maven、JUnit Jupiter 5.11.4だけを使います。フレームワーク、HTTP、ファイル、データベース、並行処理は使いません。公開境界は`TicketDispatchService#dispatchBatch(int)`であり、直接の戻り値に加えて、`dispatchedTickets()`と`waitingTickets()`の最終状態を別々に読みます。

固定の三チケットとバッチサイズ`2`だけを使うため、時刻、乱数、`sleep`、外部I/Oに依存しません。`waitingTickets()`はキューのコピーから`poll()`して返すため、観測自体が待機キューを変更しません。

## 最初に観測した事実

バグ状態はコミット[`ee5d595`](../commit/ee5d595)です。次のコマンドで、意図したアサーション差分を確認しました。

```bash
git checkout ee5d595
mvn --batch-mode test -Dtest=TicketDispatchServiceTest
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 直接のバッチ結果 | `[urgent(1), soon(2)]` | `[urgent(1), normal(4)]` | `TicketDispatchServiceTest` |
| 配信済み履歴 | `[urgent(1), soon(2)]` | `[urgent(1), normal(4)]` | `TicketDispatchService#dispatchedTickets()` |
| 待機キュー最終状態 | `[normal(4)]` | `[soon(2)]` | `TicketDispatchService#waitingTickets()` |
| 同じキューのstream順 | 優先順ではない | `[urgent(1), normal(4), soon(2)]` | `PriorityQueueObservationTest` |
| 同じキューのpoll順 | `[urgent(1), soon(2), normal(4)]` | `[urgent(1), soon(2), normal(4)]` | `PriorityQueueObservationTest` |

```text
直接のバッチ結果は優先度1と2のチケットをこの順で返す
==> expected: <[SupportTicket[id=urgent, priority=1], SupportTicket[id=soon, priority=2]]>
but was: <[SupportTicket[id=urgent, priority=1], SupportTicket[id=normal, priority=4]]>

待機キューには優先度4のチケットだけを残す
==> expected: <[SupportTicket[id=normal, priority=4]]>
but was: <[SupportTicket[id=soon, priority=2]]>
```

完全な失敗出力は[`evidence/01-bug-service-test-output.txt`](../evidence/01-bug-service-test-output.txt)に保存しています。直接の戻り値だけでなく、配信履歴と待機キューを最終状態として分けて確認したため、表示順だけの問題ではなく、誤ったチケットを実際に配信・削除したことを確定できます。

## 競合仮説と検証

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| Comparatorが優先度を逆順に比較している | 同じ三チケットをコピーキューから`poll()`し、取り出し順を観測する | `poll()`順は`1, 2, 4`。Comparatorは正しいため棄却。 |
| 投入処理がチケットをキューへ正しく登録していない | stream観測に三チケットが含まれ、`poll()`で三件すべて取り出せることを確認する | 三チケットは存在するため棄却。 |
| iterator／streamの走査順を優先順と誤認している | 同じキューでstream順とpoll順を比較する | stream順は`1, 4, 2`、poll順は`1, 2, 4`。採用。 |

## 確定した原因

バグ状態のサービスは、バッチ対象を次のように選んでいました。

```java
List<SupportTicket> selected = waiting.stream()
        .limit(maxTickets)
        .toList();
```

`PriorityQueue`のiteratorおよびSpliteratorは、要素を特定の順序で走査する保証を持ちません。[1] キューの内部は優先ヒープであり、先頭だけが最小優先度になるよう保たれます。したがってstreamの先頭二件は「優先度が高い二件」ではありません。

`PriorityQueueObservationTest`は同じ固定キューで、stream順が`1, 4, 2`、`poll()`順が`1, 2, 4`となることを直接示します。原因はComparatorや登録処理ではなく、反復順と取り出し順を混同したことです。

## 最小修正

修正コミットは[`59fc137`](../commit/59fc137)です。バッチ選択を`poll()`の反復に置き換えました。

```java
List<SupportTicket> selected = new ArrayList<>();
while (selected.size() < maxTickets && !waiting.isEmpty()) {
    selected.add(waiting.poll());
}
```

`poll()`はキューの先頭を取得・削除します。`PriorityQueue`の先頭は、指定したComparatorに対する最小要素です。[1] そのため、このループはバッチ選択と待機キューからの削除を同じ優先順で行います。

stream後に明示的な`sorted`を加える修正も可能ですが、今回の公開契約は「優先順にキューから取り出して配信する」ことです。`poll()`はこのキュー操作を直接表すため採用しました。反復順に依存したまま後処理だけを並べ替える修正や、テスト期待値を内部順へ変更する修正は採用しませんでした。

## 回帰保証

### 再発防止テスト

最初に失敗した`batchDispatch_returnsAndRecordsTheTwoHighestPriorityTickets`はそのまま残しています。このテストは、直接のバッチ結果、配信済み履歴、待機キュー最終状態を別々に検証します。

| テスト | 回帰として守る契約 |
| --- | --- |
| `batchDispatch_returnsAndRecordsTheTwoHighestPriorityTickets` | 異なる三優先度から、最上位二件を順に配信し、最下位だけを待機させる。 |
| `dispatchBatch_withOneTicketKeepsTheExistingPriorityBehavior` | 要素数がバッチサイズ未満でも、唯一のチケットを正しく配信する。 |
| `iteratorTraversalDiffersFromPollBasedPriorityTraversal` | 反復順とpoll順が別の概念であることを、固定入力で直接観測する。 |

修正後の`mvn --batch-mode clean test`では、3テストがすべて成功しました。完全な出力は[`evidence/03-fixed-full-test-output.txt`](../evidence/03-fixed-full-test-output.txt)に保存しています。

## 再現手順

```bash
git checkout ee5d595
mvn --batch-mode test -Dtest=TicketDispatchServiceTest
# expected: [urgent, soon], but was: [urgent, normal]

git checkout main
mvn --batch-mode clean test
# Tests run: 3, Failures: 0, Errors: 0
```

## スコープと注意点

この修正は、単一スレッドで優先度が異なる要素を取り出す場合に有効です。同じ優先度を持つ要素の順序は`PriorityQueue`では任意です。FIFOなどの安定順序が必要なら、シーケンス番号を加えたComparatorを設計してください。

また、`PriorityQueue`はスレッド安全ではありません。複数スレッドが同時に変更する実運用では、排他制御または用途に合う同時実行コレクションを別途選択する必要があります。本ラボの`poll()`ループだけで、並行処理の原子性や配送保証が得られるわけではありません。

## References

[1] [Oracle: `PriorityQueue`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html)
