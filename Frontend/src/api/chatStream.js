// A POST response is a byte stream, so neither UTF-8 characters nor SSE lines
// are guaranteed to end at a read() boundary.
export async function readChatStream(response, onEvent) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = '';
  let dataLines = [];
  let requestId = response.headers.get('X-Request-Id');
  let started = false;
  let terminal = false;

  const dispatch = () => {
    if (!dataLines.length) {
      eventName = '';
      return;
    }
    const name = eventName || 'message';
    const payload = JSON.parse(dataLines.join('\n'));
    eventName = '';
    dataLines = [];
    if (!['start', 'delta', 'complete', 'error'].includes(name) || !payload.requestId) {
      throw new Error('Unexpected chat stream event.');
    }
    if (name === 'delta' && typeof payload.text !== 'string') {
      throw new Error('Invalid chat text event.');
    }
    if (requestId && payload.requestId !== requestId) {
      throw new Error('Chat stream request ID changed.');
    }
    requestId = payload.requestId;
    if (terminal || (name !== 'start' && !started) || (name === 'start' && started)) {
      throw new Error('Chat stream events arrived out of order.');
    }
    if (name === 'start') started = true;
    if (name === 'complete' || name === 'error') terminal = true;
    onEvent(name, payload);
  };

  const consume = (text) => {
    buffer += text;
    let newline;
    while ((newline = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, newline).replace(/\r$/, '');
      buffer = buffer.slice(newline + 1);
      if (!line) {
        dispatch();
      } else if (!line.startsWith(':')) {
        const colon = line.indexOf(':');
        const field = colon === -1 ? line : line.slice(0, colon);
        const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
        if (field === 'event') eventName = value;
        if (field === 'data') dataLines.push(value);
      }
    }
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      consume(decoder.decode(value, { stream: true }));
    }
    consume(decoder.decode());
    if (!terminal) throw new Error('Chat connection ended before its result was confirmed.');
    return requestId;
  } catch (error) {
    await reader.cancel().catch(() => {});
    error.requestId = requestId;
    throw error;
  } finally {
    reader.releaseLock();
  }
}
