import { useEffect, useMemo, useState } from "react";
import {
  documentId,
  onSnapshot,
  query,
  where,
  type DocumentData,
  type QueryDocumentSnapshot,
} from "firebase/firestore";
import {
  cafeDoc,
  devicesCol,
  employeesCol,
  recordsCol,
  rosterCol,
  rosterDoc,
  shiftsCol,
} from "../lib/paths";
import { sortByName } from "../lib/sort";
import {
  defaultSettings,
  type AttendanceRecord,
  type Employee,
  type PinBy,
  type Punch,
  type RosterDay,
  type Settings,
  type Shift,
} from "../lib/types";

/* Pembacaan Firestore, sekaligus penerjemah dari nama field lama ke model
   aplikasi. Semua memakai pendengar snapshot, bukan sekali ambil: koreksi yang
   dikirim admin langsung terlihat di layar rekap tanpa perlu muat ulang, dan
   absen yang baru masuk dari tablet muncul sendiri.

   Tiap field dibaca satu per satu dengan nilai bawaan yang aman. Pemetaan
   otomatis gampang pecah diam-diam: satu field bertipe lain di dokumen lama
   membuat seluruh dokumen gagal dibaca, dan di aplikasi absensi itu berarti
   karyawan menghilang dari layar. */

function num(v: unknown, fallback: number): number {
  return typeof v === "number" && Number.isFinite(v) ? v : fallback;
}

function numOrNull(v: unknown): number | null {
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" ? v : fallback;
}

function bool(v: unknown, fallback: boolean): boolean {
  return typeof v === "boolean" ? v : fallback;
}

export interface SettingsState {
  settings: Settings;
  /**
   * Firestore sudah menjawab. Penting dibedakan dari "pengaturan kosong":
   * formulir yang menyalin nilai bawaan sebelum jawaban tiba akan menimpa
   * pengaturan sungguhan begitu disimpan.
   */
  loaded: boolean;
}

export function useSettingsState(): SettingsState {
  const [state, setState] = useState<SettingsState>({
    settings: defaultSettings,
    loaded: false,
  });
  const setSettings = (s: Settings) => setState({ settings: s, loaded: true });

  useEffect(() => {
    return onSnapshot(cafeDoc(), (snap) => {
      const s = (snap.data()?.settings ?? {}) as DocumentData;
      const tiers = Array.isArray(s.fineTiers)
        ? s.fineTiers.map((t: DocumentData) => ({
            upToMinutes: numOrNull(t?.upTo),
            amount: num(t?.amount, 0),
          }))
        : [];
      setSettings({
        cafeName: str(s.cafeName, defaultSettings.cafeName),
        toleranceMinutes: num(s.tolerance, defaultSettings.toleranceMinutes),
        minOvertimeMinutes: num(s.minOvertime, defaultSettings.minOvertimeMinutes),
        fineEnabled: bool(s.fineEnabled, defaultSettings.fineEnabled),
        fineTiers: tiers.length ? tiers : defaultSettings.fineTiers,
        geoMode: s.geoMode === "strict" || s.geoMode === "warn" ? s.geoMode : "off",
        geoLat: numOrNull(s.geoLat),
        geoLon: numOrNull(s.geoLon),
        geoRadiusMeters: num(s.geoRadius, defaultSettings.geoRadiusMeters),
        kioskAdminPin: String(s.pin ?? defaultSettings.kioskAdminPin),
        pinRequired: s.pinMode !== "off",
      });
    });
  }, []);
  return state;
}

/** Pengaturan saja, untuk layar yang cukup memakai nilai bawaan selagi memuat. */
export function useSettings(): Settings {
  return useSettingsState().settings;
}

function toEmployee(snap: QueryDocumentSnapshot<DocumentData>): Employee {
  const d = snap.data();
  const pin = d.pin;
  return {
    id: snap.id,
    name: str(d.name, "(tanpa nama)"),
    role: str(d.role),
    plainPin: pin === null || pin === undefined ? "" : String(pin),
    pinHash: str(d.pinHash),
    pinSalt: str(d.pinSalt),
    pinIterations: num(d.pinIterations, 0),
    toleranceMinutes: numOrNull(d.tolerance),
    active: bool(d.active, true),
  };
}

export function useEmployees(includeInactive = false): Employee[] {
  const [list, setList] = useState<Employee[]>([]);
  useEffect(() => {
    return onSnapshot(employeesCol(), (snap) => {
      setList(sortByName(snap.docs.map(toEmployee), (e) => e.name));
    });
  }, []);
  return useMemo(
    () => (includeInactive ? list : list.filter((e) => e.active)),
    [list, includeInactive],
  );
}

/** Peta id karyawan ke namanya, untuk menamai catatan absen. */
export function useEmployeeNames(employees: Employee[]): Map<string, string> {
  return useMemo(
    () => new Map(employees.map((e) => [e.id, e.name])),
    [employees],
  );
}

export function useShifts(): Shift[] {
  const [list, setList] = useState<Shift[]>([]);
  useEffect(() => {
    return onSnapshot(shiftsCol(), (snap) => {
      const items = snap.docs.map((s) => {
        const d = s.data();
        const name = str(d.name, "(tanpa nama)");
        return {
          id: s.id,
          code: str(d.code, name.slice(0, 1).toUpperCase()),
          name,
          start: str(d.start, "00:00"),
          end: str(d.end, "00:00"),
        } satisfies Shift;
      });
      items.sort((a, b) => a.start.localeCompare(b.start) || a.name.localeCompare(b.name));
      setList(items);
    });
  }, []);
  return list;
}

/** Dokumen roster menyimpan penugasan di field `hari`. */
function toRosterDay(id: string, d: DocumentData | undefined): RosterDay {
  const hari = d?.hari;
  const assign: Record<string, string> = {};
  if (hari && typeof hari === "object") {
    for (const [k, v] of Object.entries(hari as Record<string, unknown>)) {
      if (typeof v === "string") assign[k] = v;
    }
  }
  return { date: id, assign };
}

/** Roster untuk beberapa tanggal tertentu, dipakai layar yang hanya butuh satu-dua hari. */
export function useRoster(dates: string[]): Record<string, RosterDay> {
  const kunci = dates.join(",");
  const [map, setMap] = useState<Record<string, RosterDay>>({});
  useEffect(() => {
    if (!dates.length) {
      setMap({});
      return;
    }
    const unsubs = dates.map((tanggal) =>
      onSnapshot(rosterDoc(tanggal), (snap) => {
        setMap((prev) => ({ ...prev, [tanggal]: toRosterDay(tanggal, snap.data()) }));
      }),
    );
    return () => unsubs.forEach((u) => u());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kunci]);
  return map;
}

/**
 * Roster untuk sebuah rentang tanggal.
 *
 * Rekap sebulan butuh sampai 31 hari sekaligus; memasang satu pendengar per
 * tanggal akan membuka puluhan koneksi hanya untuk halaman yang jarang berubah
 * saat sedang dibuka.
 */
export function useRosterRange(from: string, to: string): Record<string, RosterDay> {
  const [map, setMap] = useState<Record<string, RosterDay>>({});
  useEffect(() => {
    if (!from || !to) {
      setMap({});
      return;
    }
    const q = query(
      rosterCol(),
      where(documentId(), ">=", from),
      where(documentId(), "<=", to),
    );
    return onSnapshot(q, (snap) => {
      const hasil: Record<string, RosterDay> = {};
      for (const s of snap.docs) hasil[s.id] = toRosterDay(s.id, s.data());
      setMap(hasil);
    });
  }, [from, to]);
  return map;
}

function pinBy(v: unknown): PinBy {
  return v === "pin" || v === "kosong" || v === "admin" || v === "off" ? v : "off";
}

/**
 * Menyusun satu sisi absen dari field berawalan `in`/`out`.
 *
 * Data lama menyimpan kedua sisi mendatar dalam satu dokumen — `inAt`,
 * `inLat`, `outAt`, `outLat`, dan seterusnya — bukan sebagai dua peta.
 */
function toPunch(d: DocumentData, sisi: "in" | "out"): Punch | null {
  const at = d[`${sisi}At`];
  if (typeof at !== "number" || !Number.isFinite(at)) return null;
  return {
    at: new Date(at),
    lat: numOrNull(d[`${sisi}Lat`]),
    lon: numOrNull(d[`${sisi}Lon`]),
    accuracyMeters: numOrNull(d[`${sisi}Acc`]),
    distanceMeters: numOrNull(d[`${sisi}Dist`]),
    outsideGeofence: bool(d[`${sisi}GeoFlag`], false),
    pinBy: pinBy(d[`${sisi}PinBy`]),
    photo: str(d[sisi === "in" ? "fotoMasuk" : "fotoPulang"]),
  };
}

export function toRecord(snap: QueryDocumentSnapshot<DocumentData>): AttendanceRecord {
  const d = snap.data();
  return {
    id: snap.id,
    employeeId: str(d.empId),
    date: str(d.date),
    shiftId: str(d.shiftId),
    offSchedule: bool(d.offSchedule, false),
    checkIn: toPunch(d, "in"),
    checkOut: toPunch(d, "out"),
    lateMinutes: num(d.lateMin, 0),
    earlyLeaveMinutes: num(d.earlyMin, 0),
    workMinutes: num(d.workMin, 0),
    overtimeMinutes: num(d.otMin, 0),
    note: str(d.note),
    edited: bool(d.edited, false),
  };
}

/**
 * Absen dalam rentang tanggal.
 *
 * Rentangnya disaring di peladen dengan perbandingan string: format
 * "yyyy-MM-dd" berurut secara leksikografis sama dengan urutan kalender,
 * sehingga indeks satu field sudah cukup.
 */
export function useRecords(from: string, to: string): AttendanceRecord[] {
  const [list, setList] = useState<AttendanceRecord[]>([]);
  useEffect(() => {
    if (!from || !to) {
      setList([]);
      return;
    }
    const q = query(recordsCol(), where("date", ">=", from), where("date", "<=", to));
    return onSnapshot(q, (snap) => setList(snap.docs.map(toRecord)));
  }, [from, to]);
  return list;
}

export interface KioskDevice {
  id: string;
  label: string;
  appVersion: string;
  lastSeen: Date | null;
}

export function useDevices(): KioskDevice[] {
  const [list, setList] = useState<KioskDevice[]>([]);
  useEffect(() => {
    return onSnapshot(devicesCol(), (snap) => {
      setList(
        snap.docs.map((s) => {
          const d = s.data();
          const seen = d.lastSeen;
          return {
            id: s.id,
            label: str(d.label, s.id),
            appVersion: str(d.appVersion, "?"),
            lastSeen: typeof seen === "number" ? new Date(seen) : null,
          } satisfies KioskDevice;
        }),
      );
    });
  }, []);
  return list;
}
