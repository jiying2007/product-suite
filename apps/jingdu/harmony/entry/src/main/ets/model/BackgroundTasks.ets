import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import nativeCore from 'libentry.so';

export interface ImportTaskResult {
  id: string;
  encoding: string;
  size: number;
  sourceSha256: string;
  normalizedSha256: string;
}

export interface OpenTaskResult {
  handle: number;
  length: number;
  path: string;
}

export interface CleanTaskResult {
  path: string;
  revision: string;
}

function fileExists(path: string): boolean {
  try {
    fs.statSync(path);
    return true;
  } catch (_) {
    return false;
  }
}

function removeIfExists(path: string): void {
  if (path.length === 0) return;
  try { fs.unlinkSync(path); } catch (_) {}
}

function copyFile(sourcePath: string, targetPath: string): number {
  let source = fs.openSync(sourcePath, fs.OpenMode.READ_ONLY);
  let target = fs.openSync(targetPath,
    fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
  let total = 0;
  try {
    let buffer = new ArrayBuffer(64 * 1024);
    while (true) {
      let count = fs.readSync(source.fd, buffer, { offset: 0, length: buffer.byteLength });
      if (count <= 0) break;
      fs.writeSync(target.fd, buffer, { offset: 0, length: count });
      total += count;
    }
    fs.fsyncSync(target.fd);
  } finally {
    fs.closeSync(source);
    fs.closeSync(target);
  }
  return total;
}

function publishImmutable(temporaryPath: string, targetPath: string): void {
  if (fileExists(targetPath)) {
    removeIfExists(temporaryPath);
    return;
  }
  try {
    fs.renameSync(temporaryPath, targetPath);
  } catch (error) {
    if (fileExists(targetPath)) {
      removeIfExists(temporaryPath);
      return;
    }
    throw error;
  }
}

function normalize(rawPath: string, normalizedPath: string, requestedEncoding: string): string {
  let raw = fs.openSync(rawPath, fs.OpenMode.READ_ONLY);
  let sampleBuffer = new ArrayBuffer(64 * 1024);
  let sampleSize = 0;
  try {
    sampleSize = fs.readSync(raw.fd, sampleBuffer,
      { position: 0, offset: 0, length: sampleBuffer.byteLength });
  } finally {
    fs.closeSync(raw);
  }

  let sample = new Uint8Array(sampleBuffer, 0, sampleSize);
  let totalSize = fs.statSync(rawPath).size;
  let encoding = requestedEncoding === 'AUTO'
    ? nativeCore.detectEncoding(sample, totalSize > sampleSize)
    : requestedEncoding;

  let decoder = util.TextDecoder.create(encoding.toLowerCase(),
    { ignoreBOM: false, fatal: false });
  let source = fs.openSync(rawPath, fs.OpenMode.READ_ONLY);
  let output = fs.openSync(normalizedPath,
    fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
  try {
    let buffer = new ArrayBuffer(64 * 1024);
    while (true) {
      let count = fs.readSync(source.fd, buffer, { offset: 0, length: buffer.byteLength });
      if (count <= 0) break;
      let chunk = decoder.decodeToString(new Uint8Array(buffer, 0, count), { stream: true });
      if (chunk.length > 0) fs.writeSync(output.fd, chunk, { encoding: 'utf-8' });
    }
    let tail = decoder.decodeToString(new Uint8Array(new ArrayBuffer(0)), { stream: false });
    if (tail.length > 0) fs.writeSync(output.fd, tail, { encoding: 'utf-8' });
    fs.fsyncSync(output.fd);
  } finally {
    fs.closeSync(source);
    fs.closeSync(output);
  }
  return encoding;
}

@Concurrent
export function importBookTask(sourceUri: string, booksRoot: string,
  requestedEncoding: string): string {
  let nonce = `${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
  let sourceTemp = `${booksRoot}/.import-${nonce}.bin`;
  let normalizedTemp = '';
  try {
    let size = copyFile(sourceUri, sourceTemp);
    let sourceSha256 = nativeCore.fileSha256(sourceTemp);
    let dir = `${booksRoot}/${sourceSha256}`;
    try { fs.mkdirSync(dir); } catch (_) {}

    let rawPath = `${dir}/source.bin`;
    publishImmutable(sourceTemp, rawPath);
    sourceTemp = '';

    normalizedTemp = `${dir}/.document-${nonce}.tmp`;
    let encoding = normalize(rawPath, normalizedTemp, requestedEncoding);
    let normalizedSha256 = nativeCore.fileSha256(normalizedTemp);
    let normalizedPath = `${dir}/document-${normalizedSha256}.txt`;
    publishImmutable(normalizedTemp, normalizedPath);
    normalizedTemp = '';

    let result: ImportTaskResult = {
      id: sourceSha256,
      encoding: encoding,
      size: size,
      sourceSha256: sourceSha256,
      normalizedSha256: normalizedSha256
    };
    return JSON.stringify(result);
  } finally {
    removeIfExists(sourceTemp);
    removeIfExists(normalizedTemp);
  }
}

@Concurrent
export function openDocumentTask(path: string): string {
  if (nativeCore.abiVersion() !== 2) throw new Error('unsupported Jingdu core ABI');
  let handle = nativeCore.open(path);
  let result: OpenTaskResult = {
    handle: handle,
    length: nativeCore.charCount(handle),
    path: path
  };
  return JSON.stringify(result);
}

@Concurrent
export function searchTask(path: string, query: string, limit: number): string {
  let handle = nativeCore.open(path);
  try { return nativeCore.search(handle, query, limit); }
  finally { nativeCore.close(handle); }
}

@Concurrent
export function chaptersTask(path: string, limit: number): string {
  let handle = nativeCore.open(path);
  try { return nativeCore.chapters(handle, limit); }
  finally { nativeCore.close(handle); }
}

@Concurrent
export function cleanTask(normalizedPath: string, normalizedSha256: string,
  rules: string, bookDir: string): string {
  let revision = nativeCore.repairRevision(normalizedSha256, rules);
  let outputPath = `${bookDir}/clean-${revision}.txt`;
  if (!fileExists(outputPath)) {
    let handle = nativeCore.open(normalizedPath);
    try { nativeCore.exportRules(handle, rules, outputPath); }
    finally { nativeCore.close(handle); }
  }
  let result: CleanTaskResult = { path: outputPath, revision: revision };
  return JSON.stringify(result);
}

@Concurrent
export function pruneRevisionsTask(bookDir: string, normalizedSha256: string,
  keepCleanPath: string): void {
  let keepDocument = `document-${normalizedSha256}.txt`;
  let keepClean = '';
  let slash = keepCleanPath.lastIndexOf('/');
  if (keepCleanPath.length > 0) keepClean = slash >= 0 ? keepCleanPath.substring(slash + 1) : keepCleanPath;

  let files: string[] = [];
  try { files = fs.listFileSync(bookDir); } catch (_) { return; }
  files.forEach((name: string): void => {
    let remove = false;
    if (name.startsWith('document-') && name.endsWith('.txt') && name !== keepDocument) remove = true;
    if (name.startsWith('clean-') && name.endsWith('.txt') && name !== keepClean) remove = true;
    if (name.startsWith('clean-') && name.endsWith('.txt.tmp')) remove = true;
    if (name.startsWith('.document-') && name.endsWith('.tmp')) remove = true;
    if (name.endsWith('.jdx.tmp')) remove = true;
    if (name.endsWith('.jdx')) {
      let sourceName = name.substring(0, name.length - 4);
      if (!fileExists(`${bookDir}/${sourceName}`)) remove = true;
    }
    if (name === 'document.txt' || name === 'clean.txt' || name === 'clean.revision') remove = true;
    if (remove) removeIfExists(`${bookDir}/${name}`);
  });
}

@Concurrent
export function exportTask(sourcePath: string, destinationUri: string): number {
  return copyFile(sourcePath, destinationUri);
}
