import { getApp, getApps, initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

/* Nilai-nilai ini memang boleh publik: apiKey Firebase bukan kunci rahasia,
   melainkan pengenal proyek. Yang mengamankan data adalah pendaftaran mandiri
   yang dimatikan di console dan aturan akses di firebase/firestore.rules. */
const firebaseConfig = {
  apiKey: "AIzaSyBQabN3-m2L4VWI9peEqzyidJIV6Siek6M",
  authDomain: "absensi-cafe-8da42.firebaseapp.com",
  projectId: "absensi-cafe-8da42",
  messagingSenderId: "843346788064",
  appId: "1:843346788064:web:7880e33412bb9635119a85",
};

/* Cloud Storage tidak dipakai: foto bukti absen tertanam di dokumen absennya
   sendiri sebagai data URL, supaya ikut mengantre saat internet cafe mati.

   Aplikasi yang sudah ada dipakai ulang alih-alih dibuat lagi. Di produksi
   modul ini hanya dijalankan sekali, tapi saat `npm run dev` berkas ini bisa
   dimuat ulang oleh HMR — dan `initializeApp` yang kedua melempar
   `app/duplicate-app`, membuat seluruh halaman gagal dimuat ulang sampai
   peramban di-refresh manual. */
export const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
