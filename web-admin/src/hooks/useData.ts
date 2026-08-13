import { useEffect, useMemo, useState } from "react";
import {
  collection,
  doc,
  documentId,
  onSnapshot,
  query,
  where,
  type DocumentData,
  type QueryDocumentSnapshot,
} from "firebase/firestore";
import { db } from "../firebase";
import { sortByName } from "../lib/sort";
import {
  defaultSettings,
  type AttendanceRecord,
  type Employee,
  type KioskDevice,
  type Punch,
  type RosterDay,
  type Settings,
  type Shift,
} from "../lib/types";

/* Semua pembacaan memakai pendengar snapshot, bukan sekali ambil: koreksi yang
   dikirim admin langsung terlihat di layar rekap tanpa perlu muat ulang, dan
   absen yang baru masuk dari tablet muncul sendiri. */

function num(v: unknown, fallback: number): number {
  return typeof v === "number" ? v : fallback;
}

function numOrNull(v: unknown): number | null {
  return typeof v === "number" ? v : null;
}

function str(v: unknown, fallback = ""): string {
  return typeof v === "string" ? v : fallback;
}

function bool(v: unknown, fallback: boolean): boolean {
  return typeof v === "boolean" ? v : fallback;
}

export function useSettings(): Settings {
  const [settings, setSettings] = useState<Settings>(defaultSettings);
  useEffect(() => {
    return onSnapshot(doc(db, "config", "settings"), (snap) => {
      if (!snap.exists()) {
        setSettings(defaultSettings);
        return;
      }
      const d = snap.data();
      const tiers = Array.isArray(d.fineTiers)
        ? d.fineTiers.map((t: DocumentData) => ({
            upToMinutes: numOrNull(t.upToMinutes),
            amount: num(t.amount, 0),
          }))
        : defaultSettings.fineTiers;
      setSettings({
        cafeName: str(d.cafeName, defaultSettings.cafeName),
        toleranceMinutes: num(d.toleranceMinutes, defaultSettings.toleranceMinutes),
        minOvertimeMinutes: num(d.minOvertimeMinutes, defaultSettings.minOvertimeMinutes),
        fineEnabled: bool(d.fineEnabled, defaultSettings.fineEnabled),
        fineTiers: tiers.length ? tiers : defaultSettings.fineTiers,
        geoMode:
          d.geoMode === "strict" || d.geoMode === "warn" ? d.geoMode : "off",
        geoLat: numOrNull(d.geoLat),
        geoLon: numOrNull(d.geoLon),
        geoRadiusMeters: num(d.geoRadiusMeters, defaultSettings.geoRadiusMeters),
        kioskAdminPin: str(d.kioskAdminPin, defaultSettings.kioskAdminPin),
        photoRequired: bool(d.photoRequired, defaultSettings.photoRequired),
      });
    });
  }, []);
  return settings;
}

export function useEmployees(includeInactive = false): Employee[] {
  const [list, setList] = useState<Employee[]>([]);
  useEffect(() => {
    return onSnapshot(collection(db, "employees"), (snap) => {
      const items = snap.docs.map((docSnap) => toEmployee(docSnap));
      setList(sortByName(items, (e) => e.name));
    });
  }, []);
  return useMemo(
    () => (includeInactive ? list : list.filter((e) => e.active)),
    [list, includeInactive],
  );
}

function toEmployee(snap: QueryDocumentSnapshot<DocumentData>): Employee {
  const d = snap.data();
  return {
    id: snap.id,
    name: str(d.name, "(tanpa nama)"),
    pinHash: str(d.pinHash),
    pinSalt: str(d.pinSalt),
    pinIterations: num(d.pinIterations, 0),
    toleranceMinutes: numOrNull(d.toleranceMinutes),
    active: bool(d.active, true),
  };
}

export function useShifts(): Shift[] {
  const [list, setList] = useState<Shift[]>([]);
  useEffect(() => {
    return onSnapshot(collection(db, "shifts"), (snap) => {
      const items = snap.docs.map((s) => {
        const d = s.data();
        return {
          id: s.id,
          code: str(d.code, "?"),
          name: str(d.name, "(tanpa nama)"),
          start: str(d.start, "00:00"),
          end: str(d.end, "00:00"),
          order: num(d.order, 0),
        } satisfies Shift;
      });
      items.sort((a, b) => a.order - b.order || a.name.localeCompare(b.name));
      setList(items);
    });
  }, []);
  return list;
}

/** Roster untuk sekumpulan tanggal, dipetakan tanggal -> penugasan. */
export function useRoster(dates: string[]): Record<string, RosterDay> {
  const kunci = dates.join(",");
  const [map, setMap] = useState<Record<string, RosterDay>>({});
  useEffect(() => {
    if (!dates.length) {
      setMap({});
      return;
    }
    const unsubs = dates.map((tanggal) =>
      onSnapshot(doc(db, "roster", tanggal), (snap) => {
        const assign =
          snap.exists() && snap.data().assign && typeof snap.data().assign === "object"
            ? (snap.data().assign as Record<string, string>)
            : {};
        setMap((prev) => ({ ...prev, [tanggal]: { date: tanggal, assign } }));
      }),
    );
    return () => unsubs.forEach((u) => u());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kunci]);
  return map;
}

/**
 * Roster untuk sebuah rentang tanggal, dibaca sekali jalan.
 *
 * Rekap sebulan butuh sampai 31 hari sekaligus; memasang satu pendengar per
 * tanggal seperti [useRoster] akan membuka puluhan koneksi hanya untuk halaman
 * yang jarang berubah saat sedang dibuka.
 */
export function useRosterRange(from: string, to: string): Record<string, RosterDay> {
  const [map, setMap] = useState<Record<string, RosterDay>>({});
  useEffect(() => {
    if (!from || !to) {
      setMap({});
      return;
    }
    const q = query(
      collection(db, "roster"),
      where(documentId(), ">=", from),
      where(documentId(), "<=", to),
    );
    return onSnapshot(q, (snap) => {
      const hasil: Record<string, RosterDay> = {};
      for (const s of snap.docs) {
        const assign =
          s.data().assign && typeof s.data().assign === "object"
            ? (s.data().assign as Record<string, string>)
            : {};
        hasil[s.id] = { date: s.id, assign };
      }
      setMap(hasil);
    });
  }, [from, to]);
  return map;
}

function toPunch(d: DocumentData | undefined | null): Punch | null {
  if (!d || !d.at) return null;
  return {
    at: d.at,
    lat: numOrNull(d.lat),
    lon: numOrNull(d.lon),
    accuracyMeters: numOrNull(d.accuracyMeters),
    distanceMeters: numOrNull(d.distanceMeters),
    outsideGeofence: bool(d.outsideGeofence, false),
    photoPath: str(d.photoPath),
    pinOk: bool(d.pinOk, true),
    adminOverride: bool(d.adminOverride, false),
    noPin: bool(d.noPin, false),
  };
}

export function toRecord(snap: QueryDocumentSnapshot<DocumentData>): AttendanceRecord {
  const d = snap.data();
  return {
    id: snap.id,
    employeeId: str(d.employeeId),
    employeeName: str(d.employeeName),
    date: str(d.date),
    shiftId: str(d.shiftId),
    shiftName: str(d.shiftName),
    shiftStart: str(d.shiftStart),
    shiftEnd: str(d.shiftEnd),
    offSchedule: bool(d.offSchedule, false),
    checkIn: toPunch(d.checkIn),
    checkOut: toPunch(d.checkOut),
    lateMinutes: num(d.lateMinutes, 0),
    earlyLeaveMinutes: num(d.earlyLeaveMinutes, 0),
    workMinutes: num(d.workMinutes, 0),
    overtimeMinutes: num(d.overtimeMinutes, 0),
    note: str(d.note),
    deviceId: str(d.deviceId),
    correctedBy: str(d.correctedBy),
  };
}

/**
 * Absen dalam rentang tanggal.
 *
 * Rentangnya disaring di peladen dengan perbandingan string: format
 * "yyyy-MM-dd" berurut secara leksikografis sama dengan urutan kalender,
 * sehingga satu indeks satu field sudah cukup.
 */
export function useRecords(from: string, to: string): AttendanceRecord[] {
  const [list, setList] = useState<AttendanceRecord[]>([]);
  useEffect(() => {
    if (!from || !to) {
      setList([]);
      return;
    }
    const q = query(
      collection(db, "records"),
      where("date", ">=", from),
      where("date", "<=", to),
    );
    return onSnapshot(q, (snap) => {
      setList(snap.docs.map(toRecord));
    });
  }, [from, to]);
  return list;
}

export function useDevices(): KioskDevice[] {
  const [list, setList] = useState<KioskDevice[]>([]);
  useEffect(() => {
    return onSnapshot(collection(db, "devices"), (snap) => {
      setList(
        snap.docs.map((s) => {
          const d = s.data();
          return {
            id: s.id,
            label: str(d.label, s.id),
            appVersion: str(d.appVersion, "?"),
            lastSeen: d.lastSeen ?? null,
          } satisfies KioskDevice;
        }),
      );
    });
  }, []);
  return list;
}
