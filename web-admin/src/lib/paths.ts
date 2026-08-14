import { collection, doc } from "firebase/firestore";
import { db } from "../firebase";

/**
 * Jalur dokumen di Firestore.
 *
 * Seluruh data cafe bersarang di bawah satu dokumen `cafe/main`, warisan
 * aplikasi versi lama yang mencerminkan satu tablet ke awan. Bentuknya
 * dipertahankan supaya karyawan, absen, dan pengaturan yang sudah ada langsung
 * terbaca tanpa migrasi.
 *
 * Semua jalur dikumpulkan di sini agar tidak ada string "cafe/main" yang
 * berserakan di halaman.
 */
export const cafeDoc = () => doc(db, "cafe", "main");
export const employeesCol = () => collection(cafeDoc(), "employees");
export const shiftsCol = () => collection(cafeDoc(), "shifts");
export const rosterCol = () => collection(cafeDoc(), "roster");
export const recordsCol = () => collection(cafeDoc(), "records");
/** Catatan tablet yang pernah tersambung; koleksi baru, tidak ada di data lama. */
export const devicesCol = () => collection(cafeDoc(), "devices");

export const employeeDoc = (id: string) => doc(employeesCol(), id);
export const shiftDoc = (id: string) => doc(shiftsCol(), id);
export const rosterDoc = (date: string) => doc(rosterCol(), date);
export const recordDoc = (id: string) => doc(recordsCol(), id);

/**
 * Pengenal dokumen bergaya aplikasi lama: waktu dalam basis 36 ditambah
 * beberapa huruf acak. Dipakai agar dokumen baru tidak terlihat asing di
 * samping data lama, dan tetap terurut menurut waktu pembuatan.
 */
export function newId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}
