import { fineFor } from "./rules";
import {
  ROSTER_OFF,
  type AttendanceRecord,
  type Employee,
  type RosterDay,
  type Settings,
} from "./types";

export interface RecapRow {
  employeeId: string;
  name: string;
  /** Hari yang dijadwalkan kerja pada periode ini (libur tidak dihitung). */
  scheduled: number;
  present: number;
  /** Dijadwalkan tapi tidak ada catatan absen sama sekali. */
  absent: number;
  /** Absen masuk tanpa absen pulang. */
  incomplete: number;
  offSchedule: number;
  lateCount: number;
  lateMinutes: number;
  fine: number;
  earlyLeaveMinutes: number;
  overtimeMinutes: number;
  workMinutes: number;
}

/**
 * Menyusun rekap satu periode.
 *
 * Alpa dihitung dari roster, bukan dari catatan absen: tanpa jadwal, hari
 * tanpa catatan tidak bisa dibedakan antara bolos dan memang libur.
 */
export function buildRecap(
  dates: string[],
  employees: Employee[],
  records: AttendanceRecord[],
  roster: Record<string, RosterDay>,
  settings: Settings,
): RecapRow[] {
  const perKaryawan = new Map<string, AttendanceRecord[]>();
  for (const r of records) {
    const list = perKaryawan.get(r.employeeId) ?? [];
    list.push(r);
    perKaryawan.set(r.employeeId, list);
  }

  return employees.map((e) => {
    const miliknya = perKaryawan.get(e.id) ?? [];
    const tanggalHadir = new Set(miliknya.map((r) => r.date));

    let scheduled = 0;
    let absent = 0;
    for (const tanggal of dates) {
      const tugas = roster[tanggal]?.assign?.[e.id];
      if (!tugas || tugas === ROSTER_OFF) continue;
      scheduled++;
      if (!tanggalHadir.has(tanggal)) absent++;
    }

    const baris: RecapRow = {
      employeeId: e.id,
      name: e.name,
      scheduled,
      present: tanggalHadir.size,
      absent,
      incomplete: miliknya.filter((r) => r.checkIn && !r.checkOut).length,
      offSchedule: miliknya.filter((r) => r.offSchedule).length,
      lateCount: miliknya.filter((r) => r.lateMinutes > 0).length,
      lateMinutes: sum(miliknya, (r) => r.lateMinutes),
      fine: sum(miliknya, (r) => fineFor(r.lateMinutes, settings)),
      earlyLeaveMinutes: sum(miliknya, (r) => r.earlyLeaveMinutes),
      overtimeMinutes: sum(miliknya, (r) => r.overtimeMinutes),
      workMinutes: sum(miliknya, (r) => r.workMinutes),
    };
    return baris;
  });
}

export function totalRow(rows: RecapRow[]): RecapRow {
  return {
    employeeId: "",
    name: "TOTAL",
    scheduled: sum(rows, (r) => r.scheduled),
    present: sum(rows, (r) => r.present),
    absent: sum(rows, (r) => r.absent),
    incomplete: sum(rows, (r) => r.incomplete),
    offSchedule: sum(rows, (r) => r.offSchedule),
    lateCount: sum(rows, (r) => r.lateCount),
    lateMinutes: sum(rows, (r) => r.lateMinutes),
    fine: sum(rows, (r) => r.fine),
    earlyLeaveMinutes: sum(rows, (r) => r.earlyLeaveMinutes),
    overtimeMinutes: sum(rows, (r) => r.overtimeMinutes),
    workMinutes: sum(rows, (r) => r.workMinutes),
  };
}

function sum<T>(items: T[], pick: (item: T) => number): number {
  return items.reduce((acc, item) => acc + pick(item), 0);
}
