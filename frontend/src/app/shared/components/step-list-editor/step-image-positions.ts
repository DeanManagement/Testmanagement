/**
 * Step images are keyed by step position while editing. These keep each image on its step when
 * steps are inserted, removed or moved (TM06). Each returns a new map; callers copy it back.
 */

/** Positions at and after {@code at} move down one, making room for a new step. */
export function imagesAfterInsert<T>(images: Map<number, T>, at: number): Map<number, T> {
  return remap(images, (key) => (key >= at ? key + 1 : key));
}

/** The removed step's image goes; positions after it move up one. */
export function imagesAfterRemove<T>(images: Map<number, T>, at: number): Map<number, T> {
  return remap(images, (key) => {
    if (key === at) return null;
    return key > at ? key - 1 : key;
  });
}

/** The moved step's image moves with it; the steps it passed shift by one. */
export function imagesAfterMove<T>(images: Map<number, T>, from: number, to: number): Map<number, T> {
  return remap(images, (key) => {
    if (key === from) return to;
    if (from < to && key > from && key <= to) return key - 1;
    if (to < from && key >= to && key < from) return key + 1;
    return key;
  });
}

function remap<T>(images: Map<number, T>, newKey: (key: number) => number | null): Map<number, T> {
  const result = new Map<number, T>();
  images.forEach((value, key) => {
    const target = newKey(key);
    if (target !== null) {
      result.set(target, value);
    }
  });
  return result;
}
