const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const { webcrypto } = require('node:crypto');

const authSource = fs.readFileSync(path.join(__dirname, '../trip-pocket/dist/line-auth.js'), 'utf8');
const appSource = fs.readFileSync(path.join(__dirname, '../trip-pocket/dist/app.js'), 'utf8');
const origin = 'https://example.com';
const userId = 'U' + 'a'.repeat(32);
function harness(options = {}) {
  const calls = { logins: [], logouts: 0, scripts: 0 };
  let signedIn = options.signedIn ?? false;
  let failProfile = options.failProfile;
  const sdk = {
    init: async settings => { calls.settings = settings; },
    isLoggedIn: () => signedIn,
    getProfile: async () => {
      if (failProfile) throw new Error('Network error');
      return { userId: options.userId || userId, displayName: '測試帳號' };
    },
    login: settings => calls.logins.push(settings),
    logout: () => { calls.logouts++; signedIn = false; }
  };
  const window = {
    POCKET_LINE_CONFIG: { liffId: options.liffId ?? '123456-test', endpointUrl: options.endpoint || origin + '/pocket/' },
    location: { origin }, crypto: webcrypto, liff: sdk,
    dispatchEvent: event => { calls.lastEvent = event; }
  };
  const context = vm.createContext({ window, URL, TextEncoder, Uint8Array, setTimeout, clearTimeout,
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
    document: { createElement: () => { calls.scripts++; return {}; }, head: { appendChild: () => {} } }
  });
  vm.runInContext(authSource, context);
  return { auth: window.PocketLine, calls, setProfileFailure: value => { failProfile = value; } };
}

test('unconfigured sites never load LINE or pretend to log in', async () => {
  const { auth, calls } = harness({ liffId: '' });
  await auth.login();
  assert.equal(auth.getState().status, 'unconfigured');
  assert.equal(auth.getState().user, null);
  assert.equal(calls.scripts, 0);
  assert.equal(calls.logins.length, 0);
});

test('external browser startup does not force a redirect', async () => {
  const { auth, calls } = harness();
  await auth.init();
  assert.equal(auth.getState().status, 'guest');
  assert.equal(calls.settings.withLoginOnExternalBrowser, false);
  assert.equal(calls.logins.length, 0);
});

test('login always returns to the configured path without query or fragment', async () => {
  const { auth, calls } = harness({ endpoint: origin + '/pocket/?return=https://other.test/#secret' });
  await auth.login();
  assert.equal(calls.logins[0].redirectUri, origin + '/pocket/');
  assert.equal(auth.getState().status, 'redirecting');
});

test('login rejects an endpoint on a different origin', async () => {
  const { auth, calls } = harness({ endpoint: 'https://other.test/' });
  await auth.login();
  assert.equal(auth.getState().status, 'error');
  assert.equal(calls.logins.length, 0);
});

test('only a successful live LINE profile produces an authenticated identity', async () => {
  const { auth } = harness({ signedIn: true });
  await auth.init();
  const state = auth.getState();
  assert.equal(state.status, 'authenticated');
  assert.equal(state.user.displayName, '測試帳號');
  assert.match(state.user.storageId, /^[a-f0-9]{64}$/);
  assert.equal(JSON.stringify(state).includes(userId), false);
});

test('failed profile requests expose no identity and can be retried', async () => {
  const { auth, setProfileFailure } = harness({ signedIn: true, failProfile: true });
  await auth.init();
  assert.equal(auth.getState().status, 'error');
  assert.equal(auth.getState().user, null);
  setProfileFailure(false);
  await auth.init();
  assert.equal(auth.getState().status, 'authenticated');
});

test('different accounts receive different local collection keys', async () => {
  const a = harness({ signedIn: true });
  const b = harness({ signedIn: true, userId: 'U' + 'b'.repeat(32) });
  await Promise.all([a.auth.init(), b.auth.init()]);
  assert.notEqual(a.auth.getState().user.storageId, b.auth.getState().user.storageId);
});

test('logout clears the in-memory identity and ends the LIFF login', async () => {
  const { auth, calls } = harness({ signedIn: true });
  await auth.init();
  auth.logout();
  assert.equal(auth.getState().status, 'guest');
  assert.equal(auth.getState().user, null);
  assert.equal(calls.logouts, 1);
});

test('guest, LINE account A and account B collections survive switching without mixing', () => {
  const storage = new Map();
  const elements = new Map();
  const context = vm.createContext({
    window: { addEventListener() {}, PocketLine: { getState: () => ({ status: 'guest' }), init() {} } },
    document: { addEventListener() {}, querySelector: () => null,
      getElementById(id) { if (!elements.has(id)) elements.set(id, { addEventListener() {} }); return elements.get(id); } },
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value) },
    structuredClone, setTimeout, clearTimeout
  });
  vm.runInContext(appSource.replace(/render\(\);\s*window\.PocketLine\.init\(\);\s*$/, ''), context);
  vm.runInContext('render=()=>{};closeOverlay=()=>{};profileModal=()=>{};data=[{id:100,name:"guest"}];persist();', context);
  const switchTo = id => vm.runInContext(`switchLineAccount({status:'authenticated',user:{storageId:'${id}',displayName:'帳號'}})`, context);
  switchTo('a');
  assert.equal(vm.runInContext('data.length', context), 0);
  vm.runInContext('data.push({id:101,name:"A"});persist();', context);
  switchTo('b');
  assert.equal(vm.runInContext('data.length', context), 0);
  vm.runInContext('data.push({id:102,name:"B"});persist();', context);
  switchTo('a');
  assert.equal(vm.runInContext('data[0].name', context), 'A');
  vm.runInContext("switchLineAccount({status:'guest'})", context);
  assert.equal(vm.runInContext('data[0].name', context), 'guest');
  switchTo('b');
  assert.equal(vm.runInContext('data[0].name', context), 'B');
});
