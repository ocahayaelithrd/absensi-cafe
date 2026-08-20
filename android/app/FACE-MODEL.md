# Model pengenalan wajah belum ada di repo

Aplikasi tetap bisa dibangun dan dipakai tanpa berkas ini. Yang terjadi kalau
berkasnya tidak ada: verifikasi wajah dilewati begitu saja, tombol **Daftarkan
wajah karyawan** di Setelan perangkat mati, dan absensi berjalan seperti biasa
dengan PIN dan foto bukti. Itu disengaja — ketiadaan model tidak boleh
menghentikan karyawan absen.

## Yang harus disiapkan

Satu berkas TensorFlow Lite berisi model *face embedding*, diletakkan di:

```
android/app/src/main/assets/face_embedder.tflite
```

Buat foldernya kalau belum ada.

## Model yang cocok

Yang dibutuhkan adalah model **face embedding** — masukan berupa potongan wajah,
keluaran berupa satu vektor. Bukan model deteksi wajah; deteksinya sudah
ditangani ML Kit.

| Syarat | Nilai |
| --- | --- |
| Format | TensorFlow Lite (`.tflite`) |
| Bentuk masukan | `[1, tinggi, lebar, 3]` — ukurannya dibaca dari model, tidak dipatok |
| Tipe masukan | float32 (piksel dipetakan ke −1..1) atau uint8 |
| Bentuk keluaran | `[1, dimensi]` — 128, 192, atau 512 semuanya bisa |

Yang lazim dipakai adalah **MobileFaceNet** (keluaran 192) atau **FaceNet**
(keluaran 128 atau 512). Keduanya tersedia dalam bentuk `.tflite` di berbagai
kumpulan model publik.

**Lisensi dan akurasinya harus Anda periksa sendiri.** Model wajah beredar
dengan asal-usul bobot yang bermacam-macam, dan sebagian tidak boleh dipakai
komersial. Ini bukan sesuatu yang bisa dipastikan dari kode.

## Setelah berkasnya dipasang

1. Bangun ulang APK dan pasang ke tablet.
2. Buka **Setelan perangkat** di tablet — baris *Model di aplikasi ini* harus
   berbunyi **terpasang**.
3. Daftarkan wajah tiap karyawan dari tablet itu, tiga jepretan per orang.
4. Di web admin, **Pengaturan → Pengenalan wajah**, mulai dari mode
   **peringatan saja**. Biarkan beberapa hari, lihat kolom *Wajah* di halaman
   Absensi, lalu setel ambangnya dan baru naikkan ke **wajib cocok**.

## Berganti model

Template wajah menyimpan pengenal model yang membuatnya — nama berkas, ukuran
berkas, dan panjang keluaran. Mengganti berkas model membuat template lama tidak
lagi dianggap sebanding, dan semua karyawan harus didaftarkan ulang. Itu
disengaja: embedding dari model berbeda tetap menghasilkan angka, tapi angkanya
tidak berarti apa-apa.
