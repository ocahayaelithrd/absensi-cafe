import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";

/* Nilai-nilai ini memang boleh publik: apiKey Firebase bukan kunci rahasia,
   melainkan pengenal proyek. Yang mengamankan data adalah pendaftaran mandiri
   yang dimatikan di console dan aturan akses di firebase/firestore.rules. */
const firebaseConfig = {
  apiKey: "AIzaSyBQabN3-m2L4VWI9peEqzyidJIV6Siek6M",
  authDomain: "absensi-cafe-8da42.firebaseapp.com",
  projectId: "absensi-cafe-8da42",
  storageBucket: "absensi-cafe-8da42.firebasestorage.app",
  messagingSenderId: "843346788064",
  appId: "1:843346788064:web:7880e33412bb9635119a85",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
