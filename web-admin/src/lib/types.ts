/* Bentuk data yang dipakai di dalam aplikasi.
 *
 * Ini BUKAN bentuk dokumen Firestore. Firestore masih memakai struktur
 * warisan aplikasi lama — semuanya di bawah `cafe/main`, dengan nama field
 * seperti `empId`, `inAt`, `lateMin`, dan roster di field `hari`. Penerjemahan
 * dua arah ada di `hooks/useData.ts` (baca) dan `lib/write.ts` (tulis), supaya
 * halaman-halaman tidak perlu tahu nama lama sama sekali.
 *
 * Model yang sama ditulis ulang di sisi Android pada `data/model/Models.kt`.
 */

export type GeoMode = "off" | "warn" | "strict";

export interface FineTier {
  /** null berarti tingkat terakhir: berlaku untuk telat berapa pun di atasnya. */
  upToMinutes: number | null;
  amount: number;
}

export interface Settings {
  cafeName: string;
  toleranceMinutes: number;
  minOvertimeMinutes: number;
  fineEnabled: boolean;
  fineTiers: FineTier[];
  geoMode: GeoMode;
  geoLat: number | null;
  geoLon: number | null;
  geoRadiusMeters: number;
  /** PIN penyelia di tablet; di Firestore bernama `pin`. */
  kioskAdminPin: string;
  /** Karyawan wajib memasukkan PIN pribadi; di Firestore `pinMode`. */
  pinRequired: boolean;
  faceMode: FaceMode;
  /** Kemiripan minimum yang dianggap cocok, 0–100. */
  faceThreshold: number;
}

export type FaceMode = "off" | "warn" | "strict";

export const defaultSettings: Settings = {
  cafeName: "Absensi Cafe",
  toleranceMinutes: 10,
  minOvertimeMinutes: 30,
  fineEnabled: true,
  fineTiers: [
    { upToMinutes: 15, amount: 5000 },
    { upToMinutes: 30, amount: 15000 },
    { upToMinutes: null, amount: 30000 },
  ],
  geoMode: "off",
  geoLat: null,
  geoLon: null,
  geoRadiusMeters: 100,
  kioskAdminPin: "1234",
  pinRequired: true,
  faceMode: "off",
  faceThreshold: 65,
};

export interface Employee {
  id: string;
  name: string;
  /** Jabatan bebas, ikut dari data lama. */
  role: string;
  /**
   * PIN apa adanya dari data lama. Kosong berarti PIN belum diatur, atau
   * sudah dipindahkan ke [pinHash].
   */
  plainPin: string;
  /** PBKDF2; diisi begitu admin mengubah PIN lewat web ini. */
  pinHash: string;
  pinSalt: string;
  pinIterations: number;
  /** null berarti ikut toleransi umum. */
  toleranceMinutes: number | null;
  active: boolean;
  /**
   * Jumlah pola wajah yang tersimpan. Vektornya sendiri tidak dibaca web —
   * pencocokan hanya terjadi di tablet, jadi di sini cukup diketahui ada atau
   * tidak.
   */
  faceTemplateCount: number;
  /** Pengenal model yang membuat polanya, untuk ditampilkan saat perlu. */
  faceModel: string;
}

export function hasFace(e: Employee): boolean {
  return e.faceTemplateCount > 0;
}

export function hasPin(e: Employee): boolean {
  return Boolean(e.pinHash) || Boolean(e.plainPin);
}

export interface Shift {
  id: string;
  code: string;
  name: string;
  /** "HH:mm". Bila end <= start, shift lewat tengah malam. */
  start: string;
  end: string;
}

export const ROSTER_OFF = "off";

export interface RosterDay {
  date: string;
  /** id karyawan -> id shift, atau ROSTER_OFF untuk libur. */
  assign: Record<string, string>;
}

/** Cara sebuah absen diloloskan; di Firestore disimpan sebagai `inPinBy`/`outPinBy`. */
export type PinBy = "pin" | "kosong" | "admin" | "off";

export interface Punch {
  at: Date;
  lat: number | null;
  lon: number | null;
  accuracyMeters: number | null;
  distanceMeters: number | null;
  outsideGeofence: boolean;
  pinBy: PinBy;
  /** Kemiripan wajah 0–100; null berarti tidak diperiksa. */
  faceScore: number | null;
  /** Wajah diperiksa tapi di bawah ambang, atau tidak terdeteksi. */
  faceFlag: boolean;
  /**
   * Foto bukti sebagai data URL JPEG, tertanam di dokumen absen.
   * Kosong berarti absen itu memang tidak berfoto.
   */
  photo: string;
}

export interface AttendanceRecord {
  id: string;
  employeeId: string;
  /** "yyyy-MM-dd", tanggal shift dimulai. */
  date: string;
  shiftId: string;
  offSchedule: boolean;
  checkIn: Punch | null;
  checkOut: Punch | null;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  workMinutes: number;
  overtimeMinutes: number;
  note: string;
  /** Pernah dikoreksi admin; di Firestore bernama `edited`. */
  edited: boolean;
}
