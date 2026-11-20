(() => {
  'use strict';

  const config = window.POCKET_LINE_CONFIG || {};
  const configured = /^\d+-[A-Za-z0-9_-]+$/.test(config.liffId || '');
  let state = Object.freeze({ status: configured ? 'loading' : 'unconfigured', user: null, message: '' });
  let initializing = null;
  let sdkLoading = null;

  function update(status, user = null, message = '') {
    state = Object.freeze({ status, user, message });
    window.dispatchEvent(new CustomEvent('pocket-line-change', { detail: state }));
    return state;
  }

  function messageFor(error) {
    if (error?.code === 'ACCESS_DENIED' || error?.code === 'FORBIDDEN') {
      return '尚未取得 LINE 個人資料授權，請重新登入並同意基本資料授權。';
    }
    if (error?.code === 'INVALID_CONFIG' || error?.code === 'INVALID_ARGUMENT') {
      return 'LINE 登入設定尚未完成，請稍後再試。';
    }
    return '暫時無法連線 LINE，請確認網路後重試。你的本機收藏仍會保留。';
  }

  function loadSdk() {
    if (window.liff) return Promise.resolve(window.liff);
    if (sdkLoading) return sdkLoading;
    sdkLoading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://static.line-scdn.net/liff/edge/2/sdk.js';
      script.charset = 'utf-8';
      script.async = true;
      const timer = setTimeout(() => finish(new Error('LINE SDK timeout')), 15000);
      function finish(error) {
        clearTimeout(timer);
        script.onload = script.onerror = null;
        if (error || !window.liff) {
          script.remove();
          reject(error || new Error('LINE SDK unavailable'));
        } else resolve(window.liff);
      }
      script.onload = () => finish();
      script.onerror = () => finish(new Error('LINE SDK unavailable'));
      document.head.appendChild(script);
    }).catch(error => {
      sdkLoading = null;
      throw error;
    });
    return sdkLoading;
  }

  function endpoint() {
    const url = new URL(config.endpointUrl);
    if (url.protocol !== 'https:' || url.origin !== window.location.origin) {
      throw Object.assign(new Error('Invalid endpoint'), { code: 'INVALID_CONFIG' });
    }
    // Callback destinations are configured by the owner, never supplied in query parameters.
    url.search = '';
    url.hash = '';
    return url.href;
  }

  async function getUser(sdk) {
    // Use LINE's live profile response; browser storage is never an identity source.
    const profile = await sdk.getProfile();
    if (!profile || !/^U[0-9a-f]{32}$/i.test(profile.userId || '') || !profile.displayName) {
      throw new Error('Invalid LINE profile');
    }
    const bytes = new TextEncoder().encode(`${config.liffId}:${profile.userId}`);
    const digest = await window.crypto.subtle.digest('SHA-256', bytes);
    const storageId = Array.from(new Uint8Array(digest), x => x.toString(16).padStart(2, '0')).join('');
    return Object.freeze({ displayName: String(profile.displayName), storageId });
  }

  async function init() {
    if (!configured) return update('unconfigured');
    if (initializing) return initializing;
    update('loading');
    initializing = Promise.resolve().then(async () => {
      try {
        endpoint();
        const sdk = await loadSdk();
        // Let LIFF consume its callback parameters before changing the page URL.
        await sdk.init({ liffId: config.liffId, withLoginOnExternalBrowser: false });
        return sdk.isLoggedIn() ? update('authenticated', await getUser(sdk)) : update('guest');
      } catch (error) {
        return update('error', null, messageFor(error));
      } finally {
        initializing = null;
      }
    });
    return initializing;
  }

  async function login() {
    if (!configured) return update('unconfigured', null, 'LINE 登入服務尚未開通。');
    if (state.status === 'redirecting') return state;
    const result = await init();
    if (result.status === 'error' || result.status === 'authenticated') return result;
    try {
      update('redirecting');
      window.liff.login({ redirectUri: endpoint() });
      return state;
    } catch (error) {
      return update('error', null, messageFor(error));
    }
  }

  function logout() {
    try {
      if (window.liff?.isLoggedIn()) window.liff.logout();
      return update('guest');
    } catch (error) {
      return update('error', null, messageFor(error));
    }
  }

  window.PocketLine = Object.freeze({ getState: () => state, init, login, logout });
})();
