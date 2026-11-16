import { mkdir, open, realpath, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import { resolveRef, validateSchema } from './validation.js';

const methods = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

export class JinbooksClient {
  constructor(env = process.env) {
    this.base = new URL(env.JINBOOKS_BASE_URL || 'http://127.0.0.1:2154/jinbooks-api');
    if (!['http:', 'https:'].includes(this.base.protocol) || this.base.search || this.base.hash || this.base.username) {
      throw new Error('JINBOOKS_BASE_URL 必须是无查询参数和凭据的 HTTP(S) 地址');
    }
    this.base.pathname = this.base.pathname.replace(/\/$/, '');
    this.token = env.JINBOOKS_TOKEN || '';
    this.refreshToken = env.JINBOOKS_REFRESH_TOKEN || '';
    this.username = env.JINBOOKS_USERNAME;
    this.password = env.JINBOOKS_PASSWORD;
    this.downloadDir = path.resolve(env.JINBOOKS_DOWNLOAD_DIR || './downloads');
    this.cookies = new Map();
    this.secrets = new Set([this.token, this.refreshToken, this.password].filter(Boolean));
    this.uploadRoots = env.JINBOOKS_UPLOAD_ROOTS ? JSON.parse(env.JINBOOKS_UPLOAD_ROOTS) : [process.cwd()];
    if (!Array.isArray(this.uploadRoots) || !this.uploadRoots.length || this.uploadRoots.some(root => typeof root !== 'string' || !path.isAbsolute(root))) throw new Error('JINBOOKS_UPLOAD_ROOTS 必须是绝对目录的 JSON 数组');
    this.maxUploadBytes = Number(env.JINBOOKS_MAX_UPLOAD_BYTES || 20 * 1024 * 1024);
    if (!Number.isSafeInteger(this.maxUploadBytes) || this.maxUploadBytes < 1) throw new Error('无效上传大小上限');
    this.confirmations = new Map();
    this.sessionVersion = 0;
  }

  sanitize(value) {
    if (typeof value === 'string') {
      for (const secret of [...this.secrets].sort((a, b) => b.length - a.length)) value = value.replaceAll(secret, '[REDACTED]');
      return value;
    }
    if (Array.isArray(value)) return value.map(item => this.sanitize(item));
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key,
      /^(?:access[_-]?token|refresh[_-]?token|token|password|passwd|secret|secretKey|client[_-]?secret|authorization|privateKey)$/i.test(key) && (item === null || typeof item !== 'object') ? '[REDACTED]' : this.sanitize(item)]));
    return value;
  }

  async request(route, { method = 'GET', query = {}, body, contentType, auth = true, retry = true } = {}) {
    if (!route.startsWith('/') || route.startsWith('//') || /[?#\\]/.test(route)) throw new Error('无效接口路径');
    const url = new URL(this.base.href);
    url.pathname = this.base.pathname + route;
    if (!url.pathname.startsWith(this.base.pathname + '/')) throw new Error('接口超出配置路径');
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null) url.searchParams.set(key, Array.isArray(value) ? value.join(',') : String(value));
    }
    const headers = { 'Accept-Language': 'zh-CN' };
    if (auth && this.token) headers.Authorization = `Bearer ${this.token}`;
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ');
    if (contentType) headers['Content-Type'] = contentType;
    const response = await fetch(url, {
      method, headers, body, redirect: 'error', signal: AbortSignal.timeout(60000)
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0];
      const split = pair.indexOf('=');
      if (split > 0) this.cookies.set(pair.slice(0, split), pair.slice(split + 1));
    }
    const json = response.headers.get('content-type')?.includes('json');
    const data = json ? await response.json() : null;
    if ((response.status === 401 || data?.code === 401) && auth && retry && this.refreshToken) {
      this.refreshing ??= this.refresh().finally(() => { this.refreshing = null; });
      await this.refreshing;
      return this.request(route, { method, query, body, contentType, auth, retry: false });
    }
    if (!response.ok || (data && typeof data.code === 'number' && data.code !== 0)) {
      throw new Error(`接口失败 HTTP ${response.status}，业务码 ${data?.code ?? '无'}，${data?.message || response.statusText}`);
    }
    if (json) return data;
    const bytes = Buffer.from(await response.arrayBuffer());
    if (response.headers.has('content-disposition') || !response.headers.get('content-type')?.startsWith('text/')) {
      await mkdir(this.downloadDir, { recursive: true });
      const file = path.join(this.downloadDir, randomUUID() + '.bin');
      await writeFile(file, bytes, { flag: 'wx' });
      return { file, bytes: bytes.length, contentType: response.headers.get('content-type'), contentDisposition: response.headers.get('content-disposition') };
    }
    return { text: bytes.toString('utf8') };
  }

  setTokens(result) {
    if (!result?.data?.token) throw new Error('认证响应缺少 token');
    this.token = result.data.token;
    this.refreshToken = result.data.refresh_token || '';
    this.secrets.add(this.token);
    if (this.refreshToken) this.secrets.add(this.refreshToken);
  }

  async refresh() {
    const result = await this.request('/auth/token/refresh', {
      method: 'POST', query: { refresh_token: this.refreshToken }, auth: false
    });
    this.setTokens(result);
  }

  async login({ state, captcha = '' } = {}) {
    if (!this.username || !this.password) throw new Error('请在 MCP 环境配置 JINBOOKS_USERNAME 和 JINBOOKS_PASSWORD，或配置 JINBOOKS_TOKEN');
    if (!state) {
      const config = await this.request('/login/get', { auth: false, query: { _allow_anonymous: true } });
      const mode = String(config.data.captcha).toUpperCase();
      if (!['FALSE', 'OFF', 'NONE', 'NO', '0', ''].includes(mode)) {
        throw new Error('当前登录需要验证码。先调用 jinbooks_login_challenge，再携带返回的 state 和用户提供的验证码调用 jinbooks_login');
      }
      state = config.data.state;
    }
    const result = await this.request('/login/signin', {
      method: 'POST', auth: false, query: { _allow_anonymous: true }, contentType: 'application/json',
      body: JSON.stringify({ username: this.username, password: this.password, authType: 'normal', state, captcha })
    });
    this.setTokens(result);
    this.sessionVersion++;
    this.confirmations.clear();
    return { authenticated: true, username: result.data.username, displayName: result.data.displayName };
  }

  async document() {
    if (!this.spec) {
      this.spec = await this.request('/v3/api-docs', { auth: false });
      if (!this.spec.paths) { this.spec = null; throw new Error('后端未返回 OpenAPI paths'); }
    }
    return this.spec;
  }

  async operations() {
    const spec = await this.document();
    return Object.entries(spec.paths).flatMap(([route, item]) => Object.entries(item)
      .filter(([method]) => methods.has(method))
      .map(([method, operation]) => ({
        id: `${method.toUpperCase()} ${route}`, method: method.toUpperCase(), path: route,
        summary: operation.summary || '', description: operation.description || '', tags: operation.tags || [],
        operation, parameters: [...(item.parameters || []), ...(operation.parameters || [])]
      })));
  }

  async operation(id) {
    const result = (await this.operations()).find(item => item.id === id);
    if (!result) throw new Error('接口不存在，请先搜索接口并使用返回的 id');
    return result;
  }

  async describe(id) {
    const item = await this.operation(id);
    const spec = await this.document();
    const refs = {};
    function collect(value) {
      if (!value || typeof value !== 'object') return;
      if (value.$ref?.startsWith('#/') && !(value.$ref in refs)) {
        const key = value.$ref;
        refs[key] = key.slice(2).split('/').reduce((obj, part) => obj?.[part.replace(/~1/g, '/').replace(/~0/g, '~')], spec);
        collect(refs[key]);
      }
      Object.values(value).forEach(collect);
    }
    collect(item);
    return { ...item, referencedSchemas: refs };
  }

  async readUpload(file) {
    if (!path.isAbsolute(file.path)) throw new Error('上传文件必须使用绝对路径');
    let resolved;
    try { resolved = await realpath(file.path); } catch { throw new Error('上传文件不存在或无法访问'); }
    const roots = await Promise.all(this.uploadRoots.map(root => realpath(root)));
    if (!roots.some(root => {
      const relative = path.relative(root, resolved);
      return relative !== '..' && !relative.startsWith('..' + path.sep) && !path.isAbsolute(relative);
    })) throw new Error('上传文件超出 JINBOOKS_UPLOAD_ROOTS 允许目录');
    let handle;
    try {
      handle = await open(resolved, 'r');
      const stat = await handle.stat();
      if (!stat.isFile()) throw new Error('上传路径必须是普通文件');
      if (stat.size > this.maxUploadBytes) throw new Error('上传文件超过大小上限');
      const buffer = Buffer.alloc(this.maxUploadBytes + 1);
      let length = 0;
      while (length < buffer.length) {
        const { bytesRead } = await handle.read(buffer, length, buffer.length - length, null);
        if (!bytesRead) break;
        length += bytesRead;
      }
      if (length > this.maxUploadBytes) throw new Error('上传文件超过大小上限');
      return { field: file.field, path: resolved, bytes: buffer.subarray(0, length) };
    } catch (error) {
      if (error.code) throw new Error('上传文件读取失败，请检查文件权限');
      throw error;
    } finally { await handle?.close(); }
  }

  async prepare({ operationId, pathParams = {}, query = {}, body, files = [] }) {
    const item = await this.operation(operationId);
    if (/^\/(login|auth|logout)(\/|$)/.test(item.path)) throw new Error('认证请使用专用登录工具');
    if (!this.token && this.username && this.password) await this.login();
    if (!this.token) throw new Error('请先调用 jinbooks_login 或配置 JINBOOKS_TOKEN');
    let route = item.path.replace(/\{([^}]+)\}/g, (_, key) => {
      if (pathParams[key] === undefined) throw new Error(`缺少路径参数 ${key}`);
      const value = String(pathParams[key]);
      if (['.', '..'].includes(value) || /[/\\?#]/.test(value)) throw new Error(`无效路径参数 ${key}`);
      return encodeURIComponent(value);
    });
    if (route.includes('{')) throw new Error('路径参数未完整填写');
    const spec = await this.document();
    const parameters = new Map();
    for (const entry of item.parameters) {
      const param = resolveRef(entry, spec);
      parameters.set(`${param.in}:${param.name}`, param);
    }
    for (const param of parameters.values()) {
      const source = param.in === 'path' ? pathParams : param.in === 'query' ? query : {};
      if (param.required && source[param.name] === undefined) throw new Error(`缺少 ${param.in} 参数 ${param.name}`);
      if (source[param.name] !== undefined) validateSchema(param.schema, source[param.name], spec, `${param.in}.${param.name}`);
    }
    for (const [key, value] of Object.entries(query)) {
      if (!parameters.has(`query:${key}`)) throw new Error(`未定义的查询参数 ${key}`);
      if (value !== null && typeof value === 'object' && !Array.isArray(value)) throw new Error(`查询参数 ${key} 暂不支持对象序列化，请按接口定义使用标量或数组`);
    }
    const requestBody = resolveRef(item.operation.requestBody, spec);
    const content = requestBody?.content || {};
    if (requestBody?.required && body === undefined && files.length === 0) throw new Error('此接口要求请求体');
    if (body !== undefined && !requestBody) throw new Error('此接口未定义请求体');
    const uploads = await Promise.all(files.map(file => this.readUpload(file)));
    const mediaType = uploads.length || content['multipart/form-data'] ? 'multipart/form-data'
      : content['application/json'] ? 'application/json'
      : content['application/x-www-form-urlencoded'] ? 'application/x-www-form-urlencoded' : null;
    if ((body !== undefined || uploads.length) && !content[mediaType]) throw new Error('此接口的请求体类型暂不支持');
    let validationBody = body;
    if (mediaType === 'multipart/form-data') {
      if (body !== undefined && (!body || Array.isArray(body) || typeof body !== 'object')) throw new Error('上传表单 body 必须是对象');
      validationBody = { ...body };
      const schema = resolveRef(content[mediaType]?.schema, spec);
      for (const file of uploads) {
        if (schema?.properties && !schema.properties[file.field]) throw new Error(`未定义的文件字段 ${file.field}`);
        if (schema?.properties?.[file.field]?.type === 'array') {
          validationBody[file.field] ??= [];
          validationBody[file.field].push(file.path);
        } else {
          if (validationBody[file.field] !== undefined) throw new Error(`重复文件字段 ${file.field}`);
          validationBody[file.field] = file.path;
        }
      }
    }
    if (validationBody !== undefined) validateSchema(content[mediaType]?.schema, validationBody, spec, 'body');
    let payload;
    let contentType;
    if (uploads.length || content['multipart/form-data']) {
      if (!content['multipart/form-data']) throw new Error('此接口不支持文件上传');
      payload = new FormData();
      for (const [key, value] of Object.entries(body || {})) payload.append(key, typeof value === 'object' ? JSON.stringify(value) : String(value));
      for (const file of uploads) {
        payload.append(file.field, new Blob([file.bytes]), path.basename(file.path));
      }
    } else if (body !== undefined) {
      if (mediaType === 'application/x-www-form-urlencoded') {
        payload = new URLSearchParams(body).toString();
        contentType = 'application/x-www-form-urlencoded';
      } else {
        payload = JSON.stringify(body);
        contentType = 'application/json';
      }
    }
    const summary = { operationId, pathParams, query, body, files: uploads.map(file => ({ field: file.field, path: file.path, size: file.bytes.length, sha256: createHash('sha256').update(file.bytes).digest('hex') })) };
    const requiresConfirmation = item.method === 'DELETE' || /delete|remove|settle|settlement|closing|close-period|batch|\{ids\}|删除|结账|结转|批量/i.test(`${item.path} ${item.summary}`);
    return { route, item, query, payload, contentType, summary, requiresConfirmation };
  }

  fingerprint(prepared, context) {
    const canonical = value => {
      if (Array.isArray(value)) return value.map(canonical);
      if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
      return value;
    };
    return createHash('sha256').update(JSON.stringify(canonical({ summary: prepared.summary, context, session: this.sessionVersion }))).digest('hex');
  }

  async context() {
    const result = await this.request('/users/currentUser');
    if (!result?.data?.id || !result.data.bookId) throw new Error('无法确定当前用户与账套，暂不能确认危险操作');
    return { userId: result.data.id, bookId: result.data.bookId };
  }

  async preview(args) {
    const prepared = await this.prepare(args);
    const context = await this.context();
    const now = Date.now();
    for (const [key, entry] of this.confirmations) if (entry.expiresAt <= now) this.confirmations.delete(key);
    if (this.confirmations.size >= 100) throw new Error('待确认操作过多，请等待现有预览过期');
    const confirmationId = randomUUID();
    const expiresAt = now + 5 * 60 * 1000;
    this.confirmations.set(confirmationId, { fingerprint: this.fingerprint(prepared, context), expiresAt });
    return { ...prepared.summary, context, requiresConfirmation: prepared.requiresConfirmation, confirmationId, expiresAt: new Date(expiresAt).toISOString(), instruction: '向用户展示此操作的接口、账套和全部参数，获得用户确认后，使用相同参数和 confirmationId 执行。' };
  }

  async execute(args) {
    const prepared = await this.prepare(args);
    if (prepared.requiresConfirmation || args.confirmationId) {
      const entry = this.confirmations.get(args.confirmationId);
      if (!entry || entry.expiresAt <= Date.now()) throw new Error('该操作需要有效确认凭据。破坏性操作请先使用 jinbooks_preview_operation 获取预览并获得用户确认。');
      const context = await this.context();
      if (entry.expiresAt <= Date.now()) throw new Error('确认凭据已过期，请先使用 jinbooks_preview_operation 重新预览并获得用户确认。');
      if (entry.fingerprint !== this.fingerprint(prepared, context)) throw new Error('操作参数、文件或当前用户账套已变化，请重新预览并确认');
      // Consume before dispatch so simultaneous calls cannot reuse a confirmation.
      if (!this.confirmations.delete(args.confirmationId)) throw new Error('确认凭据已使用');
    }
    return this.request(prepared.route, { method: prepared.item.method, query: prepared.query, body: prepared.payload, contentType: prepared.contentType });
  }
}
