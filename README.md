# Absensi Cafe

Aplikasi absensi terpusat untuk karyawan cafe, dijalankan dari **satu HP Android**
yang diletakkan di dekat kasir. Tanpa server, tanpa akun, dan setelah dipasang
tetap bisa dibuka meski internet cafe mati.

## Cara kerja

Karyawan mengetuk namanya di layar, memasukkan **PIN pribadi**, lalu kamera depan
menyala dan mengambil foto selfie. PIN membuktikan siapa yang absen, foto
menyimpan buktinya, dan geofence memastikan absen dilakukan di cafe.

## Fitur

- **Absen masuk & pulang** dengan PIN pribadi dan foto selfie sebagai bukti
- **Jadwal shift per tanggal** — roster mingguan, shift tiap karyawan bisa
  berbeda tiap hari; shift lintas tengah malam didukung
- **Hitung otomatis** keterlambatan, pulang cepat, durasi kerja, dan lembur
- **Denda keterlambatan bertingkat** yang tarif dan batas menitnya diatur sendiri
- **Geotagging & geofencing** — koordinat tersimpan di tiap absen, dengan
  pembatasan radius area cafe
- **Rekap harian & bulanan** per karyawan, termasuk hitungan alpa
- **Urutan nama A–Z / Z–A** yang berlaku serempak di semua daftar
- **Ekspor Excel (.xlsx)** tiga lembar untuk perhitungan gaji
- **Area admin terkunci PIN** — kelola karyawan, koreksi catatan absen yang
  salah, cadangkan dan pulihkan data
- **Bekerja luring** — terpasang sebagai aplikasi di layar utama dan tetap
  terbuka saat internet cafe mati

## Jadwal shift

Shift cafe berubah-ubah tiap hari, jadi jadwal disusun sebagai **roster**: satu
shift untuk satu karyawan pada satu tanggal. Buka **Admin → Jadwal** untuk grid
mingguan (Senin–Minggu), lalu:

- ketuk **satu sel** untuk mengubah sehari,
- ketuk **nama karyawan** untuk mengisi seminggu sekaligus,
- ketuk **kepala kolom hari** untuk mengatur semua karyawan pada hari itu,
- pakai **Salin Minggu Lalu** kalau polanya berulang.

Tiap sel punya tiga keadaan: shift tertentu, **Libur** (tidak dihitung alpa),
atau **belum dijadwalkan**. Karyawan tetap bisa absen di hari yang tidak
dijadwalkan — catatannya tersimpan dan ditandai *di luar jadwal*, tapi telat dan
lembur tidak dihitung karena tidak ada jam acuan.

Pola jam kerja diatur di **Kelola Shift**. Bawaannya Pagi 07:00–15:00, Sore
15:00–23:00, dan Malam 23:00–07:00; semuanya bisa diubah, ditambah, atau
dihapus. Jam selesai lebih awal dari jam mulai berarti shift lewat tengah malam.

## Keterlambatan dan denda

Telat dihitung otomatis saat karyawan absen masuk, memakai jam shift yang
dijadwalkan pada tanggal itu:

```
telat = jam absen masuk − (jam mulai shift + toleransi)     // minimum 0
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
— saat mati, kolomnya hilang dari rekap maupun Excel.

## Ekspor

Ekspor hanya menghasilkan **Excel (.xlsx)**, satu berkas berisi tiga lembar:

| Lembar | Isi |
| --- | --- |
| Rekap | Ringkasan per karyawan beserta baris total |
| Detail | Satu baris per catatan absen, lengkap dengan shift, denda, dan koordinat |
| Info | Periode, waktu ekspor, dan seluruh pengaturan yang berlaku saat itu |

Angka ditulis sebagai angka sungguhan dengan format sel — rupiah, bilangan bulat,
desimal, dan koordinat enam angka di belakang koma — sehingga langsung bisa
dijumlahkan atau dijadikan pivot tanpa dibersihkan dulu. Baris kepala dibekukan
dan diberi filter otomatis.

Berkasnya dirakit sendiri di dalam aplikasi: XML SpreadsheetML dikemas ke ZIP
dengan CRC-32 buatan sendiri dan kompresi lewat `CompressionStream("deflate-raw")`
bawaan browser, dengan cadangan tanpa kompresi bila API itu tidak tersedia. Tidak
ada library luar, sesuai sifat aplikasi yang satu berkas dan bekerja luring.

Terpisah dari ekspor, tombol **Cadangkan (ZIP)** di Setelan menyimpan seluruh data
sebagai satu berkas zip:

```
cadangan-absensi-2026-08-12.zip
├── data.json                        pengaturan, karyawan, shift, roster, absen
├── foto/2026-08-12_r1_masuk.jpg     satu berkas per foto, dinamai per tanggal
└── BACA-SAYA.txt
```

Zip itu bisa dibuka langsung di PC untuk menelusuri foto bukti tanpa perlu
aplikasi ini, atau dimuat kembali lewat tombol **Pulihkan**. Pemulihan juga masih
menerima cadangan JSON versi lama, dan catatan yang fotonya tidak ada di cadangan
otomatis berhenti mengaku punya foto.

## Urutan nama

Semua daftar karyawan diurutkan menurut nama: layar absen, grid jadwal, daftar
orang, tabel rekap, pilihan absen manual, dan ekspor Excel. Arahnya diubah lewat
tombol **Nama A–Z** di layar absen dan daftar orang, atau dengan mengetuk kolom
**Karyawan** pada tabel jadwal dan rekap. Satu pilihan berlaku serempak di semua
layar dan tersimpan di HP.

Pengurutan mengabaikan huruf besar-kecil dan tanda aksen, serta membaca angka
secara numerik — sehingga *Budi 2* berada sebelum *Budi 10*, bukan sesudahnya.

## Lokasi absen

Setiap absen menyimpan koordinat GPS beserta akurasinya dan jarak ke titik cafe.
Titik lokasi diatur di **Admin → Setelan → Lokasi absen**: berdiri di cafe lalu
tekan **Pakai Lokasi Saat Ini**, atau isi lintang dan bujur secara manual.

Tiga mode tersedia:

| Mode | Perilaku |
| --- | --- |
| Wajib di area | Absen ditolak bila di luar radius atau GPS mati, kecuali disetujui admin lewat PIN |
| Peringatan saja | Absen tetap tercatat, ditandai bila di luar radius |
| Nonaktif | Tanpa pembatasan; koordinat tetap disimpan bila titik cafe sudah diisi |

Saat kamera menyala, lokasi dicari berbarengan agar sudah dapat sinyal ketika
foto diambil, dan statusnya tampil sebagai bilah di bawah kamera.

**Menentukan radius.** GPS di dalam ruangan biasanya meleset 20–50 meter, kadang
lebih. Radius di bawah 50 meter cenderung menolak karyawan yang sebenarnya sudah
berada di cafe. Pakai **Uji Jarak** beberapa kali dari titik-titik berbeda di
dalam cafe, ambil jarak terbesar yang muncul, lalu tambahkan margin akurasi.

Koordinat, akurasi, dan jarak ikut terekspor ke Excel. Di rincian catatan absen,
tiap titik bisa dibuka di Google Maps. Semua data lokasi tersimpan di HP itu
saja dan tidak dikirim ke mana pun.

## PIN karyawan

Setiap karyawan punya PIN 4 angka yang diminta sebelum kamera menyala. PIN diatur
admin di **Orang**, tidak boleh sama antar karyawan, dan langsung ditolak kalau
bentrok.

- **PIN benar** → lanjut berfoto, catatan bersih.
- **Salah 3 kali** → ditawarkan izin admin; kalau dipakai, catatannya ditandai
  *izin admin*.
- **Dibatalkan** → tidak ada yang tercatat, kamera tidak menyala.
- **PIN belum diatur** → karyawan tetap bisa absen supaya shift pagi tidak macet,
  tapi catatannya ditandai *tanpa PIN* dan admin diberi peringatan berisi
  nama-namanya.

Penanda itu muncul di aktivitas harian, rincian karyawan, dan kolom **PIN Masuk**
serta **PIN Pulang** pada lembar Detail di Excel.

## Verifikasi wajah

> **Fitur ini nonaktif secara bawaan dan sebaiknya dibiarkan begitu.**
> Pengukuran menunjukkan wajah yang benar bisa ditolak hanya karena orangnya
> berdiri sedikit bergeser atau lebih tinggi daripada saat mendaftar — pada
> pengujian, kepala bergeser ke samping saja menjatuhkan kecocokan ke 0%.
> Menambah toleransi posisi justru menaikkan skor orang lain sampai melewati
> ambang, jadi tidak ada setelan yang memisahkan keduanya. Pengenalan wajah yang
> akurat memerlukan model terlatih, yang tidak tersedia di aplikasi satu berkas
> tanpa dependensi ini. Gunakan **PIN karyawan** di atas.

Pencocokan berjalan sepenuhnya di HP, tanpa mengirim foto ke mana pun. Metodenya
deskriptor HOG (6×6 sel × 8 arah) digabung peta intensitas 12×12, dibandingkan
dengan tiga foto yang didaftarkan per karyawan memakai kemiripan kosinus.
Deteksi wajah memakai `FaceDetector` bawaan Chrome Android bila tersedia, dengan
oval panduan sebagai cadangan.

**Kebal jarak berdiri.** Deskriptor HOG peka terhadap skala: wajah yang sama,
difoto dari jarak berbeda, menghasilkan pola gradien yang berbeda jauh. Karena
itu setiap absen dibandingkan pada tujuh perbesaran sekaligus, lalu diambil yang
paling cocok. Perbesaran pemenang juga dipakai menjelaskan hasilnya — kalau yang
menang adalah potongan yang diperbesar, berarti orangnya berdiri lebih jauh
daripada saat mendaftar, dan aplikasi menyarankan maju.

Pengukuran dengan wajah sintetis pada empat jarak berbeda: tanpa pembandingan
lintas skala, orang yang benar bisa jatuh ke 0–15%; dengan pembandingan, hasilnya
74–100%, sementara tiga wajah berbeda tetap di 44–54%. Geseran tegak sempat
dicoba tetapi tidak menambah akurasi sama sekali dan justru memperbesar peluang
orang lain ikut lolos, jadi tidak dipakai.

Cara pengukuran ini tidak sebanding dengan versi sebelumnya. Wajah yang
didaftarkan sebelum perubahan ditandai **daftar ulang** dan tidak dipakai
membandingkan, supaya tidak menghasilkan skor yang menyesatkan.

Ini **verifikasi**, bukan pengenalan wajah tingkat keamanan tinggi. Akurasinya
bergantung pada posisi HP dan pencahayaan yang konsisten. Tiga mode tersedia di
Pengaturan: wajib cocok, peringatan saja, atau nonaktif.

## Pemasangan

1. Buka halaman ini di Chrome pada HP cafe, sambil terhubung internet sekali
   agar aplikasinya tersimpan untuk dipakai luring.
2. Menu Chrome → **Tambahkan ke layar Utama**, lalu buka dari layar utama.
3. Izinkan akses **kamera** dan **lokasi** saat diminta, dan tekan **Minta Izin**
   di **Setelan → Luring & ketahanan data** agar data tidak boleh dibuang Chrome.
4. Buka tab **Admin** (PIN bawaan `1234`), lalu:
   - ganti PIN di **Setelan**,
   - tambahkan karyawan di **Orang**, dan beri tiap orang **PIN 4 angka**,
   - sesuaikan pola shift lewat **Jadwal → Kelola Shift**,
   - susun roster minggu ini di **Jadwal**,
   - atur titik cafe dan radius di **Setelan → Lokasi absen**.

Letakkan HP di dudukan permanen di dekat kasir.

## Luring & ketahanan data

Aplikasi memasang **service worker** ([`sw.js`](sw.js)) yang menyimpan berkasnya
di HP, sehingga tetap terbuka saat wifi cafe mati. Absen yang dicatat selama
luring tersimpan normal — tidak ada langkah yang butuh jaringan, termasuk ekspor
Excel yang seluruhnya dirakit di HP.

Strateginya *cache-first* dengan pembaruan latar belakang: halaman tampil
seketika dari simpanan, versi baru diambil diam-diam, lalu dipakai pada
pembukaan berikutnya. Pembaruan latar belakang memakai `cache: "no-cache"`
supaya tidak tertahan cache HTTP — GitHub Pages mengirim `max-age=600`, dan
tanpa itu versi baru bisa telat sampai sepuluh menit. Untuk memaksa segera,
tekan **Perbarui Aplikasi** di Setelan.

Aplikasi juga meminta **penyimpanan permanen** (`navigator.storage.persist()`)
setiap kali dibuka. Tanpa izin ini Chrome boleh membuang `localStorage` dan
`IndexedDB` saat memori HP sesak. Izinnya biasanya baru diberikan setelah
aplikasi dipasang ke layar utama, jadi pasang dulu lalu tekan **Minta Izin**.
Status keduanya terlihat di **Setelan → Luring & ketahanan data**.

Berkas [`manifest.webmanifest`](manifest.webmanifest) membuat "Tambahkan ke layar
Utama" menghasilkan aplikasi layar penuh, bukan sekadar pintasan. Ikonnya
dibangkitkan secara prosedural sebagai PNG, tanpa perkakas gambar.

## Data

Semua data tersimpan di HP itu saja — pengaturan dan catatan absen di
`localStorage`, foto di `IndexedDB`. Tidak ada server dan tidak ada akun.
Aplikasi tidak punya satu pun `fetch`, `XMLHttpRequest`, atau `WebSocket` ke
layanan luar; satu-satunya data yang bisa keluar adalah koordinat yang Anda
kirim sendiri saat mengetuk tautan peta.
Ekspor Excel setiap tutup buku dan gunakan **Cadangkan (ZIP)** secara berkala;
bila HP hilang atau di-reset, datanya ikut hilang.
