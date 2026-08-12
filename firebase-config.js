/* Konfigurasi proyek Firebase untuk Absensi Cafe.

   Nilai-nilai ini memang boleh publik — apiKey Firebase bukan kunci rahasia,
   melainkan pengenal proyek. Yang mengamankan data adalah dua hal lain:
   pendaftaran mandiri dimatikan di console, dan aturan akses Firestore hanya
   mengizinkan akun cafe yang sah. */

window.FIREBASE_CONFIG = {
  apiKey: "AIzaSyBQabN3-m2L4VWI9peEqzyidJIV6Siek6M",
  authDomain: "absensi-cafe-8da42.firebaseapp.com",
  projectId: "absensi-cafe-8da42",
  storageBucket: "absensi-cafe-8da42.firebasestorage.app",
  messagingSenderId: "843346788064",
  appId: "1:843346788064:web:7880e33412bb9635119a85"
};
