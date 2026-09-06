import { textToSpeech } from '@kit.CoreSpeechKit';
import { BusinessError } from '@kit.BasicServicesKit';
import nativeCore from 'libentry.so';

export type PositionListener = (offset: number) => void;
export type StopListener = (reason: string) => void;

export class TtsController {
  private engine?: textToSpeech.TextToSpeechEngine;
  private creating?: Promise<void>;
  private generation: number = 0;
  private handle: number = 0;
  private offset: number = 0;
  private onPosition?: PositionListener;
  private onStop?: StopListener;
  private rate: number = 1;
  private pitch: number = 1;

  async start(handle: number, offset: number, onPosition: PositionListener, onStop: StopListener): Promise<void> {
    this.stop('');
    await this.ensureEngine();
    this.handle = handle;
    this.offset = Math.max(0, offset);
    this.onPosition = onPosition;
    this.onStop = onStop;
    this.generation++;
    this.speakNext(this.generation);
  }

  stop(reason: string = 'stopped'): void {
    this.generation++;
    try {
      if (this.engine?.isBusy()) this.engine.stop();
    } catch (_) {}
    let listener = this.onStop;
    this.handle = 0;
    this.onPosition = undefined;
    this.onStop = undefined;
    if (reason.length > 0 && listener) listener(reason);
  }

  isSpeaking(): boolean { return this.handle !== 0; }
  setRate(value: number): void { this.rate = Math.max(0.5, Math.min(2, value)); }
  setPitch(value: number): void { this.pitch = Math.max(0.5, Math.min(2, value)); }

  close(): void {
    this.stop('');
    try { this.engine?.shutdown(); } catch (_) {}
    this.engine = undefined;
    this.creating = undefined;
  }

  private ensureEngine(): Promise<void> {
    if (this.engine) return Promise.resolve();
    if (this.creating) return this.creating;
    this.creating = new Promise<void>((resolve, reject) => {
      let extraParams: Record<string, Object> = {
        'style': 'interaction-broadcast',
        'locate': 'CN',
        'name': 'JingduTts'
      };
      let params: textToSpeech.CreateEngineParams = {
        language: 'zh-CN',
        person: 0,
        online: 0,
        extraParams: extraParams
      };
      textToSpeech.createEngine(params, (error: BusinessError, engine: textToSpeech.TextToSpeechEngine) => {
        if (error) {
          this.creating = undefined;
          reject(new Error(`TTS ${error.code}: ${error.message}`));
          return;
        }
        this.engine = engine;
        resolve();
      });
    });
    return this.creating;
  }

  private speakNext(generation: number): void {
    if (!this.engine || this.handle === 0 || generation !== this.generation) return;
    let packed: string;
    try {
      packed = nativeCore.speechChunk(this.handle, this.offset, 900);
    } catch (error) {
      this.finish(error instanceof Error ? error.message : 'TTS core error');
      return;
    }
    let tab = packed.indexOf('\t');
    if (tab <= 0) {
      this.finish('end');
      return;
    }
    let next = Number(packed.substring(0, tab));
    let value = packed.substring(tab + 1);
    if (!Number.isFinite(next) || next <= this.offset || value.trim().length === 0) {
      this.finish('end');
      return;
    }
    this.offset = next;
    if (this.onPosition) this.onPosition(next);
    let requestId = `${generation}:${Date.now()}:${next}`;
    let extraParams: Record<string, Object> = {
      'queueMode': 0,
      'speed': this.rate,
      'volume': 1,
      'pitch': this.pitch,
      'languageContext': 'zh-CN',
      'audioType': 'pcm',
      'soundChannel': 3,
      'playType': 1
    };
    let params: textToSpeech.SpeakParams = { requestId: requestId, extraParams: extraParams };
    let listener: textToSpeech.SpeakListener = {
      onStart: (_id: string, _response: textToSpeech.StartResponse): void => {},
      onComplete: (id: string, response: textToSpeech.CompleteResponse): void => {
        if (response.type !== 1) return;
        let requestGeneration = this.requestGeneration(id);
        if (requestGeneration === this.generation) this.speakNext(requestGeneration);
      },
      onStop: (_id: string, _response: textToSpeech.StopResponse): void => {},
      onData: (_id: string, _audio: ArrayBuffer, _response: textToSpeech.SynthesisResponse): void => {},
      onError: (_id: string, errorCode: number, errorMessage: string): void => {
        this.finish(`TTS ${errorCode}: ${errorMessage}`);
      }
    };
    try {
      this.engine.speak(value, params, listener);
    } catch (error) {
      this.finish(error instanceof Error ? error.message : 'TTS speak failed');
    }
  }

  private requestGeneration(requestId: string): number {
    let colon = requestId.indexOf(':');
    if (colon <= 0) return -1;
    let value = Number(requestId.substring(0, colon));
    return Number.isFinite(value) ? value : -1;
  }

  private finish(reason: string): void {
    let callback = this.onStop;
    this.handle = 0;
    this.onPosition = undefined;
    this.onStop = undefined;
    if (callback) callback(reason);
  }
}
