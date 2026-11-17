import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { createHttpServer } from '../src/http.js';

test('HTTP MCP 鉴权、Origin 检查、会话隔离及真实 SDK 调用', async t => {
  const backend = createServer(async (req, res) => {
    for await (const chunk of req) { /* consume request */ }
    res.setHeader('Content-Type', 'application/json');
    const route = req.url.split('?')[0];
    let data;
    if (route.endsWith('/v3/api-docs')) data = { openapi: '3.0.1', paths: {}, info: { title: 'HTTP test' } };
    else if (route.endsWith('/login/get')) data = { code: 0, data: { captcha: 'OFF', state: 'test' } };
    else if (route.endsWith('/login/signin')) data = { code: 0, data: { token: 'session-token', username: 'test' } };
    else data = { code: 0, data: { id: 'user1', bookId: 'book1' } };
    res.end(JSON.stringify(data));
  });
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve));
  const token = 'test-http-token-at-least-32-characters';
  const app = createHttpServer({
    CHAOYE_FINANCE_MCP_TOKEN: token,
    CHAOYE_FINANCE_BASE_URL: `http://127.0.0.1:${backend.address().port}/chaoye-api`,
    CHAOYE_FINANCE_USERNAME: 'test', CHAOYE_FINANCE_PASSWORD: 'test-password'
  });
  await new Promise(resolve => app.server.listen(0, '127.0.0.1', resolve));
  const url = new URL(`http://127.0.0.1:${app.server.address().port}/mcp`);
  const clients = [];
  t.after(async () => {
    for (const { client, transport } of clients) { await transport.terminateSession(); await client.close(); }
    await app.close();
    await new Promise(resolve => backend.close(resolve));
  });
  assert.equal((await fetch(url)).status, 401);
  assert.equal((await fetch(url, { headers: { Authorization: `Bearer ${token}`, Origin: 'https://untrusted.example' } })).status, 403);
  assert.equal((await fetch(url, { headers: { Authorization: `Bearer ${token}`, 'mcp-session-id': 'unknown' } })).status, 404);
  for (let i = 0; i < 2; i++) {
    const transport = new StreamableHTTPClientTransport(url, { requestInit: { headers: { Authorization: `Bearer ${token}` } } });
    const client = new Client({ name: `http-client-${i}`, version: '1.0.0' });
    await client.connect(transport);
    clients.push({ client, transport });
    assert.equal((await client.listTools()).tools.length, 7);
  }
  const login = await clients[0].client.callTool({ name: 'chaoye_finance_login', arguments: {} });
  assert.equal(JSON.parse(login.content[0].text).authenticated, true);
  const first = await clients[0].client.callTool({ name: 'chaoye_finance_status', arguments: {} });
  const second = await clients[1].client.callTool({ name: 'chaoye_finance_status', arguments: {} });
  assert.equal(JSON.parse(first.content[0].text).authenticated, true);
  assert.equal(JSON.parse(second.content[0].text).authenticated, false);
});
