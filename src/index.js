#!/usr/bin/env node
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createChaoyeFinanceServer } from './server.js';

await createChaoyeFinanceServer().connect(new StdioServerTransport());
