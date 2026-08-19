package jp.tonbiattack.debuglab.dispatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 値が小さいpriorityのチケットを先に配信するサービスです。
 */
public class TicketDispatchService {

    private final PriorityQueue<SupportTicket> waiting = new PriorityQueue<>(
            Comparator.comparingInt(SupportTicket::priority));
    private final List<SupportTicket> dispatched = new ArrayList<>();

    public void submit(SupportTicket ticket) {
        waiting.add(ticket);
    }

    public List<SupportTicket> dispatchBatch(int maxTickets) {
        List<SupportTicket> selected = waiting.stream()
                .limit(maxTickets)
                .toList();
        waiting.removeAll(selected);
        dispatched.addAll(selected);
        return selected;
    }

    public List<SupportTicket> dispatchedTickets() {
        return List.copyOf(dispatched);
    }

    public List<SupportTicket> waitingTickets() {
        PriorityQueue<SupportTicket> copy = new PriorityQueue<>(waiting);
        List<SupportTicket> inPriorityOrder = new ArrayList<>();
        while (!copy.isEmpty()) {
            inPriorityOrder.add(copy.poll());
        }
        return List.copyOf(inPriorityOrder);
    }
}
