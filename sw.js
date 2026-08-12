/* Service worker Absensi Cafe.

   Tujuannya satu: aplikasi harus tetap bisa dibuka saat wifi cafe mati.
   Strateginya cache-first dengan pembaruan di latar belakang — halaman
   selalu tampil seketika dari cache, lalu versi baru diambil diam-diam
   dan dipakai pada pembukaan berikutnya.

   Naikkan VERSI hanya bila strategi caching di berkas ini berubah.
   Untuk perubahan index.html tidak perlu diapa-apakan: pembaruan latar
   belakang sudah menyegarkan isinya sendiri. */

const VERSI = "absensi-cafe-v3";
const ASET = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png",
              "./firebase-config.js",
              "./vendor/firebase-app-compat.js", "./vendor/firebase-auth-compat.js",
              "./vendor/firebase-firestore-compat.js"];

/* Semua entri disimpan dengan kunci pathname saja, supaya tambahan
   query string (mis. ?cb= atau parameter dari pintasan layar utama)
   tidak membuat salinan ganda di cache. */
const kunci = (url) => new URL(url, self.location).pathname;

self.addEventListener("install", (e) => {
  e.waitUntil((async () => {
    const cache = await caches.open(VERSI);
    for(const u of ASET){
      try{
        const res = await fetch(u, { cache:"reload" });
        if(res && res.ok) await cache.put(kunci(u), res.clone());
      }catch(err){ /* satu aset gagal tidak boleh membatalkan pemasangan */ }
    }
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (e) => {
  e.waitUntil((async () => {
    const nama = await caches.keys();
    await Promise.all(nama.filter(n => n !== VERSI).map(n => caches.delete(n)));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if(req.method !== "GET") return;
  // biarkan lintas-origin lewat jaringan biasa, mis. tautan ke Google Maps
  if(new URL(req.url).origin !== self.location.origin) return;

  e.respondWith((async () => {
    const cache = await caches.open(VERSI);
    const k = kunci(req.url);
    const tersimpan = await cache.match(k);

    if(tersimpan){
      /* Perbarui di latar belakang dengan permintaan baru bermode no-cache.
         Memakai req apa adanya akan kena cache HTTP browser — GitHub Pages
         mengirim max-age=600 — sehingga bita lama hanya ditulis ulang dan
         versi baru tertahan sampai sepuluh menit. */
      e.waitUntil((async () => {
        try{
          const res = await fetch(new Request(k, { cache:"no-cache", credentials:"same-origin" }));
          if(res && res.ok) await cache.put(k, res.clone());
        }catch(err){ /* luring: biarkan yang tersimpan */ }
      })());
      return tersimpan;
    }
    try{
      const res = await fetch(req);
      if(res && res.ok) await cache.put(k, res.clone());
      return res;
    }catch(err){
      // luring dan belum pernah tersimpan: untuk navigasi, jatuhkan ke halaman utama
      if(req.mode === "navigate"){
        const utama = await cache.match(kunci("./index.html")) || await cache.match(kunci("./"));
        if(utama) return utama;
      }
      throw err;
    }
  })());
});

/* Dipakai tombol "Perbarui Aplikasi" di Setelan untuk memaksa ambil ulang. */
self.addEventListener("message", (e) => {
  if(e.data !== "segarkan") return;
  e.waitUntil((async () => {
    const cache = await caches.open(VERSI);
    for(const u of ASET){
      try{
        const res = await fetch(u, { cache:"reload" });
        if(res && res.ok) await cache.put(kunci(u), res.clone());
      }catch(err){}
    }
    for(const c of await self.clients.matchAll()) c.postMessage("tersegarkan");
  })());
});
