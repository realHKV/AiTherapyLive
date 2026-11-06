import assert from 'node:assert/strict';
import test from 'node:test';
import { readChatStream } from './chatStream.js';

const requestId = '11111111-1111-4111-8111-111111111111';

function responseFromChunks(chunks) {
  return {
    headers: new Headers({ 'X-Request-Id': requestId }),
    body: new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(chunk);
        controller.close();
      },
    }),
  };
}

test('parses named SSE events across UTF-8 and line boundaries', async () => {
  const wire = [
    `event: start\r\ndata: {"requestId":"${requestId}","userMessageId":"user-1"}\r\n\r\n`,
    `event: delta\ndata: {"requestId":"${requestId}","text":"I hear you 🌱"}\n\n`,
    `event: complete\ndata: {"requestId":"${requestId}","assistantMessageId":"assistant-1"}\n\n`,
  ].join('');
  const bytes = new TextEncoder().encode(wire);
  const chunks = [];
  for (let i = 0; i < bytes.length; i += 3) chunks.push(bytes.slice(i, i + 3));
  const events = [];
  const result = await readChatStream(responseFromChunks(chunks), (name, data) => events.push([name, data]));
  assert.equal(result, requestId);
  assert.deepEqual(events.map(([name]) => name), ['start', 'delta', 'complete']);
  assert.equal(events[1][1].text, 'I hear you 🌱');
});

test('rejects EOF without a terminal event and exposes the request ID for reconciliation', async () => {
  const wire = `event: start\ndata: {"requestId":"${requestId}"}\n\n`;
  await assert.rejects(
    readChatStream(responseFromChunks([new TextEncoder().encode(wire)]), () => {}),
    error => error.requestId === requestId && /before its result/.test(error.message),
  );
});

test('rejects mismatched request IDs', async () => {
  const wire = `event: start\ndata: {"requestId":"other"}\n\n`;
  await assert.rejects(
    readChatStream(responseFromChunks([new TextEncoder().encode(wire)]), () => {}),
    /request ID changed/,
  );
});
