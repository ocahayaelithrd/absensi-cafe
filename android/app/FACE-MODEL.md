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

Yang lazim dipakai adalah **MobileFaceNet** (masukan 112×112, keluaran 192) atau
**FaceNet** (masukan 160×160, keluaran 128).

## Penyiapan piksel harus cocok

Ini bagian yang paling mudah terlewat: cara piksel disiapkan sebelum masuk ke
model **harus sama dengan cara modelnya dilatih**. Salah pilih tidak memunculkan
galat apa pun — skor kemiripannya sekadar turun, dan penyebabnya hampir tidak
mungkin ditebak dari hasilnya.

| Model | Penyiapan | Nilai di `FaceEmbedder.kt` |
| --- | --- | --- |
| MobileFaceNet, keluarga InsightFace | piksel dipetakan ke −1..1 | `Normalization.SIGNED` (bawaan) |
| FaceNet (David Sandberg, keras-facenet) | standardisasi per foto | `Normalization.STANDARDIZED` |

Bawaannya `SIGNED`. Kalau memakai FaceNet, ubah baris `private val normalization`
di [`FaceEmbedder.kt`](src/main/java/id/omi/absensicafe/face/FaceEmbedder.kt).

## Dari mana mengunduhnya

Beberapa repositori Android publik menyertakan berkas `.tflite` siap pakai di
folder `assets` masing-masing:

- [shubham0204/FaceRecognition_With_FaceNet_Android](https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android)
  — FaceNet, masukan 160×160, keluaran 128. Repositorinya berlisensi Apache-2.0.
  Pakai `Normalization.STANDARDIZED`.
- [MCarlomagno/FaceRecognitionAuth](https://github.com/MCarlomagno/FaceRecognitionAuth/blob/master/assets/mobilefacenet.tflite)
  — `mobilefacenet.tflite`, masukan 112×112, keluaran 192. Repositorinya
  berlisensi BSD-3-Clause. Cocok dengan bawaan `SIGNED`.
- [sirius-ai/MobileFaceNet_TF](https://github.com/sirius-ai/MobileFaceNet_TF)
  — sumber MobileFaceNet aslinya, kalau ingin mengubah sendiri ke TFLite.

Unduh berkasnya lewat tombol **Download raw file** di GitHub, lalu **ganti
namanya** menjadi `face_embedder.tflite`.

**Lisensi bobotnya perlu Anda periksa sendiri, dan itu bukan hal yang sama
dengan lisensi repositorinya.** Lisensi Apache-2.0 atau BSD di sebuah repo
mencakup kodenya; bobot model wajah umumnya dilatih atas kumpulan data seperti
MS-Celeb-1M, VGGFace2, atau CASIA-WebFace, yang sebagian bersyarat "hanya untuk
penelitian". Untuk aplikasi yang dipakai menghitung gaji karyawan, itu perlu
dipastikan lebih dulu — dan itu bukan sesuatu yang bisa dijamin dari kode.

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
