import type { AttendanceRecord, Employee, FineTier, Settings, Shift } from "./types";

/**
 * Salinan aturan perhitungan dari `domain/AttendanceRules.kt`.
 *
 * Web admin butuh aturan yang sama untuk menghitung ulang saat catatan
 * dikoreksi dan untuk menyusun rekap serta ekspor. Keduanya memakai zona waktu
 * lokal perangkat — tablet kios dan PC admin berada di cafe yang sama, jadi
 * zonanya selalu sama juga.
 */

export function minutesOf(hhmm: string): number {
  const [h, m] = hhmm.split(":");
  const hh = Number.parseInt(h ?? "", 10);
  const mm = Number.parseInt(m ?? "", 10);
  if (Number.isNaN(hh) || Number.isNaN(mm)) return 0;
  return Math.min(Math.max(hh, 0), 23) * 60 + Math.min(Math.max(mm, 0), 59);
}

export function crossesMidnight(shift: Shift): boolean {
  return minutesOf(shift.end) <= minutesOf(shift.start);
}

/** "yyyy-MM-dd" -> Date tengah malam lokal. */
export function parseDate(date: string): Date {
  const [y, m, d] = date.split("-").map((v) => Number.parseInt(v, 10));
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
}

export function formatDate(date: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}`;
}

export function addDays(date: string, days: number): string {
  const d = parseDate(date);
  d.setDate(d.getDate() + days);
  return formatDate(d);
}

export function shiftStartAt(date: string, shift: Shift): Date {
  const d = parseDate(date);
  d.setMinutes(minutesOf(shift.start));
  return d;
}

export function shiftEndAt(date: string, shift: Shift): Date {
  const d = parseDate(date);
  if (crossesMidnight(shift)) d.setDate(d.getDate() + 1);
  d.setMinutes(minutesOf(shift.end));
  return d;
}

function diffMinutes(from: Date, to: Date): number {
  return Math.round((to.getTime() - from.getTime()) / 60000);
}

export function toleranceFor(employee: Employee | undefined, settings: Settings): number {
  return employee?.toleranceMinutes ?? settings.toleranceMinutes;
}

export function lateMinutes(
  checkInAt: Date,
  date: string,
  shift: Shift | null,
  tolerance: number,
): number {
  if (!shift) return 0;
  const batas = shiftStartAt(date, shift);
  batas.setMinutes(batas.getMinutes() + tolerance);
  return Math.max(0, diffMinutes(batas, checkInAt));
}

export function earlyLeaveMinutes(checkOutAt: Date, date: string, shift: Shift | null): number {
  if (!shift) return 0;
  return Math.max(0, diffMinutes(checkOutAt, shiftEndAt(date, shift)));
}

export function overtimeMinutes(
  checkOutAt: Date,
  date: string,
  shift: Shift | null,
  settings: Settings,
): number {
  if (!shift) return 0;
  const lebih = Math.max(0, diffMinutes(shiftEndAt(date, shift), checkOutAt));
  return lebih >= settings.minOvertimeMinutes ? lebih : 0;
}

export function workMinutes(checkInAt: Date, checkOutAt: Date): number {
  return Math.max(0, diffMinutes(checkInAt, checkOutAt));
}

/** Tingkat denda diurutkan naik; tingkat tanpa batas selalu di akhir. */
export function sortTiers(tiers: FineTier[]): FineTier[] {
  return [...tiers].sort((a, b) => {
    if (a.upToMinutes === null) return 1;
    if (b.upToMinutes === null) return -1;
    return a.upToMinutes - b.upToMinutes;
  });
}

/**
 * Denda dihitung saat ditampilkan, bukan disimpan di catatan absen, supaya
 * perubahan tarif langsung berlaku serempak di seluruh rekap dan ekspor.
 */
export function fineFor(late: number, settings: Settings): number {
  if (!settings.fineEnabled || late <= 0) return 0;
  const tiers = sortTiers(settings.fineTiers);
  if (tiers.length === 0) return 0;
  for (const t of tiers) {
    if (t.upToMinutes === null) return t.amount;
    if (late <= t.upToMinutes) return t.amount;
  }
  return tiers[tiers.length - 1]!.amount;
}

/**
 * Menghitung ulang seluruh angka sebuah catatan.
 *
 * Dipakai setiap kali admin mengoreksi jam masuk, jam pulang, atau shift —
 * angka turunan tidak pernah diketik tangan, supaya rekap tidak bisa berbeda
 * dari jam yang tercatat.
 */
export function recompute(
  record: AttendanceRecord,
  shift: Shift | null,
  employee: Employee | undefined,
  settings: Settings,
): AttendanceRecord {
  const efektif = record.offSchedule ? null : shift;
  const masuk = record.checkIn ? record.checkIn.at : null;
  const pulang = record.checkOut ? record.checkOut.at : null;

  return {
    ...record,
    lateMinutes: masuk
      ? lateMinutes(masuk, record.date, efektif, toleranceFor(employee, settings))
      : 0,
    earlyLeaveMinutes: pulang ? earlyLeaveMinutes(pulang, record.date, efektif) : 0,
    overtimeMinutes: pulang ? overtimeMinutes(pulang, record.date, efektif, settings) : 0,
    workMinutes: masuk && pulang ? workMinutes(masuk, pulang) : 0,
  };
}

/** Jarak dua titik bumi dalam meter (haversine). */
export function distanceMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const r = 6_371_000;
  const rad = (v: number) => (v * Math.PI) / 180;
  const dp = rad(lat2 - lat1);
  const dl = rad(lon2 - lon1);
  const a =
    Math.sin(dp / 2) ** 2 +
    Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dl / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
