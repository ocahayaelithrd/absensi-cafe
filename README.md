# Absensi Cafe

Absensi terpusat untuk karyawan cafe, dipecah menjadi dua aplikasi yang berbagi
satu proyek Firebase:

| Bagian | Untuk siapa | Teknologi |
| --- | --- | --- |
| [`android/`](android) | Karyawan — tablet kios di kasir | Kotlin + Jetpack Compose |
| [`web-admin/`](web-admin) | Admin — dari PC | React + TypeScript + Vite |
| [`firebase/`](firebase) | Aturan akses dan indeks | Firestore & Cloud Storage rules |

Karyawan mengetuk namanya di tablet, memasukkan **PIN pribadi**, lalu kamera
depan mengambil foto selfie. PIN membuktikan siapa yang absen, foto menyimpan
buktinya, dan geofence memastikan absen dilakukan di cafe. Admin mengelola
karyawan, shift, jadwal, dan rekap dari peramban di PC — tidak perlu menyentuh
tablet sama sekali.

## Pembagian tugas antara dua aplikasi

Tablet **hanya mencatat absen**. Semua yang menentukan angka gaji — daftar
karyawan, PIN, pola shift, jadwal, tarif denda, titik cafe — hanya bisa diubah
dari web admin, dan aturan Firestore menolak tulisan dari akun kios ke sana.
Tablet yang hilang atau dipinjam karyawan karena itu tidak bisa dipakai
menaikkan gaji siapa pun.

```
                    Firestore + Storage
                  (satu proyek Firebase)
                     ▲              ▲
       tulis absen   │              │   baca semua, tulis semua
       baca acuan    │              │
              ┌──────┴─────┐   ┌────┴────────┐
              │  Tablet    │   │  Web admin  │
              │  (kiosk)   │   │  (admin)    │
              └────────────┘   └─────────────┘
```

## Fitur

- **Absen masuk & pulang** dengan PIN pribadi dan foto selfie sebagai bukti
- **Jadwal shift per tanggal** — roster mingguan, shift tiap karyawan bisa
  berbeda tiap hari; shift lintas tengah malam didukung
- **Hitung otomatis** keterlambatan, pulang cepat, durasi kerja, dan lembur
- **Denda keterlambatan bertingkat** yang tarif dan batas menitnya diatur sendiri
- **Geotagging & geofencing** — koordinat tersimpan di tiap absen, dengan
  pembatasan radius area cafe dan deteksi aplikasi pemalsu lokasi
- **Rekap harian & bulanan** per karyawan, termasuk hitungan alpa
- **Ekspor Excel (.xlsx)** tiga lembar untuk perhitungan gaji
- **Koreksi dari PC** — jam yang salah diperbaiki admin, angka turunannya
  dihitung ulang sendiri
- **Bekerja luring** — tablet tetap mencatat absen saat internet cafe mati,
  foto menyusul terunggah begitu jaringan hidup

## Menyiapkan Firebase

Proyek yang dipakai adalah **absensi-cafe-8da42**, sama dengan aplikasi versi
sebelumnya.

1. **Matikan pendaftaran mandiri.** Console → Authentication → Settings → *User
   actions* → hilangkan centang *Enable create (sign-up)*. Tanpa ini siapa pun
   yang menemukan konfigurasi bisa membuat akun sendiri.
2. **Buat dua akun** di Authentication → Users, misalnya
   `kios@cafe.local` dan `admin@cafe.local`.
3. **Beri peran** dengan membuat dokumen di Firestore secara manual — satu
   dokumen per akun, id dokumennya adalah UID akun itu:

   ```
   users/{uid-tablet}   { role: "kiosk" }
   users/{uid-admin}    { role: "admin" }
   ```

   Peran inilah yang dibaca aturan Firestore. Akun tanpa dokumen ini tidak bisa
   membaca apa pun.
4. **Pasang aturan dan indeks** — kerjakan setelah langkah 3, karena aturan baru
   mengunci akses ke akun yang punya dokumen `users/{uid}`:

   ```bash
   npx firebase-tools deploy --only firestore:rules,firestore:indexes,storage
   ```

## Struktur data

Seluruh data cafe bersarang di bawah satu dokumen **`cafe/main`** — bentuk
warisan aplikasi versi lama, dipertahukan supaya karyawan, absen, dan
pengaturan yang sudah ada langsung terbaca tanpa migrasi apa pun.

```
cafe/main                    field `settings`: nama cafe, denda, geofence, toleransi
cafe/main/employees/{id}     karyawan; PIN lama apa adanya, PIN baru sebagai hash
cafe/main/shifts/{id}        pola shift
cafe/main/roster/{yyyy-MM-dd} field `hari`: id karyawan → id shift atau "off"
cafe/main/records/{id}       absen, kedua sisi mendatar dalam satu dokumen
cafe/main/devices/{id}       catatan tablet yang pernah tersambung
users/{uid}                  peran akun: "admin" atau "kiosk"
```

Nama field di dalamnya juga warisan: `empId`, `inAt`/`outAt` sebagai milidetik
epoch, `lateMin`, `otMin`, `inLat`, `inPinBy`, dan seterusnya. Nama-nama itu
sengaja **tidak menyebar** ke seluruh kode — penerjemahannya terkumpul di dua
tempat saja, [`Mappers.kt`](android/app/src/main/java/id/omi/absensicafe/data/Mappers.kt)
untuk tablet, serta [`useData.ts`](web-admin/src/hooks/useData.ts) dan
[`write.ts`](web-admin/src/lib/write.ts) untuk web. Sisanya bekerja dengan model
yang bernama wajar.

Cloud Storage tidak dipakai: foto bukti tertanam di dokumen absennya sendiri
sebagai data URL JPEG 360×360 (sekitar 20–30 KB per lembar), jauh di bawah batas
1 MB per dokumen.

## Menjalankan web admin

```bash
cd web-admin
npm install
npm run dev
```

Buka `http://localhost:5173`, masuk dengan akun admin. Untuk menerbitkannya:

```bash
cd web-admin && npm run build
npx firebase-tools deploy --only hosting
```

Halaman pertama yang perlu diisi, berurutan: **Pola Shift** → **Karyawan**
(beri tiap orang PIN 4 angka) → **Jadwal** → **Pengaturan**.

## Membangun aplikasi Android

Perlu **Android Studio** (JDK 17 sudah termasuk di dalamnya). Gradle wrapper dan
Android SDK diunduh sendiri saat proyek pertama kali dibuka.

1. Ambil `google-services.json` lebih dulu — lihat
   [`android/app/GOOGLE-SERVICES.md`](android/app/GOOGLE-SERVICES.md).
2. Buka folder [`android/`](android) di Android Studio, tunggu sinkronisasi
   Gradle selesai.
3. Jalankan ke tablet lewat **Run**, atau buat APK lewat
   **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

Dari baris perintah, setelah wrapper terbentuk:

```bash
cd android && ./gradlew assembleDebug
```

### Memasang di tablet kasir

1. Pasang APK, buka aplikasinya, masuk dengan **akun kios**.
2. Izinkan **kamera** dan **lokasi** saat diminta.
3. Ketuk ikon gigi → masukkan PIN penyelia (bawaan `1234`, ganti di web admin) →
   beri nama kios itu.
4. Letakkan tablet di dudukan permanen dekat kasir. Layar dijaga tetap menyala
   selama aplikasi terbuka.

## Cara perhitungan

**Keterlambatan** dihitung saat karyawan absen masuk, memakai jam shift yang
dijadwalkan pada tanggal itu:

```
telat = jam absen masuk − (jam mulai shift + toleransi)     // minimum 0
```

Toleransi diambil dari pengaturan karyawan bila diisi, kalau kosong ikut
pengaturan umum. Dari menit telat itu, denda ditentukan lewat tingkatan yang
diatur di **Pengaturan → Denda keterlambatan**. Bawaannya:

| Telat | Denda |
| --- | --- |
| 1–15 menit | Rp 5.000 |
| 16–30 menit | Rp 15.000 |
| Lebih dari 30 menit | Rp 30.000 |

Denda **dihitung saat ditampilkan**, bukan disimpan di catatan absen, sehingga
perubahan tarif langsung berlaku konsisten di seluruh rekap dan ekspor. Seluruh
fitur denda bisa dimatikan lewat satu sakelar — saat mati, kolomnya hilang dari
rekap maupun Excel.

**Lembur** baru dihitung setelah kelebihan jam mencapai ambang yang diatur
(bawaan 30 menit), supaya merapikan meja lima menit tidak menjadi upah lembur.

**Alpa** dihitung dari roster, bukan dari catatan absen: tanpa jadwal, hari
tanpa catatan tidak bisa dibedakan antara bolos dan memang libur. Hari libur dan
hari yang belum dijadwalkan tidak pernah dihitung alpa.

Aturan yang sama ditulis dua kali — di
[`AttendanceRules.kt`](android/app/src/main/java/id/omi/absensicafe/domain/AttendanceRules.kt)
untuk tablet dan di [`rules.ts`](web-admin/src/lib/rules.ts) untuk web, karena
keduanya harus bisa menghitung sendiri: tablet saat mencatat absen tanpa
internet, web saat admin mengoreksi jam. Kalau salah satu berubah, keduanya
harus ikut berubah. Perilakunya dikunci oleh
[`AttendanceRulesTest.kt`](android/app/src/test/java/id/omi/absensicafe/domain/AttendanceRulesTest.kt).

## Jadwal shift

Shift cafe berubah-ubah tiap hari, jadi jadwal disusun sebagai **roster**: satu
shift untuk satu karyawan pada satu tanggal. Di halaman **Jadwal**:

- ketuk **satu sel** untuk mengubah sehari,
- ketuk **nama karyawan** untuk mengisi seminggu sekaligus,
- ketuk **kepala kolom hari** untuk mengatur semua karyawan pada hari itu,
- pakai **Salin Minggu Lalu** kalau polanya berulang — sel yang sudah terisi
  minggu ini tidak ditimpa.

Tiap sel punya tiga keadaan: shift tertentu, **Libur** (tidak dihitung alpa),
atau **belum dijadwalkan**. Karyawan tetap bisa absen di hari yang tidak
dijadwalkan — catatannya tersimpan dan ditandai *di luar jadwal*, tapi telat dan
lembur tidak dihitung karena tidak ada jam acuan.

Jam selesai yang lebih awal dari jam mulai berarti shift lewat tengah malam.
Karyawan shift malam yang datang pukul 00:30 tetap tercatat pada tanggal
kemarin, bukan tanggal baru.

## PIN karyawan

Setiap karyawan punya PIN 4 angka yang diminta sebelum kamera menyala. PIN
diatur admin di halaman **Karyawan**, tidak boleh sama antar karyawan, dan
langsung ditolak kalau bentrok.

- **PIN benar** → lanjut berfoto, catatan bersih.
- **Salah 3 kali** → ditawarkan izin penyelia; kalau dipakai, catatannya
  ditandai *izin penyelia*.
- **Dibatalkan** → tidak ada yang tercatat, kamera tidak menyala.
- **PIN belum diatur** → karyawan tetap bisa absen supaya shift pagi tidak
  macet, tapi catatannya ditandai *tanpa PIN* dan admin diberi peringatan
  berisi nama-namanya.

### Dua bentuk penyimpanan PIN

Data lama menyimpan PIN **apa adanya** di field `pin`. Empat angka terlalu
sedikit untuk itu: siapa pun yang bisa membaca satu dokumen karyawan langsung
tahu PIN-nya.

PIN yang diubah lewat web admin karena itu disimpan sebagai
**PBKDF2-HMAC-SHA256 120.000 putaran** dengan garam acak per karyawan, dan field
lamanya dikosongkan. Web membuat hash lewat WebCrypto
([`pin.ts`](web-admin/src/lib/pin.ts)), tablet memeriksanya lewat
`SecretKeyFactory` ([`Pin.kt`](android/app/src/main/java/id/omi/absensicafe/domain/Pin.kt));
karena parameternya sama persis, PIN yang dibuat di PC bisa diperiksa di tablet
tanpa jaringan.

Kedua bentuk tetap diterima saat absen, supaya karyawan tidak perlu berganti PIN
serentak. Halaman **Karyawan** menandai siapa yang PIN-nya masih tersimpan polos;
mengubah PIN orang itu sekali saja sudah memindahkannya ke bentuk aman. **Selama
masih ada yang bertanda itu, PIN mereka bisa dibaca siapa pun yang punya akses
Firestore** — termasuk akun kios di tablet.

Karena hash bergaram tidak bisa dibandingkan langsung, pemeriksaan PIN bentrok
menghitung ulang PIN calon dengan garam tiap karyawan saat admin menyimpan.

## Lokasi absen

Setiap absen menyimpan koordinat GPS beserta akurasinya dan jarak ke titik cafe.
Titik lokasi diatur di **Pengaturan → Lokasi absen**. Tiga mode tersedia:

| Mode | Perilaku |
| --- | --- |
| Wajib di area | Absen ditahan bila di luar radius atau GPS mati, kecuali diloloskan penyelia lewat PIN |
| Peringatan saja | Absen tetap tercatat, ditandai bila di luar radius |
| Nonaktif | Tanpa pembatasan; koordinat tetap disimpan bila titik cafe sudah diisi |

Saat kamera menyala, lokasi dicari berbarengan agar sudah dapat sinyal ketika
foto diambil, dan statusnya tampil sebagai bilah di bawah kamera. Lokasi yang
berasal dari aplikasi pemalsu ditandai *lokasi palsu* dan, pada mode wajib,
ditahan seperti absen di luar area.

**Menentukan radius.** GPS di dalam ruangan biasanya meleset 20–50 meter, kadang
lebih. Radius di bawah 50 meter cenderung menolak karyawan yang sebenarnya sudah
berada di cafe. Titik yang paling tepat diambil dari tablet yang berdiri di
kasir, bukan dari PC di ruang belakang.

Koordinat, akurasi, dan jarak ikut terekspor ke Excel. Di rincian catatan absen,
tiap titik bisa dibuka di Google Maps.

## Ekspor

Ekspor menghasilkan satu berkas **.xlsx** berisi tiga lembar:

| Lembar | Isi |
| --- | --- |
| Rekap | Ringkasan per karyawan beserta baris total |
| Detail | Satu baris per catatan absen, lengkap dengan shift, denda, dan koordinat |
| Info | Periode, waktu ekspor, dan seluruh pengaturan yang berlaku saat itu |

Angka ditulis sebagai angka sungguhan dengan format sel — rupiah, bilangan
bulat, jam desimal, dan koordinat enam angka di belakang koma — sehingga
langsung bisa dijumlahkan atau dijadikan pivot tanpa dibersihkan dulu. Baris
kepala dibekukan dan diberi filter otomatis.

## Luring

Tablet memakai simpanan lokal Firestore tanpa batas ukuran, jadi setelah sekali
tersinkron aplikasinya tetap jalan penuh saat internet cafe mati: daftar
karyawan, jadwal, dan PIN semuanya dibaca dari simpanan, dan absen baru
tersimpan lokal lalu dikirim sendiri begitu jaringan hidup.

Penulisan ke Firestore sengaja tidak ditunggu selesai. Saat luring, penulisan
baru rampung setelah tersambung lagi — menunggunya akan menggantung layar
karyawan tanpa alasan, padahal catatannya sudah aman tersimpan di tablet.

Foto ikut aman karena tertanam di dokumen absennya sendiri, bukan diunggah
terpisah ke Cloud Storage. Firestore mengantre penulisan saat internet mati;
Storage tidak punya antrean seperti itu. Dengan menanam fotonya, absen berikut
buktinya tersimpan dalam satu penulisan yang pasti terkirim begitu jaringan
hidup — tanpa pekerjaan latar yang harus dijaga, dan tanpa keadaan setengah
jadi berupa catatan tanpa foto.

Harganya adalah ukuran: dokumen Firestore dibatasi 1 MB dan satu catatan memuat
dua foto, jadi foto dipotong menjadi bujur sangkar 360 piksel dengan mutu 72
sebelum disimpan. Pengecilan itu berjalan setelah layar hasil tampil, bukan
sebelumnya — di tablet murah prosesnya makan ratusan milidetik, dan menahan
layar selama itu membuat karyawan mengira absennya gagal lalu menekan ulang.

## Keamanan data

Nilai di [`web-admin/src/firebase.ts`](web-admin/src/firebase.ts) memang publik;
`apiKey` Firebase bukan kunci rahasia melainkan pengenal proyek. Pengaman
datanya tiga: pendaftaran mandiri dimatikan di console, akses dikunci ke akun
yang punya dokumen `users/{uid}`, dan peran kios dibatasi hanya boleh menulis
absen.

Sekali absen pulang tercatat, kios tidak boleh lagi menyentuh catatan itu sama
sekali; hanya admin yang bisa mengoreksinya. Tablet memang tidak pernah perlu —
foto ikut dalam penulisan yang sama, jadi tidak ada susulan apa pun setelah satu
hari selesai.

Cloud Storage tidak dipakai, dan aturannya di
[`firebase/storage.rules`](firebase/storage.rules) menutup seluruh bucket supaya
tidak menganga dengan aturan bawaan.
