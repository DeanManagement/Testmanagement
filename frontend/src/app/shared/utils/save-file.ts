/** Hands a blob or a data/blob URL to the browser as a download under {@code fileName}. */
export function saveFile(source: Blob | string, fileName: string): void {
  const url = typeof source === 'string' ? source : URL.createObjectURL(source);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  if (typeof source !== 'string') {
    setTimeout(() => URL.revokeObjectURL(url));
  }
}
