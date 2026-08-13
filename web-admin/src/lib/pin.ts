/**
 * PIN pribadi karyawan.
 *
 * Harus menghasilkan hash yang identik dengan `domain/Pin.kt` di aplikasi
 * Android, karena PIN dibuat di sini lalu diperiksa di tablet: sama-sama
 * PBKDF2-HMAC-SHA256, 120.000 putaran, kunci 256 bit, garam 16 byte acak.
 *
 * PIN hanya empat angka. Untuk digit ASCII, penyandian UTF-8 di WebCrypto dan
 * penyandian char[] di Java menghasilkan byte yang sama, jadi kedua sisi tidak
 * perlu penyesuaian apa pun.
 */

export const PIN_LENGTH = 4;
export const PIN_ITERATIONS = 120_000;
const KEY_BITS = 256;

export function isValidPinFormat(pin: string): boolean {
  return pin.length === PIN_LENGTH && /^\d+$/.test(pin);
}

export function newSalt(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return toHex(bytes);
}

export async function hashPin(
  pin: string,
  saltHex: string,
  iterations: number = PIN_ITERATIONS,
): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(pin),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      salt: fromHex(saltHex) as BufferSource,
      iterations,
      hash: "SHA-256",
    },
    key,
    KEY_BITS,
  );
  return toHex(new Uint8Array(bits));
}

/** Membuat garam baru sekaligus hash-nya, untuk disimpan di dokumen karyawan. */
export async function makePinFields(pin: string): Promise<{
  pinSalt: string;
  pinHash: string;
  pinIterations: number;
}> {
  const pinSalt = newSalt();
  const pinHash = await hashPin(pin, pinSalt);
  return { pinSalt, pinHash, pinIterations: PIN_ITERATIONS };
}

function toHex(bytes: Uint8Array): string {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function fromHex(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}
