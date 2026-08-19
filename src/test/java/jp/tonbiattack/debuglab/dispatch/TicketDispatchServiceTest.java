package jp.tonbiattack.debuglab.dispatch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class TicketDispatchServiceTest {

    @Test
    void batchDispatch_returnsAndRecordsTheTwoHighestPriorityTickets() {
        TicketDispatchService service = new TicketDispatchService();
        SupportTicket urgent = new SupportTicket("urgent", 1);
        SupportTicket normal = new SupportTicket("normal", 4);
        SupportTicket soon = new SupportTicket("soon", 2);
        service.submit(urgent);
        service.submit(normal);
        service.submit(soon);

        List<SupportTicket> dispatched = service.dispatchBatch(2);

        assertAll(
                () -> assertEquals(List.of(urgent, soon), dispatched,
                        "直接のバッチ結果は優先度1と2のチケットをこの順で返す"),
                () -> assertEquals(List.of(urgent, soon), service.dispatchedTickets(),
                        "配信済み履歴にも優先度1と2だけを記録する"),
                () -> assertEquals(List.of(normal), service.waitingTickets(),
                        "待機キューには優先度4のチケットだけを残す")
        );
    }

    @Test
    void dispatchBatch_withOneTicketKeepsTheExistingPriorityBehavior() {
        TicketDispatchService service = new TicketDispatchService();
        SupportTicket urgent = new SupportTicket("urgent", 1);
        service.submit(urgent);

        List<SupportTicket> dispatched = service.dispatchBatch(2);

        assertAll(
                () -> assertEquals(List.of(urgent), dispatched),
                () -> assertEquals(List.of(urgent), service.dispatchedTickets()),
                () -> assertEquals(List.of(), service.waitingTickets())
        );
    }
}
