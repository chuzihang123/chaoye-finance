#!/usr/bin/env node
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { isInitializeRequest } from '@modelcontextprotocol/sdk/types.js';
import { createJinbooksServer } from './server.js';

export function createHttpServer(env = process.env) {
  const token = env.JINBOOKS_MCP_TOKEN;
  if (!token || token.length < 32) throw new Error('JINBOOKS_MCP_TOKEN 至少需要 32 个字符');
  const expectedHash = createHash('sha256').update(`Bearer ${token}`).digest();
  const allowedOrigins = new Set(JSON.parse(env.JINBOOKS_MCP_ORIGINS || '[]'));
  const sessions = new Map();
  const reply = (res, status, message) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: message }));
  };
  const httpServer = createServer(async (req, res) => {
    try {
      if (req.url === '/healthz' && req.method === 'GET') return reply(res, 200, 'ok');
      if (req.url !== '/mcp') return reply(res, 404, 'Not found');
      const suppliedHash = createHash('sha256').update(req.headers.authorization || '').digest();
      if (!timingSafeEqual(expectedHash, suppliedHash)) {
        res.setHeader('WWW-Authenticate', 'Bearer');
        return reply(res, 401, 'Authentication required');
      }
      if (req.headers.origin && !allowedOrigins.has(req.headers.origin)) return reply(res, 403, 'Origin not allowed');
      if (!['GET', 'POST', 'DELETE'].includes(req.method)) return reply(res, 405, 'Method not allowed');
      let body;
      if (req.method === 'POST') {
        if (!req.headers['content-type']?.includes('application/json')) return reply(res, 415, 'JSON required');
        const chunks = [];
        let size = 0;
        for await (const chunk of req) {
          size += chunk.length;
          if (size > 1024 * 1024) return reply(res, 413, 'Request too large');
          chunks.push(chunk);
        }
        try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); }
        catch { return reply(res, 400, 'Invalid JSON'); }
      }
      const sessionId = req.headers['mcp-session-id'];
      let session = sessions.get(sessionId);
      if (sessionId && !session) return reply(res, 404, 'Session expired; initialize again');
      if (!session) {
        if (req.method !== 'POST' || !isInitializeRequest(body)) return reply(res, 400, 'Initialization required');
        if (sessions.size >= 32) return reply(res, 503, 'Session limit reached');
        const server = createJinbooksServer(env);
        const transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => randomUUID(),
          onsessioninitialized: id => sessions.set(id, session),
          enableJsonResponse: true
        });
        session = { server, transport, lastSeen: Date.now() };
        transport.onclose = () => sessions.delete(transport.sessionId);
        await server.connect(transport);
      }
      session.lastSeen = Date.now();
      await session.transport.handleRequest(req, res, body);
    } catch {
      if (!res.headersSent) reply(res, 500, 'MCP request failed');
      else res.end();
    }
  });
  const cleanup = setInterval(() => {
    for (const [id, session] of sessions) {
      if (Date.now() - session.lastSeen > 60 * 60 * 1000) {
        sessions.delete(id);
        void session.server.close();
      }
    }
  }, 60000);
  cleanup.unref();
  return {
    server: httpServer,
    async close() {
      clearInterval(cleanup);
      await Promise.all([...sessions.values()].map(session => session.server.close()));
      sessions.clear();
      httpServer.closeAllConnections();
      if (httpServer.listening) await new Promise(resolve => httpServer.close(resolve));
    }
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const app = createHttpServer();
  const port = Number(process.env.JINBOOKS_MCP_PORT || 3155);
  app.server.listen(port, process.env.JINBOOKS_MCP_HOST || '127.0.0.1', () => console.error(`JinBooks MCP HTTP listening on port ${port}`));
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => { void app.close(); });
}
