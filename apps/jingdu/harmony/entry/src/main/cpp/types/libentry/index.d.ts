export interface JingduNativeCore {
  abiVersion(): number;
  detectEncoding(sample: Uint8Array, truncated: boolean): string;
  fileSha256(path: string): string;
  repairRevision(normalizedSha256: string, packedRules: string): string;
  open(path: string): number;
  close(handle: number): void;
  charCount(handle: number): number;
  read(handle: number, offset: number, count: number): string;
  search(handle: number, query: string, limit: number): string;
  chapters(handle: number, limit: number): string;
  speechChunk(handle: number, offset: number, count: number): string;
  exportRules(handle: number, packedRules: string, outputPath: string): void;
}

declare const nativeCore: JingduNativeCore;
export default nativeCore;
