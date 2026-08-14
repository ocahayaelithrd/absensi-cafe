import { deleteDoc, deleteField, getDoc, setDoc, updateDoc } from "firebase/firestore";
import {
  cafeDoc,
  employeeDoc,
  employeesCol,
  newId,
  recordDoc,
  rosterDoc,
  shiftDoc,
  shiftsCol,
} from "./paths";
import { doc } from "firebase/firestore";
import type { AttendanceRecord, Employee, Settings, Shift } from "./types";

/*
 * Penulisan ke Firestore, sekaligus penerjemah dari model aplikasi ke nama
 * field lama. Halaman tidak pernah menyusun dokumen sendiri, supaya nama lama
 * hanya muncul di satu berkas ini dan di `hooks/useData.ts`.
 */

/** Bidang PIN baru; kosong berarti PIN tidak diubah. */
export interface PinFields {
  pinHash: string;
  pinSalt: string;
  pinIterations: number;
}

export async function saveSettings(s: Settings): Promise<void> {
  /* Ditulis dengan merge agar field lama yang tidak lagi dipakai aplikasi ini
     — faceMode, faceThreshold, retention, sortDir — tetap utuh di dokumen. */
  await setDoc(
    cafeDoc(),
    {
      settings: {
        cafeName: s.cafeName,
        pin: s.kioskAdminPin,
        pinMode: s.pinRequired ? "wajib" : "off",
        tolerance: s.toleranceMinutes,
        minOvertime: s.minOvertimeMinutes,
        fineEnabled: s.fineEnabled,
        fineTiers: s.fineTiers.map((t) => ({ upTo: t.upToMinutes, amount: t.amount })),
        geoMode: s.geoMode,
        geoLat: s.geoLat,
        geoLon: s.geoLon,
        geoRadius: s.geoRadiusMeters,
      },
    },
    { merge: true },
  );
}

export async function saveEmployee(
  id: string | null,
  data: {
    name: string;
    role: string;
    toleranceMinutes: number | null;
    active: boolean;
  },
  pin: PinFields | null,
): Promise<void> {
  const isi: Record<string, unknown> = {
    name: data.name,
    role: data.role,
    tolerance: data.toleranceMinutes,
    active: data.active,
  };

  /* PIN lama tersimpan apa adanya sebagai `pin`. Begitu admin mengubahnya,
     yang ditulis adalah hash bergaram dan field lamanya dikosongkan — PIN
     berpindah ke bentuk aman satu per satu, tanpa memaksa semua karyawan
     berganti PIN sekaligus. */
  if (pin) {
    Object.assign(isi, pin, { pin: null });
  }

  if (id) {
    await updateDoc(employeeDoc(id), isi);
  } else {
    const baru = newId();
    await setDoc(doc(employeesCol(), baru), {
      id: baru,
      pin: null,
      pinHash: "",
      pinSalt: "",
      pinIterations: 0,
      templates: [],
      avatar: null,
      ...isi,
    });
  }
}

export async function clearPin(id: string): Promise<void> {
  await updateDoc(employeeDoc(id), {
    pin: null,
    pinHash: "",
    pinSalt: "",
    pinIterations: 0,
  });
}

export async function deleteEmployee(id: string): Promise<void> {
  await deleteDoc(employeeDoc(id));
}

export async function saveShift(id: string | null, s: Omit<Shift, "id">): Promise<void> {
  const isi = { code: s.code, name: s.name, start: s.start, end: s.end };
  if (id) {
    await updateDoc(shiftDoc(id), isi);
  } else {
    const baru = newId();
    await setDoc(doc(shiftsCol(), baru), { id: baru, ...isi });
  }
}

export async function deleteShift(id: string): Promise<void> {
  await deleteDoc(shiftDoc(id));
}

/**
 * Mengisi roster satu tanggal untuk sekumpulan karyawan.
 *
 * Penugasan ditulis sebagai peta bersarang di field `hari` dengan merge, bukan
 * jalur bertitik: pada `setDoc`, titik justru membuat field bernama
 * "hari.xxx" secara harfiah.
 */
export async function setRoster(
  date: string,
  employeeIds: string[],
  shiftId: string | null,
): Promise<void> {
  const hari: Record<string, unknown> = {};
  for (const id of employeeIds) hari[id] = shiftId === null ? deleteField() : shiftId;
  await setDoc(rosterDoc(date), { hari }, { merge: true });
}

/**
 * Menyalin roster satu hari ke hari lain tanpa menimpa sel yang sudah terisi.
 *
 * Pola shift cafe berulang, tapi perubahan yang sudah disepakati dengan
 * karyawan lebih penting daripada polanya.
 */
export async function copyRosterDay(from: string, to: string): Promise<number> {
  const snap = await getDoc(rosterDoc(from));
  if (!snap.exists()) return 0;
  const sumber = (snap.data().hari ?? {}) as Record<string, string>;

  const tujuanSnap = await getDoc(rosterDoc(to));
  const sudah = (tujuanSnap.data()?.hari ?? {}) as Record<string, string>;

  const hari: Record<string, string> = {};
  let jumlah = 0;
  for (const [id, shiftId] of Object.entries(sumber)) {
    if (sudah[id] !== undefined) continue;
    hari[id] = shiftId;
    jumlah++;
  }
  if (jumlah === 0) return 0;
  await setDoc(rosterDoc(to), { hari }, { merge: true });
  return jumlah;
}

/**
 * Menyimpan koreksi sebuah catatan absen.
 *
 * Hanya jam, shift, dan catatan yang datang dari admin. Telat, lembur, dan jam
 * kerja selalu berasal dari perhitungan ulang, tidak pernah diketik tangan,
 * supaya rekap tidak bisa berbeda dari jam yang tersimpan.
 */
export async function saveRecordCorrection(r: AttendanceRecord): Promise<void> {
  await updateDoc(recordDoc(r.id), {
    inAt: r.checkIn ? r.checkIn.at.getTime() : null,
    outAt: r.checkOut ? r.checkOut.at.getTime() : null,
    hasOut: Boolean(r.checkOut),
    shiftId: r.shiftId || null,
    offSchedule: r.offSchedule,
    lateMin: r.lateMinutes,
    earlyMin: r.earlyLeaveMinutes,
    workMin: r.workMinutes,
    otMin: r.overtimeMinutes,
    note: r.note,
    edited: true,
  });
}

export async function deleteRecord(id: string): Promise<void> {
  await deleteDoc(recordDoc(id));
}

/** Semua PIN yang masih tersimpan apa adanya, untuk peringatan di layar admin. */
export function employeesWithPlainPin(employees: Employee[]): Employee[] {
  return employees.filter((e) => e.plainPin !== "" && e.pinHash === "");
}
