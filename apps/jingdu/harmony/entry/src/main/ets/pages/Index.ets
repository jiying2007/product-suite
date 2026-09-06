import { common } from '@kit.AbilityKit';
import { picker } from '@kit.CoreFileKit';
import { taskpool } from '@kit.ArkTS';
import { BookMeta, BookStore } from '../model/BookStore';
import {
  CleanTaskResult, cleanTask, exportTask, importBookTask, pruneRevisionsTask
} from '../model/BackgroundTasks';
import { ChapterItem, ReaderController, SearchHit } from '../model/ReaderController';
import { TtsController } from '../model/TtsController';

@Entry
@Component
struct Index {
  @State pageText: string = '导入本地 TXT 开始阅读。\n\n文件只复制到应用私有目录，源文件不会被修改。';
  @State status: string = '离线 · 无账号 · 无网络权限';
  @State books: BookMeta[] = [];
  @State searchHits: SearchHit[] = [];
  @State chapters: ChapterItem[] = [];
  @State bookmarks: number[] = [];
  @State panel: string = '';
  @State searchQuery: string = '';
  @State ruleFrom: string = '';
  @State ruleTo: string = '';
  @State fontSize: number = 20;
  @State night: boolean = false;
  @State busy: boolean = false;
  @State autoReading: boolean = false;
  @State ttsReading: boolean = false;
  @State cleanMode: boolean = false;

  private store?: BookStore;
  private current?: BookMeta;
  private reader: ReaderController = new ReaderController();
  private tts: TtsController = new TtsController();
  private autoTimer: number = -1;
  private sleepTimer: number = -1;
  private autoDelay: number = 6000;
  private workGeneration: number = 0;

  aboutToAppear(): void {
    let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.store = new BookStore(context);
    this.refreshLibrary();
    if (this.books.length > 0) this.openBook(this.books[0], false);
  }

  aboutToDisappear(): void {
    this.workGeneration++;
    this.stopAuto();
    this.cancelSleepTimer();
    this.tts.close();
    this.persistProgress();
    this.reader.close();
  }

  build() {
    Column() {
      this.toolbar()
      Text(this.status)
        .fontSize(12)
        .fontColor(this.night ? '#B8B8B8' : '#666666')
        .padding({ left: 16, right: 16, top: 6, bottom: 6 })
        .width('100%')
      if (this.panel.length > 0) this.panelView()
      Scroll() {
        Text(this.pageText)
          .fontSize(this.fontSize)
          .fontColor(this.night ? '#E2E2E2' : '#242424')
          .lineHeight(this.fontSize * 1.55)
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .padding({ left: 24, right: 24, top: 16, bottom: 64 })
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor(this.night ? '#181818' : '#FAF8F0')
      Row({ space: 12 }) {
        Button('上一页').enabled(this.current !== undefined && !this.busy).layoutWeight(1)
          .onClick(() => { this.reader.previous(); this.render(); })
        Button('下一页').enabled(this.current !== undefined && !this.busy).layoutWeight(1)
          .onClick(() => { this.reader.next(); this.render(); })
      }.width('100%').padding(10)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.night ? '#181818' : '#FAF8F0')
  }

  @Builder
  toolbar() {
    Scroll() {
      Row({ space: 6 }) {
        Button('导入').enabled(!this.busy).onClick(() => this.panel = 'encoding')
        Button('书架').enabled(!this.busy).onClick(() => { this.refreshLibrary(); this.panel = 'library'; })
        Button('搜索').enabled(this.current !== undefined && !this.busy).onClick(() => this.panel = 'search')
        Button('目录').enabled(this.current !== undefined && !this.busy)
          .onClick(() => { this.panel = 'chapters'; this.loadChapters(); })
        Button('书签').enabled(this.current !== undefined && !this.busy && !this.cleanMode)
          .onClick(() => { this.loadBookmarks(); this.panel = 'bookmarks'; })
        Button('净读').enabled(this.current !== undefined && !this.busy).onClick(() => this.panel = 'repair')
        Button(this.ttsReading ? '暂停朗读' : '朗读')
          .enabled(this.current !== undefined && !this.busy).onClick(() => this.toggleTts())
        Button(this.autoReading ? '停止自动' : '自动')
          .enabled(this.current !== undefined && !this.busy).onClick(() => this.toggleAuto())
        Button('睡眠').enabled(this.current !== undefined).onClick(() => this.panel = 'sleep')
        Button(this.night ? '日间' : '夜间').onClick(() => this.night = !this.night)
        Button('A-').onClick(() => this.fontSize = Math.max(14, this.fontSize - 2))
        Button('A+').onClick(() => this.fontSize = Math.min(36, this.fontSize + 2))
      }.padding({ left: 8, right: 8 })
    }.scrollable(ScrollDirection.Horizontal).width('100%').height(52)
  }

  @Builder
  panelView() {
    Column({ space: 8 }) {
      Row() {
        Text(this.panelTitle()).fontSize(16).fontWeight(FontWeight.Medium).layoutWeight(1)
        Button('关闭').onClick(() => this.panel = '')
      }.width('100%')

      if (this.panel === 'encoding') this.encodingPanel()
      else if (this.panel === 'library') this.libraryPanel()
      else if (this.panel === 'search') this.searchPanel()
      else if (this.panel === 'chapters') this.chapterPanel()
      else if (this.panel === 'bookmarks') this.bookmarkPanel()
      else if (this.panel === 'repair') this.repairPanel()
      else if (this.panel === 'sleep') this.sleepPanel()
      else if (this.panel === 'danger') {
        Button('删除当前私有副本').fontColor(Color.Red).onClick(() => this.confirmDelete())
      }

      if (this.current !== undefined && this.panel !== 'danger') {
        Button('删除私有副本…').fontColor(Color.Red).onClick(() => this.panel = 'danger')
      }
    }
    .padding(12)
    .margin({ left: 8, right: 8, bottom: 4 })
    .borderRadius(12)
    .backgroundColor(this.night ? '#282828' : '#F0EDE5')
    .width('calc(100% - 16vp)')
  }

  @Builder
  encodingPanel() {
    Text('AUTO：UTF-8/BOM、无 BOM UTF-16、Big5/GB18030；仍可手动覆盖。').fontSize(12)
    Flex({ wrap: FlexWrap.Wrap, space: { main: 6, cross: 6 } }) {
      ForEach(BookStore.ENCODINGS, (encoding: string) => {
        Button(encoding).onClick(async () => await this.pickImport(encoding))
      }, (encoding: string) => encoding)
    }
  }

  @Builder
  libraryPanel() {
    if (this.books.length === 0) Text('书架为空')
    ForEach(this.books, (book: BookMeta) => {
      Row() {
        Column() {
          Text(book.name).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(`${book.encoding} · ${book.sourceSha256.substring(0, 8)}`)
            .fontSize(11).fontColor('#777777')
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        Button('打开').onClick(() => { this.openBook(book, false); this.panel = ''; })
      }.width('100%')
    }, (book: BookMeta) => book.id)
  }

  @Builder
  searchPanel() {
    Row({ space: 8 }) {
      TextInput({ placeholder: '全文搜索', text: this.searchQuery }).layoutWeight(1)
        .onChange((value: string) => this.searchQuery = value)
      Button('搜索').enabled(!this.busy).onClick(() => this.runSearch())
    }.width('100%')
    Scroll() {
      Column() {
        ForEach(this.searchHits, (hit: SearchHit) => {
          Text(hit.context).fontSize(13).padding(8).width('100%')
            .onClick(() => { this.reader.jump(hit.offset); this.render(); this.panel = ''; })
        }, (hit: SearchHit) => `${hit.offset}:${hit.context}`)
      }
    }.height(Math.min(240, Math.max(48, this.searchHits.length * 48)))
  }

  @Builder
  chapterPanel() {
    Scroll() {
      Column() {
        if (this.chapters.length === 0 && !this.busy) Text('未识别到章节标题')
        ForEach(this.chapters, (chapter: ChapterItem) => {
          Text(chapter.title).padding(8).width('100%').onClick(() => {
            this.reader.jump(chapter.offset); this.render(); this.panel = '';
          })
        }, (chapter: ChapterItem) => `${chapter.offset}:${chapter.title}`)
      }
    }.height(Math.min(280, Math.max(48, this.chapters.length * 44)))
  }

  @Builder
  bookmarkPanel() {
    Button('添加当前位置').onClick(() => this.addBookmark())
    ForEach(this.bookmarks, (offset: number) => {
      Row() {
        Text(`位置 ${offset}`).layoutWeight(1)
          .onClick(() => { this.reader.jump(offset); this.render(); this.panel = ''; })
        Button('删除').onClick(() => this.removeBookmark(offset))
      }.width('100%').padding(4)
    }, (offset: number) => offset.toString())
  }

  @Builder
  repairPanel() {
    TextInput({ placeholder: '查找（字面文本）', text: this.ruleFrom })
      .onChange((value: string) => this.ruleFrom = value)
    TextInput({ placeholder: '替换为', text: this.ruleTo })
      .onChange((value: string) => this.ruleTo = value)
    Row({ space: 8 }) {
      Button('添加规则').onClick(() => this.addRule())
      Button(this.cleanMode ? '原文视图' : '净读预览').enabled(!this.busy)
        .onClick(() => this.toggleClean())
      Button('导出').enabled(!this.busy).onClick(async () => await this.exportClean())
      Button('清空规则').onClick(() => this.clearRules())
    }
    Text(`规则数：${this.ruleCount()}`).fontSize(12)
  }

  @Builder
  sleepPanel() {
    Row({ space: 8 }) {
      Button('关闭').onClick(() => this.setSleepTimer(0))
      Button('15 分钟').onClick(() => this.setSleepTimer(15))
      Button('30 分钟').onClick(() => this.setSleepTimer(30))
      Button('60 分钟').onClick(() => this.setSleepTimer(60))
    }
  }

  private panelTitle(): string {
    if (this.panel === 'encoding') return '导入 TXT';
    if (this.panel === 'library') return '书架';
    if (this.panel === 'search') return `搜索结果 · ${this.searchHits.length}`;
    if (this.panel === 'chapters') return `目录 · ${this.chapters.length}`;
    if (this.panel === 'bookmarks') return `书签 · ${this.bookmarks.length}`;
    if (this.panel === 'repair') return '净读';
    if (this.panel === 'sleep') return '睡眠定时';
    if (this.panel === 'danger') return '删除';
    return '';
  }

  private refreshLibrary(): void { this.books = this.store?.list() ?? []; }

  private uriName(uri: string): string {
    let index = uri.lastIndexOf('/');
    let name = index >= 0 ? uri.substring(index + 1) : 'TXT';
    try { return decodeURIComponent(name); } catch (_) { return name; }
  }

  private async pickImport(encoding: string): Promise<void> {
    if (!this.store || this.busy) return;
    let token = 0;
    try {
      let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      let options = new picker.DocumentSelectOptions();
      options.maxSelectNumber = 1;
      options.fileSuffixFilters = ['TXT|.txt'];
      let uris = await new picker.DocumentViewPicker(context).select(options);
      if (uris.length === 0) return;
      token = ++this.workGeneration;
      this.busy = true;
      this.status = '正在导入…';
      let result = await taskpool.execute(new taskpool.Task(
        importBookTask, uris[0], this.store.rootPath(), encoding)) as string;
      if (token !== this.workGeneration) return;
      let book = this.store.persistImported(this.uriName(uris[0]), result);
      this.refreshLibrary();
      this.busy = false;
      await this.openBook(book, false);
      if (token + 1 === this.workGeneration) this.panel = '';
    } catch (error) {
      if (token === 0 || token === this.workGeneration) this.showError(error, '导入失败');
    } finally {
      if (token === this.workGeneration) this.busy = false;
    }
  }

  private async openBook(book: BookMeta, clean: boolean): Promise<void> {
    if (!this.store || this.busy) return;
    this.stopAuto();
    this.tts.stop('');
    this.ttsReading = false;

    let previous = this.current;
    let previousClean = this.cleanMode;
    let restored = clean ? 0 :
      (previous?.id === book.id && previous.normalizedSha256 === book.normalizedSha256 &&
        !previousClean ? this.reader.position() : book.progress);
    this.persistProgress();
    let token = ++this.workGeneration;
    this.busy = true;
    this.current = undefined;
    this.cleanMode = false;
    this.bookmarks = [];
    if (this.panel === 'bookmarks') this.panel = '';
    this.status = clean ? '正在生成净读视图…' : '正在打开…';

    try {
      let path = this.store.normalizedPath(book);
      if (clean) path = await this.buildClean(book);
      if (token !== this.workGeneration) return;

      let opened = await this.reader.open(path, restored);
      if (token !== this.workGeneration || !opened) return;

      this.current = book;
      this.cleanMode = clean;
      await taskpool.execute(new taskpool.Task(
        pruneRevisionsTask,
        this.store.bookDir(book),
        book.normalizedSha256,
        clean ? path : ''));
      if (token !== this.workGeneration) return;
      this.render();
    } catch (error) {
      if (token === this.workGeneration) {
        this.current = previous;
        this.cleanMode = previousClean;
        if (previous !== undefined) this.render();
        this.showError(error, clean ? '净读失败' : '打开失败');
      }
    } finally {
      if (token === this.workGeneration) this.busy = false;
    }
  }

  private render(): void {
    if (!this.current || !this.store) return;
    try {
      this.pageText = this.reader.page();
      if (!this.cleanMode) this.store.saveProgress(this.current, this.reader.position());
      let percent = this.reader.length() === 0 ? 0 :
        Math.min(100, Math.floor(this.reader.position() * 100 / this.reader.length()));
      this.status = `${this.current.name} · ${percent}% · ${this.current.encoding} · ${this.current.normalizedSha256.substring(0, 8)}${this.cleanMode ? ' · 净读' : ' · 原文视图'}`;
    } catch (error) {
      this.showError(error, '读取失败');
    }
  }

  private persistProgress(): void {
    if (this.current && this.store && !this.cleanMode) {
      this.store.saveProgress(this.current, this.reader.position());
    }
  }

  private async runSearch(): Promise<void> {
    if (this.searchQuery.trim().length === 0 || this.busy) return;
    let token = ++this.workGeneration;
    this.busy = true;
    this.status = '正在搜索…';
    try {
      let hits = await this.reader.search(this.searchQuery);
      if (token === this.workGeneration) this.searchHits = hits;
    } catch (error) {
      if (token === this.workGeneration) this.showError(error, '搜索失败');
    } finally {
      if (token === this.workGeneration) {
        this.busy = false;
        if (this.current) this.render();
      }
    }
  }

  private async loadChapters(): Promise<void> {
    if (this.busy) return;
    let token = ++this.workGeneration;
    this.busy = true;
    this.status = '正在生成目录…';
    try {
      let items = await this.reader.chapters();
      if (token === this.workGeneration) this.chapters = items;
    } catch (error) {
      if (token === this.workGeneration) this.showError(error, '目录失败');
    } finally {
      if (token === this.workGeneration) {
        this.busy = false;
        if (this.current) this.render();
      }
    }
  }

  private loadBookmarks(): void {
    if (!this.current || !this.store || this.cleanMode) {
      this.bookmarks = [];
      return;
    }
    this.bookmarks = this.store.bookmarks(this.current)
      .sort((a: number, b: number): number => a - b);
  }

  private addBookmark(): void {
    if (!this.current || !this.store || this.cleanMode) return;
    let offset = this.reader.position();
    let values = this.store.bookmarks(this.current);
    if (values.indexOf(offset) < 0) values.push(offset);
    values.sort((a: number, b: number): number => a - b);
    this.store.saveBookmarks(this.current, values);
    this.bookmarks = values;
  }

  private removeBookmark(offset: number): void {
    if (!this.current || !this.store || this.cleanMode) return;
    let values = this.store.bookmarks(this.current)
      .filter((item: number): boolean => item !== offset);
    this.store.saveBookmarks(this.current, values);
    this.bookmarks = values;
  }

  private rules(): string {
    return this.current && this.store ? this.store.rules(this.current) : '';
  }

  private ruleCount(): number {
    let value = this.rules();
    return value.length === 0 ? 0 : value.split('\u001e').length;
  }

  private addRule(): void {
    if (!this.current || !this.store) return;
    let from = this.ruleFrom;
    let to = this.ruleTo;
    if (from.length === 0 || from.indexOf('\u001e') >= 0 || from.indexOf('\u001f') >= 0 ||
      to.indexOf('\u001e') >= 0 || to.indexOf('\u001f') >= 0) {
      this.toast('规则包含保留分隔符或查找为空');
      return;
    }
    let old = this.rules();
    this.store.saveRules(this.current,
      `${old}${old.length === 0 ? '' : '\u001e'}${from}\u001f${to}`);
    this.ruleFrom = '';
    this.ruleTo = '';
    if (this.cleanMode) this.openBook(this.current, true);
  }

  private clearRules(): void {
    if (!this.current || !this.store) return;
    this.store.saveRules(this.current, '');
    if (this.cleanMode) this.openBook(this.current, false);
  }

  private async buildClean(book: BookMeta): Promise<string> {
    if (!this.store) throw new Error('book store unavailable');
    let packed = await taskpool.execute(new taskpool.Task(
      cleanTask,
      this.store.normalizedPath(book),
      book.normalizedSha256,
      this.store.rules(book),
      this.store.bookDir(book))) as string;
    let result = JSON.parse(packed) as CleanTaskResult;
    if (result.revision.length !== 64 || result.path !== this.store.cleanPath(book, result.revision)) {
      throw new Error('invalid clean artifact result');
    }
    return result.path;
  }

  private toggleClean(): void {
    if (this.current) this.openBook(this.current, !this.cleanMode);
  }

  private async exportClean(): Promise<void> {
    if (!this.current || !this.store || this.busy) return;
    let book = this.current;
    let token = ++this.workGeneration;
    try {
      this.busy = true;
      this.status = '正在准备导出…';
      let cleanPath = await this.buildClean(book);
      if (token !== this.workGeneration) return;
      let context = this.getUIContext().getHostContext() as common.UIAbilityContext;
      let options = new picker.DocumentSaveOptions();
      options.newFileNames = [`${this.stripTxt(book.name)}-净读.txt`];
      options.fileSuffixChoices = ['txt'];
      let uris = await new picker.DocumentViewPicker(context).save(options);
      if (token !== this.workGeneration) return;
      if (uris.length > 0) {
        await taskpool.execute(new taskpool.Task(exportTask, cleanPath, uris[0]));
        if (token === this.workGeneration) this.toast('导出完成');
      }
    } catch (error) {
      if (token === this.workGeneration) this.showError(error, '导出失败');
    } finally {
      if (token === this.workGeneration) {
        this.busy = false;
        if (this.current) this.render();
      }
    }
  }

  private stripTxt(name: string): string {
    return name.toLowerCase().endsWith('.txt') ? name.substring(0, name.length - 4) : name;
  }

  private async toggleTts(): Promise<void> {
    if (!this.current || this.busy) return;
    this.stopAuto();
    if (this.ttsReading) {
      this.tts.stop('');
      this.ttsReading = false;
      return;
    }
    try {
      this.ttsReading = true;
      await this.tts.start(this.reader.handleValue(), this.reader.position(),
        (offset: number): void => { this.reader.jump(offset); this.render(); },
        (reason: string): void => {
          this.ttsReading = false;
          if (reason !== 'end' && reason.length > 0) this.toast(reason);
        });
    } catch (error) {
      this.ttsReading = false;
      this.showError(error, '朗读失败');
    }
  }

  private toggleAuto(): void {
    if (!this.current || this.busy) return;
    if (this.autoReading) {
      this.stopAuto();
      return;
    }
    this.tts.stop('');
    this.ttsReading = false;
    this.autoReading = true;
    this.autoTimer = setInterval(() => {
      if (!this.current || this.reader.position() >= Math.max(0, this.reader.length() - 1)) {
        this.stopAuto();
        return;
      }
      this.reader.next();
      this.render();
    }, this.autoDelay);
  }

  private stopAuto(): void {
    this.autoReading = false;
    if (this.autoTimer >= 0) {
      clearInterval(this.autoTimer);
      this.autoTimer = -1;
    }
  }

  private setSleepTimer(minutes: number): void {
    this.cancelSleepTimer();
    if (minutes > 0) {
      this.sleepTimer = setTimeout(() => {
        this.stopAuto();
        this.tts.stop('sleep timer');
        this.ttsReading = false;
        this.toast('睡眠定时结束');
      }, minutes * 60 * 1000);
      this.toast(`已设置 ${minutes} 分钟`);
    } else {
      this.toast('已关闭睡眠定时');
    }
    this.panel = '';
  }

  private cancelSleepTimer(): void {
    if (this.sleepTimer >= 0) {
      clearTimeout(this.sleepTimer);
      this.sleepTimer = -1;
    }
  }

  private confirmDelete(): void {
    if (!this.current || !this.store) return;
    let book = this.current;
    this.getUIContext().showAlertDialog({
      title: '删除私有副本？',
      message: `${book.name}\n源 TXT 不会被删除。`,
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: {
        value: '删除',
        fontColor: Color.Red,
        action: () => {
          this.workGeneration++;
          this.stopAuto();
          this.tts.stop('');
          this.ttsReading = false;
          this.persistProgress();
          this.reader.close();
          this.store?.delete(book);
          this.current = undefined;
          this.cleanMode = false;
          this.pageText = '导入本地 TXT 开始阅读。';
          this.status = '离线 · 无账号 · 无网络权限';
          this.panel = '';
          this.refreshLibrary();
          if (this.books.length > 0) this.openBook(this.books[0], false);
        }
      }
    });
  }

  private toast(message: string): void {
    this.getUIContext().getPromptAction().showToast({ message: message, duration: 1800 });
  }

  private showError(error: Object, prefix: string): void {
    let message = error instanceof Error ? error.message : JSON.stringify(error);
    this.toast(`${prefix}: ${message}`);
  }
}
