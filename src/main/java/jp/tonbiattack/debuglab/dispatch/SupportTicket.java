package jp.tonbiattack.debuglab.dispatch;

/**
 * priorityの値が小さいほど先に配信すべきチケットです。
 */
public record SupportTicket(String id, int priority) {
}
