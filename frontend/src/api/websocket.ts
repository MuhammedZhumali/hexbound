export function subscribeGame(
  gameId: string,
  onMessage: () => void,
  onStatus: (s: string) => void,
) {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
  const host = import.meta.env.VITE_API_URL
    ? new URL(import.meta.env.VITE_API_URL).host
    : location.host;
  const ws = new WebSocket(`${protocol}://${host}/ws`);
  let active = true;
  ws.onopen = () => {
    onStatus('connected');
    ws.send('CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0');
    setTimeout(
      () =>
        active &&
        ws.readyState === 1 &&
        ws.send(`SUBSCRIBE\nid:game\ndestination:/topic/games/${gameId}\nack:auto\n\n\0`),
      20,
    );
  };
  ws.onmessage = (e) => {
    if (String(e.data).startsWith('MESSAGE')) onMessage();
  };
  ws.onerror = () => onStatus('reconnecting');
  ws.onclose = () => onStatus('offline');
  return () => {
    active = false;
    ws.close();
  };
}
