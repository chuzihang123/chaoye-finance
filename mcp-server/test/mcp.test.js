import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

test('真实 stdio MCP 握手、发现、登录、查询、写入、刷新、导出与业务错误', async t => {
  const requests = [];
  let refreshCount = 0;
  const spec = {
    openapi: '3.0.1', info: { title: 'Mock JinBooks', version: 'test' },
    paths: {
      '/voucher/fetch': { get: { summary: '凭证查询', parameters: [{ name: 'year', in: 'query', required: true, schema: { type: 'integer' } }] } },
      '/voucher/draft': { post: { summary: '暂存凭证', requestBody: { required: true, content: { 'application/json': { schema: { $ref: '#/components/schemas/Draft' } } } } } },
      '/voucher/get/{id}': { get: {} },
      '/voucher/export': { get: {} },
      '/upload': { post: { requestBody: { content: { 'multipart/form-data': {} } } } },
      '/failure': { get: {} },
      '/delete/{id}': { delete: {} }
    },
    components: { schemas: { Draft: { type: 'object', properties: { amount: { type: 'number' } } } } }
  };
  const backend = http.createServer(async (req, res) => {
    let raw = '';
    for await (const chunk of req) raw += chunk;
    const url = new URL(req.url, 'http://localhost');
    requests.push({ method: req.method, pathname: url.pathname, query: Object.fromEntries(url.searchParams), headers: req.headers, raw });
    res.setHeader('Content-Type', 'application/json');
    const send = value => res.end(JSON.stringify(value));
    const route = url.pathname.replace('/jinbooks-api', '');
    if (route === '/v3/api-docs') return send(spec);
    if (route === '/login/get') {
      res.setHeader('Set-Cookie', 'SESSION=test; HttpOnly');
      return send({ code: 0, data: { captcha: 'OFF', state: 'state-1' } });
    }
    if (route === '/login/signin') {
      assert.equal(JSON.parse(raw).state, 'state-1');
      assert.equal(JSON.parse(raw).password, 'test-password');
      assert.equal(req.headers.cookie, 'SESSION=test');
      return send({ code: 0, data: { token: 'old-token', refresh_token: 'refresh-1', username: 'tester' } });
    }
    if (route === '/auth/token/refresh') {
      refreshCount++;
      assert.equal(url.searchParams.get('refresh_token'), 'refresh-1');
      return send({ code: 0, data: { token: 'new-token', refresh_token: 'refresh-2' } });
    }
    if (req.headers.authorization === 'Bearer old-token') {
      res.statusCode = 401;
      return send({ code: 401 });
    }
    if (route === '/voucher/export') {
      res.setHeader('Content-Type', 'application/octet-stream');
      res.setHeader('Content-Disposition', 'attachment; filename="test.xlsx"');
      return res.end('export-test');
    }
    if (route === '/users/currentUser') return send({ code: 0, data: { id: 'user1', bookId: 'book1', password: 'backend-password' } });
    if (route === '/failure') return send({ code: 2, message: '凭证借贷不平 old-token refresh-1' });
    return send({ code: 0, data: { received: true } });
  });
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve));
  const downloadDir = await mkdtemp(path.join(tmpdir(), 'jinbooks-mcp-test-'));
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('../src/index.js', import.meta.url))],
    env: { ...process.env, JINBOOKS_BASE_URL: `http://127.0.0.1:${backend.address().port}/jinbooks-api`,
      JINBOOKS_TOKEN: '', JINBOOKS_REFRESH_TOKEN: '', JINBOOKS_USERNAME: 'tester', JINBOOKS_PASSWORD: 'test-password', JINBOOKS_DOWNLOAD_DIR: downloadDir, JINBOOKS_UPLOAD_ROOTS: JSON.stringify([downloadDir]) }
  });
  const client = new Client({ name: 'integration-test', version: '1.0.0' });
  t.after(async () => {
    await client.close();
    await new Promise(resolve => backend.close(resolve));
    await rm(downloadDir, { recursive: true });
  });
  await client.connect(transport);
  const call = (name, args = {}) => client.callTool({ name, arguments: args });
  const unpack = result => JSON.parse(result.content[0].text);
  const tools = await client.listTools();
  assert.equal(tools.tools.length, 7);
  assert.equal(tools.tools.find(tool => tool.name === 'jinbooks_execute').annotations.destructiveHint, true);
  assert.equal(unpack(await call('jinbooks_status')).authenticated, false);
  const search = unpack(await call('jinbooks_search_operations', { keyword: '凭证' }));
  assert.equal(search.total, 2);
  const detail = unpack(await call('jinbooks_describe_operation', { operationId: 'POST /voucher/draft' }));
  assert.equal(detail.referencedSchemas['#/components/schemas/Draft'].type, 'object');
  const login = await call('jinbooks_login');
  assert.equal(unpack(login).authenticated, true);
  assert.doesNotMatch(JSON.stringify(login), /old-token|refresh-1|test-password/);
  const missing = await call('jinbooks_execute', { operationId: 'GET /voucher/fetch' });
  assert.equal(missing.isError, true);
  const query = await call('jinbooks_execute', { operationId: 'GET /voucher/fetch', query: { year: 2026 } });
  assert.equal(unpack(query).data.received, true);
  assert.equal(refreshCount, 1);
  assert.equal(requests.at(-1).headers.authorization, 'Bearer new-token');
  assert.equal(requests.at(-1).query.year, '2026');
  await call('jinbooks_execute', { operationId: 'POST /voucher/draft', body: { amount: 12 } });
  assert.equal(JSON.parse(requests.at(-1).raw).amount, 12);
  assert.equal(requests.at(-1).method, 'POST');
  assert.equal((await call('jinbooks_execute', { operationId: 'GET /unknown' })).isError, true);
  assert.equal((await call('jinbooks_execute', { operationId: 'GET /voucher/get/{id}', pathParams: { id: '..' } })).isError, true);
  const exported = unpack(await call('jinbooks_execute', { operationId: 'GET /voucher/export' }));
  assert.equal(await readFile(exported.file, 'utf8'), 'export-test');
  await call('jinbooks_execute', { operationId: 'POST /upload', files: [{ field: 'file', path: exported.file }] });
  assert.match(requests.at(-1).headers['content-type'], /multipart\/form-data; boundary=/);
  assert.match(requests.at(-1).raw, /export-test/);
  const failure = await call('jinbooks_execute', { operationId: 'GET /failure' });
  assert.equal(failure.isError, true);
  assert.match(failure.content[0].text, /凭证借贷不平/);
  assert.doesNotMatch(failure.content[0].text, /old-token|refresh-1/);
  const status = await call('jinbooks_status');
  assert.doesNotMatch(JSON.stringify(status), /backend-password/);
  const dangerous = { operationId: 'DELETE /delete/{id}', pathParams: { id: 'v1' } };
  assert.equal((await call('jinbooks_execute', dangerous)).isError, true);
  const preview = unpack(await call('jinbooks_preview_operation', dangerous));
  assert.equal(preview.context.bookId, 'book1');
  assert.equal(unpack(await call('jinbooks_execute', { ...dangerous, confirmationId: preview.confirmationId })).code, 0);
  assert.equal((await call('jinbooks_execute', { ...dangerous, confirmationId: preview.confirmationId })).isError, true);
});
