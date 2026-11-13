// 씨앤씨 영업 현황 - 오프라인용 기본 파일 저장 (데이터는 Apps Script에서 실시간으로 불러옴)
var CACHE = 'cnc-sales-v5';
var SHELL = ['./', 'index.html', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/maskable-512.png', 'icons/apple-touch-icon.png', 'icons/mark.png', 'icons/favicon.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () { return self.skipWaiting(); }));
});
self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});
// 같은 사이트 파일만: 인터넷 먼저, 안 되면 저장본
self.addEventListener('fetch', function (e) {
  var url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  e.respondWith(fetch(e.request).then(function (res) {
    var copy = res.clone();
    caches.open(CACHE).then(function (c) { c.put(e.request, copy); });
    return res;
  }).catch(function () {
    return caches.match(e.request).then(function (r) { return r || caches.match('index.html'); });
  }));
});

// ───── 휴대폰 알림(푸시) ─────
// 서버는 "새 알림 있음" 신호만 보내고, 여기서 앱 서버에 최신 알림 내용을 물어봐서 띄움
var APP_URL = 'https://script.google.com/macros/s/AKfycbw6Qsi7ZVZktH4xutPdGWHkYaM1u2nw6HGJDN3u8mBQVk0sbqBTpPliLXGwn20O_ix8/exec';
function idbOpen() { return new Promise(function (ok, no) { var r = indexedDB.open('cnc', 1); r.onupgradeneeded = function () { r.result.createObjectStore('kv'); }; r.onsuccess = function () { ok(r.result); }; r.onerror = no; }); }
function idbGet(k) { return idbOpen().then(function (db) { return new Promise(function (ok) { var q = db.transaction('kv').objectStore('kv').get(k); q.onsuccess = function () { ok(q.result); }; q.onerror = function () { ok(null); }; }); }).catch(function () { return null; }); }
function idbSet(k, v) { return idbOpen().then(function (db) { return new Promise(function (ok) { var t = db.transaction('kv', 'readwrite'); t.objectStore('kv').put(v, k); t.oncomplete = ok; t.onerror = ok; }); }).catch(function () {}); }

self.addEventListener('push', function (e) {
  e.waitUntil((async function () {
    var title = '씨앤씨', body = '새 알림이 있어요', tag = 'cnc';
    try {
      var token = await idbGet('token'), since = (await idbGet('since')) || 0;
      if (token) {
        var r = await fetch(APP_URL + '?api=noti&token=' + encodeURIComponent(token) + '&since=' + since);
        var j = await r.json();
        if (j && j.last) await idbSet('since', j.last);   // 알림 설정에서 끈 종류도 건너뛰게 (2026-11-13)
        if (j && j.items && j.items.length) {
          var x = j.items[0];
          title = (x.kind || '알림') + (x.co ? ' · ' + x.co : '');
          body = (x.text || '') + (j.items.length > 1 ? '  (외 ' + (j.items.length - 1) + '건)' : '');
          tag = 'noti-' + x.id;
        } else if (j && j.test) { title = '씨앤씨 알림 테스트'; body = '휴대폰 알림이 잘 와요 ✓'; tag = 'test'; }
      }
    } catch (err) {}
    return self.registration.showNotification(title, { body: body, tag: tag, renotify: true, silent: false, vibrate: [300, 150, 300, 150, 500], timestamp: Date.now(), icon: 'icons/icon-192.png', badge: 'icons/badge.png', data: { url: './' } });
  })());
});
self.addEventListener('notificationclick', function (e) {
  e.notification.close();
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (cs) {
    for (var i = 0; i < cs.length; i++) if ('focus' in cs[i]) return cs[i].focus();
    return clients.openWindow('./');
  }));
});
