package jp.tonbiattack.debuglab.dispatch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import org.junit.jupiter.api.Test;

class PriorityQueueObservationTest {

    @Test
    void iteratorTraversalDiffersFromPollBasedPriorityTraversal() {
        SupportTicket urgent = new SupportTicket("urgent", 1);
        SupportTicket normal = new SupportTicket("normal", 4);
        SupportTicket soon = new SupportTicket("soon", 2);
        PriorityQueue<SupportTicket> queue = new PriorityQueue<>(
                Comparator.comparingInt(SupportTicket::priority));
        queue.add(urgent);
        queue.add(normal);
        queue.add(soon);

        List<SupportTicket> iteratorOrder = queue.stream().toList();
        List<SupportTicket> pollOrder = new ArrayList<>();
        PriorityQueue<SupportTicket> copy = new PriorityQueue<>(queue);
        while (!copy.isEmpty()) {
            pollOrder.add(copy.poll());
        }

        assertAll(
                () -> assertEquals(List.of(urgent, normal, soon), iteratorOrder,
                        "この固定入力に対するiterator順は優先順ではない"),
                () -> assertEquals(List.of(urgent, soon, normal), pollOrder,
                        "pollは先頭から優先順に取り出す")
        );
    }
}
