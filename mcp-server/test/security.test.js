import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { JinbooksClient } from '../src/client.js';

function fixture() {
  const client = new JinbooksClient({ JINBOOKS_TOKEN: 'original-token', JINBOOKS_REFRESH_TOKEN: 'original-refresh', JINBOOKS_PASSWORD: 'test-password' });
  client.spec = {
    openapi: '3.0.1',
    paths: {
      '/draft': { post: { requestBody: { $ref: '#/components/requestBodies/Draft' } } },
      '/delete/{id}': { delete: { parameters: [{ name: 'id', in: 'path', required: true, schema: { type: 'string' } }] } },
      '/fetch': { get: { parameters: [{ name: 'year', in: 'query', required: true, schema: { type: 'integer', minimum: 2000 } }] } },
      '/upload': { post: { requestBody: { required: true, content: { 'multipart/form-data': { schema: { type: 'object', required: ['file'], properties: { file: { type: 'string', format: 'binary' } } } } } } } }
    },
    components: {
      requestBodies: { Draft: { required: true, content: { 'application/json': { schema: { $ref: '#/components/schemas/Draft' } } } } },
      schemas: {
        Draft: { type: 'object', required: ['items'], properties: { items: { type: 'array', minItems: 1, items: { $ref: '#/components/schemas/Item' } } } },
        Item: { type: 'object', required: ['amount', 'kind'], properties: { amount: { type: 'number', minimum: 0, exclusiveMinimum: true }, kind: { type: 'string', enum: ['debit', 'credit'] } } }
      }
    }
  };
  const calls = [];
  client.request = async (route, options) => {
    calls.push({ route, options });
    if (route === '/users/currentUser') return { code: 0, data: { id: 'u1', bookId: 'b1' } };
    return { code: 0 };
  };
  return { client, calls };
}

test('按引用 requestBody 和嵌套 Schema 校验类型、必填、枚举及范围', async () => {
  const { client, calls } = fixture();
  for (const body of [undefined, 'bad', {}, { items: [] }, { items: [{ amount: '1', kind: 'debit' }] }, { items: [{ amount: 0, kind: 'debit' }] }, { items: [{ amount: 1, kind: 'unknown' }] }]) {
    await assert.rejects(client.execute({ operationId: 'POST /draft', body }));
  }
  assert.equal(calls.length, 0);
  await client.execute({ operationId: 'POST /draft', body: { items: [{ amount: 1, kind: 'debit' }] } });
  assert.equal(calls.length, 1);
  const detail = await client.describe('POST /draft');
  assert.ok(detail.referencedSchemas['#/components/requestBodies/Draft']);
});

test('拒绝错误查询参数、未声明字段及错误路径类型', async () => {
  const { client, calls } = fixture();
  for (const query of [{}, { year: '2026' }, { year: {} }, { year: 1999 }, { year: 2026, unexpected: 1 }]) {
    await assert.rejects(client.execute({ operationId: 'GET /fetch', query }));
  }
  await assert.rejects(client.execute({ operationId: 'DELETE /delete/{id}', pathParams: { id: 123 } }));
  assert.equal(calls.length, 0);
});

test('危险操作必须预览，凭据绑定参数、有效期、账套且单次使用', async () => {
  const { client, calls } = fixture();
  const args = { operationId: 'DELETE /delete/{id}', pathParams: { id: 'v1' } };
  await assert.rejects(client.execute(args), /请先使用 jinbooks_preview_operation/);
  const preview = await client.preview(args);
  assert.equal(preview.context.bookId, 'b1');
  assert.equal(calls.filter(call => call.route.startsWith('/delete')).length, 0);
  await assert.rejects(client.execute({ ...args, pathParams: { id: 'v2' }, confirmationId: preview.confirmationId }), /已变化/);
  await client.execute({ ...args, confirmationId: preview.confirmationId });
  await assert.rejects(client.execute({ ...args, confirmationId: preview.confirmationId }), /请先使用 jinbooks_preview_operation/);
  const expired = await client.preview(args);
  client.confirmations.get(expired.confirmationId).expiresAt = 0;
  await assert.rejects(client.execute({ ...args, confirmationId: expired.confirmationId }), /请先使用 jinbooks_preview_operation/);
  const changed = await client.preview(args);
  client.request = async () => ({ data: { id: 'u1', bookId: 'b2' } });
  await assert.rejects(client.execute({ ...args, confirmationId: changed.confirmationId }), /已变化/);
});

test('并发执行仅能消费一次确认凭据', async () => {
  const { client, calls } = fixture();
  const args = { operationId: 'DELETE /delete/{id}', pathParams: { id: 'v1' } };
  const { confirmationId } = await client.preview(args);
  const results = await Promise.allSettled([client.execute({ ...args, confirmationId }), client.execute({ ...args, confirmationId })]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  assert.equal(calls.filter(call => call.route.startsWith('/delete')).length, 1);
});

test('查询用户账套期间凭据过期时阻止业务执行', async () => {
  const { client, calls } = fixture();
  const args = { operationId: 'DELETE /delete/{id}', pathParams: { id: 'v1' } };
  const { confirmationId } = await client.preview(args);
  const request = client.request;
  client.request = async (route, options) => {
    const result = await request(route, options);
    if (route === '/users/currentUser') client.confirmations.get(confirmationId).expiresAt = 0;
    return result;
  };
  await assert.rejects(client.execute({ ...args, confirmationId }), /确认凭据已过期/);
  assert.equal(calls.filter(call => call.route.startsWith('/delete')).length, 0);
});

test('成功结果和错误文本脱敏，并保留旧令牌历史和字段 Schema', () => {
  const { client } = fixture();
  client.setTokens({ data: { token: 'new-token', refresh_token: 'new-refresh' } });
  const result = client.sanitize({ data: { password: 'other-password', access_token: 'unknown-token', nested: ['original-token original-refresh new-token new-refresh test-password'] } });
  assert.equal(result.data.password, '[REDACTED]');
  assert.equal(result.data.access_token, '[REDACTED]');
  assert.doesNotMatch(JSON.stringify(result), /original-token|original-refresh|new-token|new-refresh|test-password|other-password|unknown-token/);
  assert.deepEqual(client.sanitize({ properties: { password: { type: 'string' } } }), { properties: { password: { type: 'string' } } });
});

test('重启后首次业务操作可使用配置账号重新登录', async () => {
  const { client, calls } = fixture();
  client.token = '';
  client.username = 'tester';
  let logins = 0;
  client.login = async () => { logins++; client.setTokens({ data: { token: 'recovered-token' } }); };
  await client.execute({ operationId: 'GET /fetch', query: { year: 2026 } });
  await client.execute({ operationId: 'GET /fetch', query: { year: 2026 } });
  assert.equal(logins, 1);
  assert.equal(calls.length, 2);
});

test('后端 OpenAPI 包含上下文前缀时仅发送一次前缀', async () => {
  const { client, calls } = fixture();
  client.spec.paths['/jinbooks-api/fetch'] = client.spec.paths['/fetch'];
  delete client.spec.paths['/fetch'];
  const operation = await client.operation('GET /fetch');
  assert.equal(operation.path, '/fetch');
  await client.execute({ operationId: 'GET /fetch', query: { year: 2026 } });
  assert.equal(calls[0].route, '/fetch');
});

test('上传拒绝相对路径、越界、缺失、目录、过大文件及目录链接逃逸', async t => {
  const root = await mkdtemp(path.join(tmpdir(), 'jinbooks-upload-test-'));
  const outside = await mkdtemp(path.join(tmpdir(), 'jinbooks-outside-test-'));
  t.after(async () => { await rm(root, { recursive: true }); await rm(outside, { recursive: true }); });
  await writeFile(path.join(root, 'small.txt'), 'ok');
  await writeFile(path.join(root, 'large.txt'), 'oversize');
  await writeFile(path.join(outside, 'private.txt'), 'outside');
  await symlink(outside, path.join(root, 'escape'), process.platform === 'win32' ? 'junction' : 'dir');
  const { client, calls } = fixture();
  client.uploadRoots = [root];
  client.maxUploadBytes = 4;
  for (const file of ['relative.txt', path.join(outside, 'private.txt'), path.join(root, 'missing'), root, path.join(root, 'large.txt'), path.join(root, 'escape', 'private.txt')]) {
    await assert.rejects(client.execute({ operationId: 'POST /upload', files: [{ field: 'file', path: file }] }));
  }
  assert.equal(calls.length, 0);
  await assert.rejects(client.execute({ operationId: 'POST /upload', files: [{ field: 'unknown', path: path.join(root, 'small.txt') }] }), /未定义的文件字段/);
  await client.execute({ operationId: 'POST /upload', files: [{ field: 'file', path: path.join(root, 'small.txt') }] });
  assert.equal(calls.length, 1);
  client.spec.paths['/batch-upload'] = client.spec.paths['/upload'];
  const args = { operationId: 'POST /batch-upload', files: [{ field: 'file', path: path.join(root, 'small.txt') }] };
  const preview = await client.preview(args);
  await writeFile(path.join(root, 'small.txt'), 'new');
  await assert.rejects(client.execute({ ...args, confirmationId: preview.confirmationId }), /已变化/);
});
