/**
 * Urutan nama karyawan, sama seperti `domain/NameSort.kt` di aplikasi Android.
 *
 * Huruf besar-kecil dan tanda aksen diabaikan, dan angka dibaca sebagai angka
 * — sehingga "Budi 2" berada sebelum "Budi 10", bukan sesudahnya.
 */
const collator = new Intl.Collator("id", { sensitivity: "base", numeric: true });

export function compareNames(a: string, b: string): number {
  return collator.compare(a, b);
}

export function sortByName<T>(
  items: T[],
  name: (item: T) => string,
  ascending = true,
): T[] {
  const arah = ascending ? 1 : -1;
  return [...items].sort((a, b) => compareNames(name(a), name(b)) * arah);
}
