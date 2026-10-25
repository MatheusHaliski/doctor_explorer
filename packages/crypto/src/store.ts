/**
 * Onde o aparelho guarda chaves e estado MLS. No navegador é IndexedDB cifrado com uma chave AES-GCM não exportável
 * (apps/web/lib/chat-store.ts); nos testes, memória. Valores são bytes; quem cifra em repouso é a implementação.
 */
export interface KeyStore {
  get(key: string): Promise<Uint8Array | undefined>;
  put(key: string, value: Uint8Array): Promise<void>;
  delete(key: string): Promise<void>;
  keys(prefix: string): Promise<string[]>;
}

export class MemoryStore implements KeyStore {
  private readonly map = new Map<string, Uint8Array>();

  async get(key: string) {
    const v = this.map.get(key);
    return v ? v.slice() : undefined;
  }

  async put(key: string, value: Uint8Array) {
    this.map.set(key, value.slice());
  }

  async delete(key: string) {
    this.map.delete(key);
  }

  async keys(prefix: string) {
    return [...this.map.keys()].filter((k) => k.startsWith(prefix));
  }

  /** Para o teste "o servidor não vê texto claro": tudo que ficou guardado neste aparelho. */
  dump(): Map<string, Uint8Array> {
    return new Map(this.map);
  }
}
