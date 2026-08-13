import type { Timestamp } from "firebase/firestore";

/* Bentuk dokumen Firestore, sama persis dengan model di aplikasi Android.
   Kalau salah satu berubah, keduanya harus ikut berubah. */

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
  kioskAdminPin: string;
  photoRequired: boolean;
}

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
  photoRequired: true,
};

export interface Employee {
  id: string;
  name: string;
  pinHash: string;
  pinSalt: string;
  pinIterations: number;
  /** null berarti ikut toleransi umum. */
  toleranceMinutes: number | null;
  active: boolean;
}

export interface Shift {
  id: string;
  code: string;
  name: string;
  /** "HH:mm". Bila end <= start, shift lewat tengah malam. */
  start: string;
  end: string;
  order: number;
}

export const ROSTER_OFF = "off";

export interface RosterDay {
  date: string;
  /** id karyawan -> id shift, atau ROSTER_OFF untuk libur. */
  assign: Record<string, string>;
}

export interface Punch {
  at: Timestamp;
  lat: number | null;
  lon: number | null;
  accuracyMeters: number | null;
  distanceMeters: number | null;
  outsideGeofence: boolean;
  photoPath: string;
  pinOk: boolean;
  adminOverride: boolean;
  noPin: boolean;
}

export interface AttendanceRecord {
  id: string;
  employeeId: string;
  employeeName: string;
  /** "yyyy-MM-dd", tanggal shift dimulai. */
  date: string;
  shiftId: string;
  shiftName: string;
  shiftStart: string;
  shiftEnd: string;
  offSchedule: boolean;
  checkIn: Punch | null;
  checkOut: Punch | null;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  workMinutes: number;
  overtimeMinutes: number;
  note: string;
  deviceId: string;
  correctedBy: string;
}

export interface KioskDevice {
  id: string;
  label: string;
  appVersion: string;
  lastSeen: Timestamp | null;
}
