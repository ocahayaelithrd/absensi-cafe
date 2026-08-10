# Absensi Cafe

Aplikasi absensi terpusat untuk karyawan cafe, dijalankan dari **satu HP Android**
yang diletakkan di dekat kasir. Seluruhnya satu berkas HTML, tanpa server dan
tanpa internet setelah halaman terbuka.

## Cara kerja

Karyawan mengetuk namanya di layar, kamera depan menyala, wajahnya dideteksi dan
difoto, lalu sistem membandingkan foto itu dengan wajah yang sudah didaftarkan.
Foto selfie selalu disimpan sebagai bukti.

## Fitur

- **Absen masuk & pulang** dengan selfie dan verifikasi wajah
- **Jadwal fleksibel per karyawan** — jam masuk, jam pulang, hari kerja, dan
  toleransi telat sendiri-sendiri; shift lintas tengah malam didukung
- **Hitung otomatis** keterlambatan, pulang cepat, durasi kerja, dan lembur
- **Denda keterlambatan bertingkat** yang tarif dan batas menitnya diatur sendiri
- **Rekap harian & bulanan** per karyawan, termasuk hitungan alpa
- **Ekspor CSV** detail dan rekap untuk perhitungan gaji
- **Area admin terkunci PIN** — kelola karyawan, koreksi catatan absen yang
  salah, cadangkan dan pulihkan data

## Keterlambatan dan denda

Telat dihitung otomatis saat karyawan absen masuk:

```
telat = jam absen masuk − (jam masuk jadwal + toleransi)     // minimum 0
```

Toleransi diambil dari pengaturan karyawan bila diisi, kalau kosong ikut
pengaturan umum. Dari menit telat itu, denda ditentukan lewat tingkatan yang
bisa diatur di **Admin → Pengaturan → Denda keterlambatan**. Bawaannya:

| Telat | Denda |
| --- | --- |
| 1–15 menit | Rp 5.000 |
| 16–30 menit | Rp 15.000 |
| Lebih dari 30 menit | Rp 30.000 |

Tingkatan bisa ditambah, dihapus, dan diubah tarifnya; urutannya dirapikan
otomatis. Telat 0 menit tidak pernah kena denda. Denda dihitung saat ditampilkan,
bukan disimpan di catatan absen, sehingga perubahan tarif langsung berlaku
konsisten di seluruh rekap. Seluruh fitur denda bisa dimatikan lewat satu sakelar
— saat mati, kolomnya hilang dari rekap maupun CSV.

## Verifikasi wajah

Pencocokan berjalan sepenuhnya di HP, tanpa mengirim foto ke mana pun. Metodenya
deskriptor HOG (6×6 sel × 8 arah) digabung peta intensitas 12×12, dibandingkan
dengan tiga foto yang didaftarkan per karyawan memakai kemiripan kosinus.
Deteksi wajah memakai `FaceDetector` bawaan Chrome Android bila tersedia, dengan
oval panduan sebagai cadangan.

Ini **verifikasi**, bukan pengenalan wajah tingkat keamanan tinggi. Akurasinya
bergantung pada posisi HP dan pencahayaan yang konsisten. Tiga mode tersedia di
Pengaturan: wajib cocok, peringatan saja, atau nonaktif.

## Pemasangan

1. Buka halaman ini di Chrome pada HP cafe.
2. Menu Chrome → **Tambahkan ke layar Utama**, agar terbuka layar penuh.
3. Izinkan akses kamera saat diminta.
4. Buka tab **Admin** (PIN bawaan `1234`), lalu:
   - ganti PIN di **Pengaturan**,
   - tambahkan karyawan beserta jadwalnya di **Karyawan**,
   - daftarkan wajah tiap karyawan (3 foto),
   - pakai **Uji Wajah** untuk menyetel ambang kecocokan sesuai pencahayaan cafe.

Letakkan HP di dudukan permanen. Bila HP dipindah atau lampu diganti, daftarkan
ulang wajah karyawan.

## Data

Semua data tersimpan di HP itu saja — pengaturan dan catatan absen di
`localStorage`, foto di `IndexedDB`. Tidak ada server dan tidak ada akun.
Ekspor CSV setiap tutup buku dan gunakan **Cadangkan (JSON)** secara berkala;
bila HP hilang atau di-reset, datanya ikut hilang.
