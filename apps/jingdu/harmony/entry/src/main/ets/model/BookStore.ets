import { common } from '@kit.AbilityKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { preferences } from '@kit.ArkData';
import { ImportTaskResult } from './BackgroundTasks';

export interface BookMeta {
  id: string;
  name: string;
  encoding: string;
  size: number;
  sourceSha256: string;
  normalizedSha256: string;
  progress: number;
  touchedAt: number;
}

export class BookStore {
  static readonly ENCODINGS: string[] = [
    'AUTO', 'UTF-8', 'GB18030', 'GBK', 'GB2312', 'Big5', 'UTF-16LE', 'UTF-16BE'
  ];

  private readonly prefs: preferences.Preferences;
  private readonly root: string;

  constructor(context: common.UIAbilityContext) {
    this.root = `${context.filesDir}/books`;
    try { fs.mkdirSync(this.root); } catch (_) {}
    this.cleanupRootTemporaries();
    this.prefs = preferences.getPreferencesSync(context, { name: 'jingdu_library_v2' });
  }

  rootPath(): string { return this.root; }

  bookDir(book: BookMeta): string { return `${this.root}/${book.id}`; }

  normalizedPath(book: BookMeta): string {
    return `${this.bookDir(book)}/document-${book.normalizedSha256}.txt`;
  }

  cleanPath(book: BookMeta, revision: string): string {
    return `${this.bookDir(book)}/clean-${revision}.txt`;
  }

  list(): BookMeta[] {
    let raw = this.prefs.getSync('books', '[]') as string;
    let books: BookMeta[] = [];
    try {
      let parsed = JSON.parse(raw) as BookMeta[];
      parsed.forEach((item: BookMeta): void => {
        if (item.id.length !== 64 || item.id !== item.sourceSha256 ||
          item.normalizedSha256.length !== 64) return;
        try {
          fs.statSync(`${this.bookDir(item)}/source.bin`);
          fs.statSync(this.normalizedPath(item));
          books.push(item);
        } catch (_) {}
      });
    } catch (_) {
      // Hard-cut v2 metadata: old/malformed private data is intentionally ignored.
    }
    books.sort((a: BookMeta, b: BookMeta): number => b.touchedAt - a.touchedAt);
    return books;
  }

  persistImported(name: string, taskJson: string): BookMeta {
    let result = JSON.parse(taskJson) as ImportTaskResult;
    if (result.id.length !== 64 || result.id !== result.sourceSha256 ||
      result.normalizedSha256.length !== 64) {
      throw new Error('invalid shared-core identity result');
    }

    let existing = this.list().find((item: BookMeta): boolean => item.id === result.id);
    let book: BookMeta = {
      id: result.id,
      name: name,
      encoding: result.encoding,
      size: result.size,
      sourceSha256: result.sourceSha256,
      normalizedSha256: result.normalizedSha256,
      progress: existing !== undefined &&
        existing.normalizedSha256 === result.normalizedSha256 ? existing.progress : 0,
      touchedAt: Date.now()
    };
    let books = this.list().filter((item: BookMeta): boolean => item.id !== book.id);
    books.push(book);
    this.write(books);
    return book;
  }

  saveProgress(book: BookMeta, progress: number): void {
    book.progress = Math.max(0, progress);
    book.touchedAt = Date.now();
    let books = this.list().filter((item: BookMeta): boolean => item.id !== book.id);
    books.push(book);
    this.write(books);
  }

  delete(book: BookMeta): void {
    let dir = this.bookDir(book);
    let files: string[] = [];
    try { files = fs.listFileSync(dir); } catch (_) {}
    files.forEach((name: string): void => {
      try { fs.unlinkSync(`${dir}/${name}`); } catch (_) {}
    });
    try { fs.rmdirSync(dir); } catch (_) {}

    this.write(this.list().filter((item: BookMeta): boolean => item.id !== book.id));
    this.prefs.deleteSync(`rules.${book.id}`);
    this.prefs.deleteSync(`bookmarks.${book.id}`);
    this.prefs.flush();
  }

  rules(book: BookMeta): string {
    return this.prefs.getSync(`rules.${book.id}`, '') as string;
  }

  saveRules(book: BookMeta, rules: string): void {
    this.prefs.putSync(`rules.${book.id}`, rules);
    this.prefs.flush();
  }

  bookmarks(book: BookMeta): number[] {
    let raw = this.prefs.getSync(`bookmarks.${book.id}`, '[]') as string;
    try { return JSON.parse(raw) as number[]; } catch (_) { return []; }
  }

  saveBookmarks(book: BookMeta, values: number[]): void {
    this.prefs.putSync(`bookmarks.${book.id}`, JSON.stringify(values));
    this.prefs.flush();
  }

  private cleanupRootTemporaries(): void {
    let files: string[] = [];
    try { files = fs.listFileSync(this.root); } catch (_) { return; }
    files.forEach((name: string): void => {
      if (name.startsWith('.import-') && name.endsWith('.bin')) {
        try { fs.unlinkSync(`${this.root}/${name}`); } catch (_) {}
      }
    });
  }

  private write(items: BookMeta[]): void {
    this.prefs.putSync('books', JSON.stringify(items));
    this.prefs.flush();
  }
}
