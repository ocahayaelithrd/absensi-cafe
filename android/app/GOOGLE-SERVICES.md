# Berkas `google-services.json` belum ada di repo

Build akan berhenti dengan pesan *File google-services.json is missing* sampai
berkas itu diletakkan di folder ini (`android/app/google-services.json`).

Berkasnya tidak bisa dibuat sendiri: isinya memuat pengenal aplikasi Android
yang baru terbit setelah aplikasi didaftarkan di proyek Firebase.

## Cara mengambilnya

1. Buka [console.firebase.google.com](https://console.firebase.google.com) dan
   pilih proyek **absensi-cafe-8da42** — proyek yang sama dengan aplikasi lama,
   supaya data yang sudah ada tidak perlu dipindah.
2. **Project settings → Your apps → Add app → Android**.
3. Isi *Android package name* dengan persis:

   ```
   id.omi.absensicafe
   ```

4. Unduh `google-services.json` yang muncul, simpan ke `android/app/`.

Varian debug memakai applicationId `id.omi.absensicafe.debug`. Kalau ingin
memasang versi debug dan rilis berdampingan di satu tablet, daftarkan juga
paket `.debug` sebagai aplikasi kedua di proyek Firebase yang sama; kalau tidak,
cukup pasang salah satu.

Berkas ini bukan rahasia — isinya pengenal proyek, bukan kunci akses. Yang
mengamankan data tetap aturan Firestore di [`firebase/`](../../firebase) dan
pendaftaran mandiri yang dimatikan di console.
