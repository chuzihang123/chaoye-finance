#!/usr/bin/env node
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { createJinbooksServer } from './server.js';

await createJinbooksServer().connect(new StdioServerTransport());
