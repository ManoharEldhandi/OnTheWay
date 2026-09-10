import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useOrderRealtime } from '../realtime';
import type { OrderMessage } from '../types';

interface OrderChatProps {
  orderId: number;
  active: boolean;
  compact?: boolean;
}

/** A focused, order-only conversation. Updates arrive through the same scoped event stream. */
export function OrderChat({ orderId, active, compact = false }: OrderChatProps) {
  const { user } = useAuth();
  const [messages, setMessages] = useState<OrderMessage[]>([]);
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const endRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async () => {
    if (!active) return;
    try {
      const data = await api.get<OrderMessage[]>(`/api/orders/${orderId}/messages`);
      setMessages(data);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load order messages');
    }
  }, [active, orderId]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { endRef.current?.scrollIntoView({ block: 'nearest' }); }, [messages.length]);

  useOrderRealtime(useCallback((event) => {
    if (event.type === 'ORDER_CHAT_MESSAGE' && Number(event.orderId) === orderId) void load();
  }, [load, orderId]));

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const text = body.trim();
    if (!text || sending) return;
    setSending(true);
    try {
      await api.post(`/api/orders/${orderId}/messages`, { body: text });
      setBody('');
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send message');
    } finally {
      setSending(false);
    }
  }

  if (!active) {
    return (
      <section className="chat-card chat-locked">
        <span className="kicker">Order chat</span>
        <strong>Chat opens when the shop accepts your order.</strong>
        <span className="muted small">Use it for a quick pickup note or a change request while the order is active.</span>
      </section>
    );
  }

  return (
    <section className={`chat-card ${compact ? 'compact' : ''}`} aria-label={`Order ${orderId} chat`}>
      <div className="spread">
        <div><span className="kicker">Order chat</span><strong>Quick changes, in one thread.</strong></div>
        <span className="chat-live"><i />Live</span>
      </div>
      <div className="chat-messages" aria-live="polite">
        {messages.length === 0 && <div className="chat-empty">No messages yet. Send a note about this pickup.</div>}
        {messages.map((message) => {
          const mine = message.senderUserId === user?.userId;
          return <div className={`chat-bubble ${mine ? 'mine' : ''}`} key={message.orderMessageId}>
            <span>{message.body}</span>
            <small>{mine ? 'You' : message.senderName} · {new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</small>
          </div>;
        })}
        <div ref={endRef} />
      </div>
      {error && <div className="error small">{error}</div>}
      <form className="chat-compose" onSubmit={send}>
        <textarea
          aria-label="Message for this order"
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="e.g. Please keep the drink less sweet"
          maxLength={600}
          rows={compact ? 2 : 3}
        />
        <div className="spread"><span className="muted small">Visible only to you and this shop.</span><button className="primary" disabled={sending || !body.trim()} type="submit">{sending ? 'Sending…' : 'Send'}</button></div>
      </form>
    </section>
  );
}
