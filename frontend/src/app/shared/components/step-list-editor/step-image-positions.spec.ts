import { describe, expect, it } from 'vitest';
import { imagesAfterInsert, imagesAfterMove, imagesAfterRemove } from './step-image-positions';

/** Steps A, B, C, D with an image on B (1) and on D (3). */
const images = new Map([[1, 'b'], [3, 'd']]);

function entries(map: Map<number, string>): [number, string][] {
  return [...map.entries()].sort((x, y) => x[0] - y[0]);
}

describe('step image positions', () => {
  it('moves the images at and after an inserted step down one', () => {
    expect(entries(imagesAfterInsert(images, 1))).toEqual([[2, 'b'], [4, 'd']]);
  });

  it('leaves images before an inserted step where they are', () => {
    expect(entries(imagesAfterInsert(images, 2))).toEqual([[1, 'b'], [4, 'd']]);
  });

  it('drops the removed step image and moves later ones up', () => {
    expect(entries(imagesAfterRemove(images, 1))).toEqual([[2, 'd']]);
  });

  it('carries the image of a step moved up and shifts the ones it passed', () => {
    // D to the top: D, A, B, C
    expect(entries(imagesAfterMove(images, 3, 0))).toEqual([[0, 'd'], [2, 'b']]);
  });

  it('carries the image of a step moved down and shifts the ones it passed', () => {
    // B to the end: A, C, D, B
    expect(entries(imagesAfterMove(images, 1, 3))).toEqual([[2, 'd'], [3, 'b']]);
  });

  it('changes nothing when a step is dropped where it was', () => {
    expect(entries(imagesAfterMove(images, 1, 1))).toEqual([[1, 'b'], [3, 'd']]);
  });
});
