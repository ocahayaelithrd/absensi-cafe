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
- **Jadwal shift per tanggal** — roster mingguan, shift tiap karyawan bisa
  berbeda tiap hari; shift lintas tengah malam didukung
- **Hitung otomatis** keterlambatan, pulang cepat, durasi kerja, dan lembur
- **Denda keterlambatan bertingkat** yang tarif dan batas menitnya diatur sendiri
- **Geotagging & geofencing** — koordinat tersimpan di tiap absen, dengan
  pembatasan radius area cafe
- **Rekap harian & bulanan** per karyawan, termasuk hitungan alpa
- **Urutan nama A–Z / Z–A** yang berlaku serempak di semua daftar
- **Ekspor Excel (.xlsx)** tiga lembar, plus CSV detail dan rekap
- **Area admin terkunci PIN** — kelola karyawan, koreksi catatan absen yang
  salah, cadangkan dan pulihkan data

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
— saat mati, kolomnya hilang dari rekap maupun CSV.

## Ekspor

**Excel (.xlsx)** — satu berkas berisi tiga lembar:

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

**CSV** tetap tersedia untuk detail dan rekap, memakai pemisah titik koma dan
desimal koma agar cocok dengan Excel berlokal Indonesia. Bila unduhan diblokir
browser, tersedia pilihan menyalin isinya sebagai teks.

## Urutan nama

Semua daftar karyawan diurutkan menurut nama: layar absen, grid jadwal, daftar
orang, tabel rekap, pilihan absen manual, dan ekspor CSV. Arahnya diubah lewat
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

Koordinat, akurasi, dan jarak ikut terekspor ke CSV. Di rincian catatan absen,
tiap titik bisa dibuka di Google Maps. Semua data lokasi tersimpan di HP itu
saja dan tidak dikirim ke mana pun.

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
3. Izinkan akses **kamera** dan **lokasi** saat diminta.
4. Buka tab **Admin** (PIN bawaan `1234`), lalu:
   - ganti PIN di **Setelan**,
   - tambahkan karyawan di **Orang**, dan daftarkan wajah tiap orang (3 foto),
   - sesuaikan pola shift lewat **Jadwal → Kelola Shift**,
   - susun roster minggu ini di **Jadwal**,
   - atur titik cafe dan radius di **Setelan → Lokasi absen**,
   - pakai **Orang → Uji Wajah** untuk menyetel ambang kecocokan sesuai
     pencahayaan cafe.

Letakkan HP di dudukan permanen. Bila HP dipindah atau lampu diganti, daftarkan
ulang wajah karyawan.

## Data

Semua data tersimpan di HP itu saja — pengaturan dan catatan absen di
`localStorage`, foto di `IndexedDB`. Tidak ada server dan tidak ada akun.
Ekspor CSV setiap tutup buku dan gunakan **Cadangkan (JSON)** secara berkala;
bila HP hilang atau di-reset, datanya ikut hilang.
