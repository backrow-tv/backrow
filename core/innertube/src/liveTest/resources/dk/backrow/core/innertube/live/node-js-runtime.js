// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

// The node side of NodeJsRuntime (live tests only). Reads one JSON-encoded script per line on
// stdin, runs it as a classic script in this process's single global context (so globals persist
// between scripts, as JsRuntime requires), and answers one JSON line per script on stdout:
// {"ok":true,"value":"<String(result)>"} or {"ok":false,"error":"<message>"}.
// Uses only node built-ins.
'use strict';

const readline = require('node:readline');
const vm = require('node:vm');

// stdout carries the protocol; anything a script logs goes to stderr, which the caller discards.
for (const name of ['log', 'info', 'warn', 'error', 'debug', 'trace']) {
  console[name] = () => {};
}

const lines = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });

lines.on('line', (line) => {
  let reply;
  try {
    const value = vm.runInThisContext(JSON.parse(line));
    reply = { ok: true, value: String(value) };
  } catch (e) {
    // Only the message: a stack trace could quote parts of the script.
    reply = { ok: false, error: String(e && e.message ? e.message : e) };
  }
  process.stdout.write(JSON.stringify(reply) + '\n');
});

lines.on('close', () => process.exit(0));
